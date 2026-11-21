package com.admi.mifi.data.api

import com.admi.mifi.data.DeviceProtocol
import com.admi.mifi.data.DeviceSnapshot
import com.admi.mifi.data.WifiClient
import com.admi.mifi.util.asBoolean
import com.admi.mifi.util.asInt
import com.admi.mifi.util.asLong
import com.admi.mifi.util.cleanValue
import com.admi.mifi.util.decodeUnicodeHex
import com.admi.mifi.util.firstNumber
import com.admi.mifi.util.formatBand
import com.admi.mifi.util.mapCarrier
import com.admi.mifi.util.mapNetworkType
import com.admi.mifi.util.normalizeBatteryPercent
import com.admi.mifi.util.normalizeSignal
import com.admi.mifi.util.signalLevelFromDbm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Base64
import java.util.Locale

/**
 * ZTE/ASR derived firmware which exposes the classic goform API.
 * Both the `/goform/` and `/reqproc/` variants are supported.
 */
class GoformApi : DeviceApi {

    override val protocol: DeviceProtocol = DeviceProtocol.GOFORM

    private var sessionKey: String = ""
    private var currentPassword: String = ""
    private var getPath: String? = null
    private var setPath: String? = null
    private var loggedIn = false

    private data class Endpoint(val get: String, val set: String)

    private val endpoints = listOf(
        Endpoint("/goform/goform_get_cmd_process", "/goform/goform_set_cmd_process"),
        Endpoint("/reqproc/proc_get", "/reqproc/proc_post"),
    )

    private val coreFields = listOf(
        "network_type", "net_type", "wan_network_type", "network_mode", "sys_mode", "sys_submode",
        "sub_network_type", "network_provider", "network_operator", "sim_operator", "spn", "carrier", "plmn",
        "band", "lte_band", "nr_band", "remo_band", "cur_band", "active_band", "band_current", "act_current",
        "signal_strength", "rsrp", "lte_rsrp", "nr_rsrp", "rssi", "rscp", "sinr", "rsrq",
        "wan_ipaddr", "wan_ip", "ipv4_wan_address", "ipv4_wan_ip", "wan_ipv4", "wan_conn_status",
        "network_connected", "connect_status", "ppp_status",
        "imei", "device_imei", "iccid", "sim_iccid", "ziccid",
        "battery_percent", "battery_level", "battery_capacity", "bat_percent", "battery_vol_percent",
        "battery_voltage", "battery_vol", "battery_charging", "battery_charge", "battery_pers",
        "charge_status", "is_charging",
        "wifi_ssid", "wlan_ssid", "ssid1", "main_ssid", "SSID1",
        "wifi_key", "wpa_psk_key", "wlan_password", "wifi_password", "wpa_key", "psk", "WPAPSK1_encode",
        "hardware_version", "hw_version", "hw_ver",
        "software_version", "sw_version", "wa_inner_version", "cr_version", "firmware_version", "version",
        "device_model", "device_name", "model_name", "product_name",
        "uptime", "uptime_seconds", "connect_time", "realtime_time",
        "station_num", "wifi_clients_num", "nr_connected_dev", "sta_count",
    )

    private val coreFieldSet: Set<String> = coreFields.map { it.lowercase(Locale.ROOT) }.toSet()

    private val trafficFields = listOf(
        "monthly_rx_bytes", "monthly_tx_bytes", "month_rx_bytes", "month_tx_bytes",
        "cur_month_rx_bytes", "cur_month_tx_bytes",
        "session_rx_bytes", "session_tx_bytes", "current_rx_bytes", "current_tx_bytes",
        "connect_rx_bytes", "connect_tx_bytes",
        "rx_bytes", "tx_bytes", "total_rx_bytes", "total_tx_bytes",
        "realtime_rx_bytes", "realtime_tx_bytes", "realtime_rx_thrpt", "realtime_tx_thrpt",
    )

    override suspend fun read(config: HostConfig, password: String): DeviceSnapshot =
        withContext(Dispatchers.IO) {
            ensureSession(config, password)
            var core = query(config, coreFields)
            if (!hasUsefulData(core) && !loggedIn) {
                runCatching { login(config, password) }
                if (loggedIn) core = query(config, coreFields)
            }
            if (!hasUsefulData(core)) {
                if (password.isBlank()) {
                    throw IOException("设备需要登录，请在设置中填写管理密码")
                }
                throw IOException("设备未返回数据，请检查管理密码或接入协议")
            }

            val traffic = query(config, trafficFields)
            val stationRaw = queryCommand(config, "station_list")
            val clients = parseClients(stationRaw).ifEmpty {
                val fallbackOrder = listOf("wifi_clients", "client_list", "lan_station_list", "associated_device")
                fallbackOrder.firstNotNullOfOrNull { command ->
                    parseClients(queryCommand(config, command)).takeIf { it.isNotEmpty() }
                } ?: emptyList()
            }

            val merged = LinkedHashMap<String, String>(core)
            traffic.forEach { (key, value) -> merged.putIfAbsent(key, value) }

            buildSnapshot(merged, clients)
        }

    private fun hasUsefulData(data: Map<String, String>): Boolean =
        data.keys.any { it in coreFieldSet }

    private fun pick(data: Map<String, String>, vararg keys: String): String? {
        for (key in keys) {
            val value = cleanValue(data[key.lowercase(Locale.ROOT)])
            if (value != null) return value
        }
        return null
    }

    private fun pickRaw(data: Map<String, String>, vararg keys: String): String? {
        for (key in keys) {
            val value = data[key.lowercase(Locale.ROOT)]?.trim()
            if (!value.isNullOrEmpty()) return value
        }
        return null
    }

    private fun pickLong(data: Map<String, String>, vararg keys: String): Long? {
        for (key in keys) {
            val value = asLong(data[key.lowercase(Locale.ROOT)])
            if (value != null) return value
        }
        return null
    }

    private fun buildSnapshot(data: Map<String, String>, clients: List<WifiClient>): DeviceSnapshot {
        val rawNetwork = pickRaw(
            data,
            "sub_network_type", "network_type", "net_type", "wan_network_type", "network_mode",
            "sys_submode", "sys_mode",
        )
        val bandRaw = pick(data, "band", "lte_band", "nr_band", "remo_band", "cur_band", "active_band", "band_current")
        val networkType = mapNetworkType(rawNetwork, bandRaw)
        val is5g = networkType?.contains("5G") == true
        val band = formatBand(bandRaw, is5g)

        val rsrp = firstNumber(pickRaw(data, "rsrp", "lte_rsrp", "nr_rsrp"))?.toInt()
        val signalPair = when {
            rsrp != null && rsrp in -140..-30 -> rsrp to signalLevelFromDbm(rsrp)
            rsrp != null && rsrp in 0..97 -> (-140 + rsrp) to signalLevelFromDbm(-140 + rsrp)
            else -> normalizeSignal(pickRaw(data, "signal_strength", "rssi"), asrRawRsrp = false)
        }
        val signalDbm = signalPair.first
        val signalLevel = signalPair.second

        val batteryRaw = pickRaw(
            data,
            "battery_percent", "battery_level", "battery_capacity", "bat_percent",
            "battery_vol_percent", "battery_voltage", "battery_vol",
        )
        val battery = normalizeBatteryPercent(batteryRaw)
        val charging = asBoolean(pickRaw(data, "battery_charging", "battery_charge", "charge_status", "is_charging"))

        val ssid = decodeUnicodeHex(pick(data, "wifi_ssid", "wlan_ssid", "ssid1", "main_ssid"))
        val wifiPassword = pick(
            data,
            "wifi_key", "wpa_psk_key", "wlan_password", "wifi_password", "wpa_key", "psk",
        ) ?: decodeBase64Text(data["wpapsk1_encode"])
        val wanIp = pick(data, "wan_ipaddr", "wan_ip", "ipv4_wan_address", "ipv4_wan_ip", "wan_ipv4")
        val connectedValue = asBoolean(
            pickRaw(data, "wan_conn_status", "network_connected", "connect_status", "ppp_status"),
        )
        val wanConnected = connectedValue ?: (wanIp != null && networkType != "无服务")

        val iccid = pick(data, "iccid", "sim_iccid", "ziccid")
        val hw = pick(data, "hardware_version", "hw_version", "hw_ver")
        val swBase = pick(data, "software_version", "sw_version", "wa_inner_version", "firmware_version")
        val swCr = pick(data, "cr_version")
        val software = when {
            swBase != null && swCr != null && swCr != swBase -> "$swBase / $swCr"
            swBase != null -> swBase
            else -> swCr
        }

        val monthlyRx = pickLong(data, "monthly_rx_bytes", "month_rx_bytes", "cur_month_rx_bytes")
        val monthlyTx = pickLong(data, "monthly_tx_bytes", "month_tx_bytes", "cur_month_tx_bytes")
        val sessionRx = pickLong(data, "session_rx_bytes", "current_rx_bytes", "connect_rx_bytes")
            ?: pickLong(data, "realtime_rx_bytes")
        val sessionTx = pickLong(data, "session_tx_bytes", "current_tx_bytes", "connect_tx_bytes")
            ?: pickLong(data, "realtime_tx_bytes")

        return DeviceSnapshot(
            protocol = DeviceProtocol.GOFORM,
            model = pick(data, "device_model", "device_name", "model_name", "product_name"),
            networkType = networkType,
            carrier = mapCarrier(
                iccid,
                pick(data, "network_provider", "network_operator", "sim_operator", "spn", "carrier", "plmn"),
            ),
            band = band,
            signalDbm = signalDbm,
            signalLevel = signalLevel,
            iccid = iccid,
            imei = pick(data, "imei", "device_imei"),
            wanIp = wanIp,
            wanConnected = wanConnected,
            batteryPercent = battery,
            charging = charging,
            wifiSsid = ssid,
            wifiPassword = wifiPassword,
            clients = clients,
            monthlyRxBytes = monthlyRx,
            monthlyTxBytes = monthlyTx,
            sessionRxBytes = sessionRx,
            sessionTxBytes = sessionTx,
            totalRxBytes = pickLong(data, "realtime_rx_bytes", "rx_bytes", "total_rx_bytes"),
            totalTxBytes = pickLong(data, "realtime_tx_bytes", "tx_bytes", "total_tx_bytes"),
            realtimeRxBps = pickLong(data, "realtime_rx_thrpt", "realtime_rx_bps"),
            realtimeTxBps = pickLong(data, "realtime_tx_thrpt", "realtime_tx_bps"),
            hardwareVersion = hw,
            softwareVersion = software,
            uptimeSeconds = parseUptime(pickRaw(data, "uptime", "uptime_seconds", "connect_time", "realtime_time")),
            connectedCount = maxOf(
                pickRaw(data, "station_num", "wifi_clients_num", "nr_connected_dev", "sta_count")
                    ?.toIntOrNull() ?: 0,
                clients.count { it.connected },
            ),
        )
    }

    private fun decodeBase64Text(raw: String?): String? {
        val text = cleanValue(raw) ?: return null
        val bytes = runCatching { Base64.getDecoder().decode(text) }.getOrElse {
            runCatching { Base64.getUrlDecoder().decode(text) }.getOrNull()
        } ?: return text
        val decoded = runCatching { String(bytes, Charsets.UTF_8) }.getOrNull() ?: return text
        return decoded.takeIf { value ->
            value.isNotEmpty() && value.none { it.isISOControl() }
        } ?: text
    }

    private fun parseUptime(raw: String?): Long? {
        val value = cleanValue(raw) ?: return null
        val direct = value.toLongOrNull()
        if (direct != null) return direct
        var seconds = 0L
        Regex("(\\d+)\\s*天").find(value)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { seconds += it * 86400 }
        Regex("(\\d+)\\s*(?:时|小时)").find(value)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { seconds += it * 3600 }
        Regex("(\\d+)\\s*分").find(value)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { seconds += it * 60 }
        Regex("(\\d+)\\s*秒").find(value)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { seconds += it }
        return seconds.takeIf { it > 0 }
    }

    private suspend fun ensureSession(config: HostConfig, password: String) {
        val key = "${config.origin}|${config.pathPrefix}|$password"
        if (sessionKey == key && getPath != null) return
        sessionKey = key
        currentPassword = password
        getPath = null
        setPath = null
        loggedIn = false
        Http.cookieJar.clear()
        probe(config)
    }

    private suspend fun probe(config: HostConfig) {
        for (endpoint in endpoints) {
            val url = config.url(endpoint.get, mapOf("isTest" to "false", "cmd" to "network_type", "multi_data" to "1"))
            val result = runCatching { Http.get(url, config.defaultHeaders()) }.getOrNull()
                ?: continue
            val body = result.body.trim()
            val html = body.startsWith("<html", ignoreCase = true) || body.startsWith("<!DOCTYPE", ignoreCase = true)
            if (result.code in listOf(200, 401, 403) && !html) {
                getPath = endpoint.get
                setPath = endpoint.set
                return
            }
        }
        throw IOException("未找到 Goform 接口，请确认管理地址或切换到 ASR 协议")
    }

    private suspend fun query(config: HostConfig, fields: List<String>): Map<String, String> {
        val path = getPath ?: throw IOException("管理接口尚未就绪")
        val url = config.url(
            path,
            mapOf(
                "isTest" to "false",
                "cmd" to fields.joinToString(","),
                "multi_data" to "1",
                "_" to System.currentTimeMillis().toString(),
            ),
        )
        val result = Http.get(url, config.defaultHeaders())
        if (result.code == 401 || result.code == 403) {
            if (!loggedIn) {
                runCatching { login(config, currentPassword) }
                if (!loggedIn) {
                    throw IOException(
                        if (currentPassword.isBlank()) {
                            "设备需要登录，请在设置中填写管理密码"
                        } else {
                            "管理密码验证失败，请检查密码"
                        },
                    )
                }
                return queryOnce(config, url)
            }
            throw IOException("设备拒绝了请求，请检查管理密码")
        }
        if (!result.isSuccessful) {
            throw IOException("设备返回 HTTP ${result.code}")
        }
        return parsePayload(result.body)
    }

    private suspend fun queryOnce(config: HostConfig, url: HttpUrl): Map<String, String> {
        val result = Http.get(url, config.defaultHeaders())
        if (!result.isSuccessful) {
            throw IOException("设备返回 HTTP ${result.code}")
        }
        return parsePayload(result.body)
    }

    private suspend fun queryCommand(config: HostConfig, command: String): String? {
        return runCatching {
            val path = getPath ?: return@runCatching null
            val url = config.url(
                path,
                mapOf(
                    "isTest" to "false",
                    "cmd" to command,
                    "multi_data" to "1",
                    "_" to System.currentTimeMillis().toString(),
                ),
            )
            val result = Http.get(url, config.defaultHeaders())
            if (!result.isSuccessful) return@runCatching null
            val body = result.body.trim()
            if (body.startsWith("{")) {
                val json = JSONObject(body)
                cleanValue(json.optString(command)) ?: cleanValue(json.optString("value"))
            } else {
                cleanValue(body)
            }
        }.getOrNull()
    }

    private fun parsePayload(body: String): Map<String, String> {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return emptyMap()
        if (trimmed.startsWith("<")) {
            val root = parseXmlDocument(trimmed) ?: return emptyMap()
            return root.flatten()
        }
        if (!trimmed.startsWith("{")) return emptyMap()
        return runCatching {
            val json = JSONObject(trimmed)
            val map = LinkedHashMap<String, String>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = json.opt(key)
                when (value) {
                    null, JSONObject.NULL -> Unit
                    is JSONArray, is JSONObject -> map[key.lowercase(Locale.ROOT)] = value.toString()
                    else -> {
                        val text = value.toString().trim()
                        // Keep "0": it is valid for counters, flags and signal values.
                        if (text == "0" || cleanValue(text) != null) {
                            map[key.lowercase(Locale.ROOT)] = text
                        }
                    }
                }
            }
            map
        }.getOrDefault(emptyMap())
    }

    private suspend fun login(config: HostConfig, password: String) {
        val path = setPath ?: throw IOException("管理接口尚未就绪")
        val url = config.url(path)
        // The reference app's ZTE/Goform default is the URL-encoded base64 of "admin".
        val sources = if (password.isBlank()) listOf("admin") else listOf(password, "admin").distinct()
        val candidates = sources.flatMap { source ->
            val encoded = Base64.getEncoder().encodeToString(source.toByteArray(Charsets.UTF_8))
            listOf(encoded, source)
        }.distinct()
        for (candidate in candidates) {
            val body = "isTest=false&goformId=LOGIN&password=${urlEncode(candidate)}"
            val result = runCatching {
                Http.postForm(url, body, config.defaultHeaders() + mapOf("Referer" to "${config.origin}/index.html"))
            }.getOrNull() ?: continue
            if (!result.isSuccessful) continue
            val responseBody = result.body.trim()
            if (responseBody.startsWith("{")) {
                val resultValue = runCatching { JSONObject(responseBody).optString("result") }.getOrNull()
                if (!resultValue.isNullOrEmpty() && resultValue != "0") continue
            }
            loggedIn = true
            // Confirm that the session actually works before pretending login succeeded.
            val probe = runCatching {
                val checkUrl = config.url(
                    getPath ?: return@runCatching emptyMap(),
                    mapOf("isTest" to "false", "cmd" to "network_type", "multi_data" to "1"),
                )
                parsePayload(Http.get(checkUrl, config.defaultHeaders()).body)
            }.getOrDefault(emptyMap())
            if (probe.isNotEmpty()) return
            loggedIn = false
        }
        if (password.isNotBlank()) {
            throw IOException("管理密码验证失败，请检查密码或接入协议")
        }
    }

    private fun urlEncode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    private fun parseClients(raw: String?): List<WifiClient> = parseClientJsonOrText(raw)

    override suspend fun reboot(config: HostConfig, password: String): Boolean {
        ensureSessionForAction(config, password)
        return sendAction(config, listOf("REBOOT_DEVICE", "REBOOT"))
    }

    override suspend fun shutdown(config: HostConfig, password: String): Boolean {
        ensureSessionForAction(config, password)
        return sendAction(config, listOf("POWER_OFF_DEVICE", "SHUTDOWN_DEVICE", "TURN_OFF_DEVICE"))
    }

    private suspend fun ensureSessionForAction(config: HostConfig, password: String) {
        if (getPath == null) {
            runCatching { ensureSession(config, password) }
        }
        if (password.isNotBlank() && !loggedIn) {
            runCatching { login(config, password) }
        }
    }

    private suspend fun sendAction(config: HostConfig, commands: List<String>): Boolean {
        val path = setPath ?: return false
        val url = config.url(path)
        for (command in commands) {
            val body = "isTest=false&goformId=$command"
            val result = try {
                Http.postForm(url, body, config.defaultHeaders())
            } catch (_: IOException) {
                // The device commonly drops the connection while executing power actions.
                return true
            }
            if (result.isSuccessful) {
                val lower = result.body.lowercase(Locale.ROOT)
                if (!lower.contains("fail") && !lower.contains("error")) return true
            }
        }
        return false
    }
}
