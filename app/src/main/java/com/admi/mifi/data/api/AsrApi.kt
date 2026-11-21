package com.admi.mifi.data.api

import com.admi.mifi.data.DeviceProtocol
import com.admi.mifi.data.DeviceSnapshot
import com.admi.mifi.data.WifiClient
import com.admi.mifi.util.asBoolean
import com.admi.mifi.util.asLong
import com.admi.mifi.util.cleanValue
import com.admi.mifi.util.decodeUnicodeHex
import com.admi.mifi.util.firstNumber
import com.admi.mifi.util.formatBand
import com.admi.mifi.util.mapCarrier
import com.admi.mifi.util.mapNetworkType
import com.admi.mifi.util.md5Hex
import com.admi.mifi.util.normalizeAsrRssi
import com.admi.mifi.util.normalizeBatteryPercent
import com.admi.mifi.util.normalizeSignal
import com.admi.mifi.util.parseDigestChallenge
import com.admi.mifi.util.randomHex
import com.admi.mifi.util.signalLevelFromDbm
import com.admi.mifi.util.DigestChallenge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

/**
 * ASR / UAPXB (LV01, MF518A03, Air720, ...) XML-CGI firmware.
 *
 * ASR firmware exposes XML files through:
 *   /xml_action.cgi?method=get&module=duster&file=status1
 * and may protect them with a two-step ASR Digest login:
 *   /login.cgi -> WWW-Authenticate -> /login.cgi?Action=Digest...
 *
 * Newer derivatives also expose json_* files; those are used as a fallback.
 */
class AsrApi : DeviceApi {

    override val protocol: DeviceProtocol = DeviceProtocol.ASR

    private data class AsrAuth(
        val username: String,
        val realm: String,
        val nonce: String,
        val qop: String,
        val ha1: String,
        var nc: Int = 2,
    )

    private data class Credential(val username: String, val password: String)

    private data class CacheEntry(val body: String?, val time: Long)

    private var sessionKey: String = ""
    private var auth: AsrAuth? = null
    private var loginBlockedUntil: Long = 0L
    private val cache = mutableMapOf<String, CacheEntry>()

    override suspend fun read(config: HostConfig, password: String): DeviceSnapshot =
        withContext(Dispatchers.IO) {
            prepareSession(config, password)

            val statusXml = fetchCached(config, password, "status1", ttlMs = 0)
            if (statusXml.isNullOrXml()) {
                val json = readJsonFallback(config, password)
                if (json != null) return@withContext json
                val legacy = readLegacyFallback(config, password)
                if (legacy != null) return@withContext legacy
                throw IOException("设备未返回状态数据，请检查管理密码或协议")
            }

            val statusRoot = parseXmlDocument(statusXml!!)
                ?: throw IOException("设备返回了无法解析的数据")

            val statisticsXml = if (statusRoot.path("statistics", "WanStatistics") != null) {
                null
            } else {
                fetchCached(config, password, "statistics", ttlMs = 8_000)
            }
            val clientsXml = fetchCached(config, password, "device_management_all", ttlMs = 8_000)
                ?: fetchCached(config, password, "device_management", ttlMs = 8_000)
            val securityXml = fetchCached(config, password, "uapxb_wlan_security_settings", ttlMs = 45_000)
                ?: fetchCached(config, password, "wlan_primary_security", ttlMs = 45_000)
            val customXml = fetchCached(config, password, "customconfig", ttlMs = 90_000)

            AsrStatusParser.parse(
                root = statusRoot,
                statisticsXml = statisticsXml,
                clientsXml = clientsXml,
                securityXml = securityXml,
                customXml = customXml,
            )
        }

    private fun String?.isNullOrXml(): Boolean {
        if (this == null) return true
        val text = trim()
        if (text.isEmpty()) return true
        val start = text.indexOf('<')
        if (start < 0) return true
        val head = text.substring(start).take(200).lowercase(Locale.ROOT)
        return head.startsWith("<html") || head.startsWith("<!doctype")
    }

    private fun isLoginPage(body: String): Boolean {
        val text = body.trim()
        if (text.isEmpty()) return false
        val start = text.indexOf('<')
        if (start < 0) return false
        val head = text.substring(start).take(200).lowercase(Locale.ROOT)
        return head.startsWith("<html") || head.startsWith("<!doctype")
    }

    private fun prepareSession(config: HostConfig, password: String) {
        val key = "${config.origin}|${config.pathPrefix}|$password"
        if (key != sessionKey) {
            sessionKey = key
            auth = null
            loginBlockedUntil = 0
            cache.clear()
            Http.cookieJar.clear()
        }
    }

    private suspend fun fetchCached(
        config: HostConfig,
        password: String,
        file: String,
        ttlMs: Long,
    ): String? {
        val now = System.currentTimeMillis()
        val cached = cache[file]
        if (cached != null) {
            val ttl = if (cached.body == null) minOf(ttlMs, 10_000) else ttlMs
            if (now - cached.time <= ttl) return cached.body
        }
        val body = try {
            fetchXmlFile(config, password, file)
        } catch (error: IOException) {
            if (ttlMs > 0L) {
                cache[file] = CacheEntry(null, now)
                return null
            }
            throw error
        }
        cache[file] = CacheEntry(body, now)
        return body
    }

    private suspend fun fetchXmlFile(config: HostConfig, password: String, file: String): String? {
        var result = requestFile(config, password, file)
        if (result == null) return null

        if (result.code == 401 || isUnauthorized(result.body) || isLoginPage(result.body)) {
            // Even with an empty saved password, ASR firmware may accept one of
            // its factory defaults. login() handles those candidates first and
            // only raises the "please set password" error when all fail.
            login(config, password)
            result = requestFile(config, password, file)
                ?: throw IOException("登录后仍无法读取设备数据")
        }

        if (result.code == 404) return null
        if (!result.isSuccessful) throw IOException("设备返回 HTTP ${result.code}")
        val body = result.body.trim()
        if (isUnauthorized(body)) {
            throw IOException("设备拒绝了请求，请确认管理密码")
        }
        if (body.isEmpty()) return ""
        return body
    }

    private suspend fun requestFile(
        config: HostConfig,
        password: String,
        file: String,
    ): HttpResult? {
        val url = config.url(
            "/xml_action.cgi",
            mapOf(
                "method" to "get",
                "module" to "duster",
                "file" to file,
                "_" to System.currentTimeMillis().toString(),
            ),
        )
        val headers = config.defaultHeaders() + authHeader("GET")
        return try {
            Http.get(url, headers)
        } catch (error: IOException) {
            if (file == "status1") throw error else null
        }
    }

    @Synchronized
    private fun authHeader(method: String): Map<String, String> {
        val current = auth ?: return emptyMap()
        val nc = current.nc
        current.nc += 1
        val ncString = nc.toString(16).padStart(8, '0')
        val cnonce = randomHex(16)
        val ha2 = md5Hex("$method:/cgi/xml_action.cgi")
        val response = md5Hex("${current.ha1}:${current.nonce}:$ncString:$cnonce:${current.qop}:$ha2")
        val value = buildString {
            append("Digest username=\"${current.username}\"")
            append(", realm=\"${current.realm}\"")
            append(", nonce=\"${current.nonce}\"")
            append(", uri=\"/cgi/xml_action.cgi\"")
            append(", response=\"$response\"")
            append(", qop=${current.qop}")
            append(", nc=$ncString")
            append(", cnonce=\"$cnonce\"")
        }
        return mapOf("Authorization" to value)
    }

    private suspend fun login(config: HostConfig, password: String) {
        val now = System.currentTimeMillis()
        if (now < loginBlockedUntil) {
            throw IOException("登录尝试过于频繁，请稍后再试")
        }

        var challengeSeen = false
        for (credential in credentials(password)) {
            val challenge = fetchDigestChallenge(config)
            if (challenge == null) continue
            challengeSeen = true
            if (!tryLogin(config, challenge, credential)) continue

            val qop = challenge.qop.ifBlank { "auth" }
            auth = AsrAuth(
                username = credential.username,
                realm = challenge.realm,
                nonce = challenge.nonce,
                qop = qop,
                ha1 = md5Hex("${credential.username}:${challenge.realm}:${credential.password}"),
                nc = 3,
            )
            if (verifyLogin(config)) return
            auth = null
        }

        auth = null
        loginBlockedUntil = System.currentTimeMillis() + 20_000
        throw IOException(
            when {
                !challengeSeen -> "设备未返回登录质询，请确认已连接随身 WiFi"
                password.isBlank() -> "设备要求登录，请在设置中填写管理密码"
                else -> "管理密码验证失败，请检查密码"
            },
        )
    }

    private suspend fun fetchDigestChallenge(config: HostConfig): DigestChallenge? {
        var result = Http.get(config.url("/login.cgi"), config.defaultHeaders())
        var header = result.header("WWW-Authenticate")
        if (header == null && result.code == 404) {
            result = Http.get(config.url("/cgi/login.cgi"), config.defaultHeaders())
            header = result.header("WWW-Authenticate")
        }
        return parseDigestChallenge(header)
    }

    private suspend fun tryLogin(
        config: HostConfig,
        challenge: DigestChallenge,
        credential: Credential,
    ): Boolean {
        // The ASR firmware uses two different digest URIs during login:
        // the query-string response is signed for /cgi/protected.cgi, while
        // the accompanying Authorization header is signed for /cgi/xml_action.cgi.
        val nc = "00000001"
        val headerNc = "00000002"
        val cnonce = randomHex(16)
        val headerCnonce = randomHex(16)
        val qop = challenge.qop.ifBlank { "auth" }
        val ha1 = md5Hex("${credential.username}:${challenge.realm}:${credential.password}")
        val queryResponse = md5Hex(
            "$ha1:${challenge.nonce}:$nc:$cnonce:$qop:${md5Hex("GET:/cgi/protected.cgi")}",
        )
        val authResponse = md5Hex(
            "$ha1:${challenge.nonce}:$headerNc:$headerCnonce:$qop:${md5Hex("GET:/cgi/xml_action.cgi")}",
        )

        val loginUrl = config.url(
            "/login.cgi",
            mapOf(
                "Action" to "Digest",
                "username" to credential.username,
                "realm" to challenge.realm,
                "nonce" to challenge.nonce,
                "response" to queryResponse,
                "qop" to qop,
                "cnonce" to cnonce,
                "nc" to nc,
                "temp" to "marvell",
                "client" to "APP",
            ),
        )
        val authorization = buildString {
            append("Digest username=\"${credential.username}\"")
            append(", realm=\"${challenge.realm}\"")
            append(", nonce=\"${challenge.nonce}\"")
            append(", uri=\"/cgi/xml_action.cgi\"")
            append(", response=\"$authResponse\"")
            append(", qop=$qop")
            append(", nc=$headerNc")
            append(", cnonce=\"$headerCnonce\"")
        }
        val result = runCatching {
            Http.get(
                loginUrl,
                config.defaultHeaders() + mapOf("Authorization" to authorization),
            )
        }.getOrNull() ?: return false
        if (!result.isSuccessful || isUnauthorized(result.body)) return false
        val lower = result.body.lowercase(Locale.ROOT)
        return !(lower.contains("<login_status>") && lower.contains("fail"))
    }

    private suspend fun verifyLogin(config: HostConfig): Boolean {
        val result = runCatching {
            Http.get(
                config.url(
                    "/xml_action.cgi",
                    mapOf("method" to "get", "module" to "duster", "file" to "status1"),
                ),
                config.defaultHeaders() + authHeader("GET"),
            )
        }.getOrNull() ?: return false
        return result.isSuccessful &&
            !isUnauthorized(result.body) &&
            !result.body.isNullOrXml()
    }

    private fun credentials(password: String): List<Credential> =
        if (password.isBlank()) {
            defaultCredentials
        } else {
            listOf(
                Credential("admin", password),
                Credential("sysadmin", password),
            ).plus(defaultCredentials).distinct()
        }

    private companion object {
        val defaultCredentials = listOf(
            Credential("admin", "admin"),
            Credential("sysadmin", "sysadmin"),
            Credential("admin", "sysadmin"),
            Credential("sysadmin", "admin"),
        ).distinct()
    }

    private fun isUnauthorized(body: String): Boolean {
        val lower = body.lowercase(Locale.ROOT)
        return lower.contains("unauthorized") || lower.contains("<login_status>fail") ||
            lower.contains("login_status>unauthorized")
    }

    private suspend fun readJsonFallback(config: HostConfig, password: String): DeviceSnapshot? {
        val statusBody = fetchJsonFile(config, password, "json_status_info") ?: return null
        val homeBody = fetchJsonFile(config, password, "json_homepage_info")
        val statsBody = fetchJsonFile(config, password, "json_statistics_info")

        val status = runCatching { JSONObject(statusBody) }.getOrNull() ?: return null
        val home = runCatching { JSONObject(homeBody.orEmpty()) }.getOrNull()
        val stats = runCatching { JSONObject(statsBody.orEmpty()) }.getOrNull()

        fun JSONObject.string(vararg keys: String): String? {
            for (key in keys) {
                val value = optString(key)
                if (value.isNotBlank() && value != "null") return value
            }
            return null
        }

        val sysMode = status.optString("sys_mode")
        val networkType = mapNetworkType(sysMode, null)
        val signal = normalizeSignal(status.optString("rssi"), asrRawRsrp = false)
        val battery = normalizeBatteryPercent(status.optString("battery_percent"))
        val ssid = decodeUnicodeHex(home?.string("ssid"))
        val monthlyRx = asLong(stats?.string("rx_byte_month", "monthly_rx_bytes"))
        val monthlyTx = asLong(stats?.string("tx_byte_month", "monthly_tx_bytes"))
        val monthly = asLong(stats?.string("total_used_month", "monthly_used_bytes"))
            ?: if (monthlyRx != null || monthlyTx != null) {
                (monthlyRx ?: 0L) + (monthlyTx ?: 0L)
            } else {
                null
            }
        val sessionRx = asLong(stats?.string("rx_byte"))
        val sessionTx = asLong(stats?.string("tx_byte"))
        val lifetimeRx = asLong(stats?.string("rx_byte_all"))
        val lifetimeTx = asLong(stats?.string("tx_byte_all"))

        return DeviceSnapshot(
            protocol = DeviceProtocol.ASR,
            model = home?.string("device_name", "model_name"),
            networkType = networkType,
            carrier = mapCarrier(home?.string("network_name")),
            band = formatBand(home?.string("band", "network_band"), networkType?.contains("5G") == true),
            signalDbm = signal.first,
            signalLevel = signal.second,
            iccid = home?.string("iccid", "sim_iccid"),
            imei = home?.string("imei"),
            wanIp = home?.string("wan_ip"),
            wanConnected = asBoolean(home?.string("connect_disconnect", "connect_status")) ?: (home?.string("wan_ip") != null),
            batteryPercent = battery,
            charging = asBoolean(status.optString("charging", "battery_charging")),
            wifiSsid = ssid,
            monthlyRxBytes = monthlyRx,
            monthlyTxBytes = monthlyTx,
            monthlyUsedBytes = monthly,
            realtimeRxBps = asLong(stats?.string("rx_speed", "realtime_rx_thrpt")),
            realtimeTxBps = asLong(stats?.string("tx_speed", "realtime_tx_thrpt")),
            sessionRxBytes = sessionRx,
            sessionTxBytes = sessionTx,
            sessionUsedBytes = if (sessionRx != null || sessionTx != null) {
                (sessionRx ?: 0) + (sessionTx ?: 0)
            } else {
                null
            },
            counterBytes = if (lifetimeRx != null || lifetimeTx != null) {
                (lifetimeRx ?: 0L) + (lifetimeTx ?: 0L)
            } else {
                null
            },
            hardwareVersion = home?.string("hw_version", "hardware_version"),
            softwareVersion = home?.string("sw_version", "software_version", "version"),
            uptimeSeconds = asLong(status.optString("run_seconds")),
            connectedCount = status.optString("wifi_clients_num").toIntOrNull(),
        )
    }

    private suspend fun fetchJsonFile(config: HostConfig, password: String, file: String): String? {
        val body = runCatching { fetchXmlFile(config, password, file) }.getOrNull() ?: return null
        return body.takeIf { it.trim().startsWith("{") }
    }

    private suspend fun readLegacyFallback(config: HostConfig, password: String): DeviceSnapshot? {
        val files = listOf(
            "wan", "statistics", "sysinfo", "wlan_settings",
            "wlan_primary_security", "device_management_all", "device_management", "customconfig",
        )
        val roots = LinkedHashMap<String, XmlNode>()
        for (file in files) {
            val body = runCatching { fetchCached(config, password, file, ttlMs = 15_000) }.getOrNull()
                ?: continue
            val root = parseXmlDocument(body) ?: continue
            roots[file] = root
        }
        if (roots.isEmpty()) return null
        val fields = LinkedHashMap<String, String>()
        roots.values.forEach { it.flatten(output = fields) }

        fun pick(vararg names: String): String? =
            names.firstNotNullOfOrNull { cleanValue(fields[it.lowercase(Locale.ROOT)]) }

        val wanRoot = roots["wan"]
        val sysinfo = roots["sysinfo"]
        val stats = roots["statistics"]?.path("statistics", "WanStatistics")
        val sysMode = pick("sys_mode")
        val networkRaw = pick("sys_submode", "sys_mode", "network_type")
        val networkType = mapNetworkType(networkRaw, pick("nr_band", "lte_band", "band", "band_current"))
        val is5g = networkType?.contains("5G") == true || pick("nr_band") != null
        val rsrp = pick("lte_rsrp", "nr_rsrp", "rsrp")
        val signal = when {
            pick("rssi") != null -> {
                val dbm = normalizeAsrRssi(pick("rssi"), sysMode)
                dbm to signalLevelFromDbm(dbm)
            }
            rsrp != null -> normalizeSignal(rsrp, asrRawRsrp = true)
            else -> null to 0
        }
        val securityRoot = roots["wlan_primary_security"]
        val security = securityRoot?.child("wlan_security")
        val ssid = decodeUnicodeHex(pick("ssid") ?: security?.value("ssid"))
        val clients = parseXmlClients(roots["device_management_all"] ?: roots["device_management"])
            .filter { it.connected }
        val sessionRx = asLong(stats?.value("rx_byte"))
        val sessionTx = asLong(stats?.value("tx_byte"))
        val monthly = asLong(stats?.value("total_used_month"))
            ?: asLong(pick("total_used_month"))
        val lifetime = asLong(stats?.value("total_used_unlimit"))
            ?: asLong(pick("total_used_unlimit"))
        val totalRx = asLong(stats?.value("rx_byte_all"))
        val totalTx = asLong(stats?.value("tx_byte_all"))
        val pdp = wanRoot?.path("wan", "cellular", "pdp_context_list")?.children?.firstOrNull()
        val wanIp = pdp?.value("ipv4") ?: pick("wan_ip", "ipv4", "ip")
        val iccid = pick("iccid", "sim_iccid")

        return DeviceSnapshot(
            protocol = DeviceProtocol.ASR,
            model = sysinfo?.value("model_name", "device_name"),
            networkType = networkType,
            carrier = mapCarrier(iccid, pick("network_name", "isp_name", "carrier")),
            band = formatBand(
                if (is5g) pick("nr_band", "lte_band") else pick("lte_band", "nr_band"),
                is5g,
            ) ?: formatBand(pick("band_current", "band", "current_band"), is5g),
            signalDbm = signal.first,
            signalLevel = signal.second,
            iccid = iccid,
            imei = pick("imei"),
            wanIp = wanIp,
            wanConnected = asBoolean(pick("wan_conn_status", "connect_status"))
                ?: (wanIp != null),
            batteryPercent = normalizeBatteryPercent(pick("battery_voltage", "battery_percent")),
            charging = asBoolean(pick("battery_charging", "battery_charge")),
            wifiSsid = ssid,
            wifiPassword = AsrStatusParser.extractWifiPassword(security),
            clients = clients,
            monthlyUsedBytes = monthly,
            sessionRxBytes = sessionRx,
            sessionTxBytes = sessionTx,
            sessionUsedBytes = if (sessionRx != null || sessionTx != null) {
                (sessionRx ?: 0L) + (sessionTx ?: 0L)
            } else {
                null
            },
            totalRxBytes = totalRx,
            totalTxBytes = totalTx,
            realtimeRxBps = asLong(pick("rx_rate")),
            realtimeTxBps = asLong(pick("tx_rate")),
            counterBytes = if (totalRx != null || totalTx != null) {
                (totalRx ?: 0L) + (totalTx ?: 0L)
            } else {
                lifetime ?: monthly
            },
            hardwareVersion = sysinfo?.value("hardware_version"),
            softwareVersion = sysinfo?.value("version_num"),
            connectedCount = pick("nr_connected_dev")?.toIntOrNull() ?: clients.size,
        )
    }

    override suspend fun reboot(config: HostConfig, password: String): Boolean =
        triggerAction(config, password, "reset")

    override suspend fun shutdown(config: HostConfig, password: String): Boolean =
        triggerAction(config, password, "power_off")

    private suspend fun triggerAction(config: HostConfig, password: String, file: String): Boolean =
        withContext(Dispatchers.IO) {
            prepareSession(config, password)
            val url = config.url(
                "/xml_action.cgi",
                mapOf(
                    "method" to "get",
                    "module" to "duster",
                    "file" to file,
                    "_" to System.currentTimeMillis().toString(),
                ),
            )
            try {
                var result = Http.get(url, config.defaultHeaders() + authHeader("GET"))
                if (result.code == 401 || isUnauthorized(result.body)) {
                    runCatching { login(config, password) }.getOrElse { return@withContext false }
                    result = Http.get(url, config.defaultHeaders() + authHeader("GET"))
                }
                if (result.code == 404) return@withContext false
                !isUnauthorized(result.body) && (result.isSuccessful || result.code in 200..399)
            } catch (_: IOException) {
                // The device usually disconnects before it can answer a power action.
                true
            }
        }
}
