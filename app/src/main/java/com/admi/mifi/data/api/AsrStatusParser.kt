package com.admi.mifi.data.api

import com.admi.mifi.data.DeviceProtocol
import com.admi.mifi.data.DeviceSnapshot
import com.admi.mifi.util.asBoolean
import com.admi.mifi.util.asLong
import com.admi.mifi.util.cleanValue
import com.admi.mifi.util.decodeUnicodeHex
import com.admi.mifi.util.firstNumber
import com.admi.mifi.util.formatBand
import com.admi.mifi.util.mapCarrier
import com.admi.mifi.util.mapNetworkType
import com.admi.mifi.util.normalizeAsrRssi
import com.admi.mifi.util.normalizeBatteryPercent
import com.admi.mifi.util.normalizeSignal
import com.admi.mifi.util.signalLevelFromDbm

/**
 * Parser for the XML files exposed by ASR/UAPXB firmware.
 *
 * The reference app reads almost everything from `status1`:
 * `wan.cellular.rssi`, `wan.lte_band`, embedded `statistics.WanStatistics`
 * and embedded `device_management.nr_connected_dev`. Some firmware revisions
 * only serve those blocks as separate files, so both layouts are accepted.
 */
internal object AsrStatusParser {

    fun parse(
        root: XmlNode,
        statisticsXml: String? = null,
        clientsXml: String? = null,
        securityXml: String? = null,
        customXml: String? = null,
    ): DeviceSnapshot {
        val wan = root.child("wan")
        val cellular = wan?.child("cellular")
        val pdpList = cellular?.child("pdp_context_list")?.children.orEmpty()
        val pdp = pdpList.firstOrNull { it.child("default")?.text == "1" } ?: pdpList.firstOrNull()
        val sysinfo = root.child("sysinfo")
        val wlanSecurity = root.child("wlan_security")
        val lan = root.child("lan")

        val sysMode = wan?.rawValue("sys_mode")
        val lteBand = wan?.value("lte_band", "LTE_band")
        val nrBand = wan?.value("nr_band", "NR_band")
        val genericBand = wan?.value("band", "band_current", "current_band")
            ?: root.value("band", "network_band")
        val simStatus = cellular?.rawValue("sim_status") ?: wan?.rawValue("sim_status")
        val sysModeNumber = sysMode?.trim()?.toIntOrNull()
        val noService = sysModeNumber == 0 || simStatus == "1"
        val parsedNetworkType = mapNetworkType(
            wan?.value("network_type", "wan_network_type", "sys_submode", "sys_mode"),
            nrBand ?: lteBand ?: genericBand,
        )
        val networkType = if (noService) "无服务" else parsedNetworkType
        val is5g = !noService && (
            (sysModeNumber != null && sysModeNumber in 18..20) ||
                networkType?.contains("5G") == true ||
                (sysModeNumber == null && nrBand != null)
            )
        val bandRaw = if (is5g) {
            nrBand ?: lteBand ?: genericBand
        } else {
            lteBand ?: nrBand ?: genericBand
        }
        val customSecurity = securityXml?.let(::parseXmlDocument)
        val customRoot = customXml?.let(::parseXmlDocument)
        val customBand = customRoot?.child("customconfig")?.value("band_current")
            ?: customRoot?.value("band_current")
        val band = if (noService) null else formatBand(bandRaw ?: customBand, is5g)

        // The stock ASR UI converts the raw cellular.rssi value using sys_mode:
        // LTE/NR: rssi - 140, 2G/3G: 2 * rssi - 113.
        val cellularRssi = cellular?.value("rssi") ?: wan?.value("rssi")
        val directRsrp = cellular?.value("rsrp", "lte_rsrp", "nr_rsrp")
            ?: wan?.value("rsrp", "lte_rsrp", "nr_rsrp")
        val rscpRaw = wan?.value("rscp")
        val qualityRaw = wan?.value("signal_quality", "signal_level")
        val signalPair = when {
            noService -> null to 0
            cellularRssi != null -> {
                val dbm = normalizeAsrRssi(cellularRssi, sysMode)
                dbm to signalLevelFromDbm(dbm)
            }
            directRsrp != null -> normalizeSignal(directRsrp, asrRawRsrp = true)
            rscpRaw != null -> {
                val raw = firstNumber(rscpRaw)?.toInt()
                val dbm = raw?.let { if (it in -140..-30) it else (-120 + it / 2) }
                dbm to signalLevelFromDbm(dbm)
            }
            qualityRaw != null -> normalizeSignal(qualityRaw, asrRawRsrp = false)
            else -> null to 0
        }

        val connectedValue = asBoolean(wan?.rawValue("wan_conn_status", "connect_status", "network_status"))
        val wanIp = pdp?.value("ipv4", "ip")
            ?: wan?.value("wan_ip", "ipv4", "ip")
        val wanConnected = if (noService) false else connectedValue ?: (wanIp != null)

        val batteryConnect = asBoolean(wan?.rawValue("Battery_connect", "battery_connect"))
        val battery = if (batteryConnect == false) {
            null
        } else {
            normalizeBatteryPercent(wan?.rawValue("Battery_voltage", "battery_percent", "battery_level"))
        }
        val charging = asBoolean(wan?.rawValue("Battery_charging", "Battery_charge"))

        val securityRoot = customSecurity?.let { securityNode(it) }
        val ssidRaw = wlanSecurity?.value("ssid") ?: securityRoot?.value("ssid")
        val ssid = decodeUnicodeHex(ssidRaw)
        val wifiPassword = extractWifiPassword(wlanSecurity) ?: extractWifiPassword(securityRoot)

        val embeddedTraffic = root.path("statistics", "WanStatistics")
        val externalStatsRoot = statisticsXml?.let(::parseXmlDocument)
        val externalTraffic = externalStatsRoot?.path("statistics", "WanStatistics")
            ?: externalStatsRoot?.child("WanStatistics")
            ?: externalStatsRoot?.child("statistics")
        val traffic = embeddedTraffic ?: externalTraffic
        val sessionRx = asLong(traffic?.rawValue("rx_byte"))
        val sessionTx = asLong(traffic?.rawValue("tx_byte"))
        val totalRx = asLong(traffic?.rawValue("rx_byte_all"))
        val totalTx = asLong(traffic?.rawValue("tx_byte_all"))
        val monthlyUsed = asLong(traffic?.rawValue("total_used_month"))
            ?: asLong(traffic?.findValueByTag("total_used_month"))
            ?: asLong(externalStatsRoot?.findValueByTag("total_used_month"))
            ?: asLong(root.findValueByTag("total_used_month"))
        val lifetimeUsed = asLong(traffic?.rawValue("total_used_unlimit"))
            ?: asLong(traffic?.findValueByTag("total_used_unlimit"))
            ?: asLong(externalStatsRoot?.findValueByTag("total_used_unlimit"))

        val externalClientsRoot = clientsXml?.let(::parseXmlDocument)
        val embeddedClientsRoot = root.child("device_management_all") ?: root.child("device_management")
        val allClients = parseXmlClients(externalClientsRoot) + parseXmlClients(embeddedClientsRoot)
        val clients = allClients.filter { it.connected }
            .sortedByDescending { (it.rxBytes ?: 0L) + (it.txBytes ?: 0L) }
        val embeddedCount = asLong(
            root.child("device_management")?.rawValue("nr_connected_dev", "connected_num", "sta_count"),
        )
        val externalCount = asLong(
            externalClientsRoot?.findValueByTag("nr_connected_dev")
                ?: externalClientsRoot?.findValueByTag("connected_num"),
        )
        val connectedCount = maxOf(
            embeddedCount ?: 0L,
            externalCount ?: 0L,
            clients.count { it.connected }.toLong(),
        ).toInt()

        val uptime = buildUptime(lan, pdp)
        val model = sysinfo?.value("model_name", "device_name", "product_name")
            ?: wan?.value("device_model", "model_name")
        val hwVersion = sysinfo?.value("hardware_version", "hw_version", "hw_ver")
        val swVersion = sysinfo?.value("version_num", "software_version", "sw_version", "version_date")
        val iccid = wan?.value("ICCID", "iccid", "sim_iccid")
        val imei = wan?.value("IMEI", "imei", "device_imei")
        val networkName = wan?.value("network_name", "ISP_name", "carrier", "network_provider", "spn")
        val carrier = mapCarrier(iccid, networkName)
            ?: mapCarrier(wan?.value("roaming_network_name"))

        val counterBytes = when {
            totalRx != null || totalTx != null -> (totalRx ?: 0L) + (totalTx ?: 0L)
            lifetimeUsed != null -> lifetimeUsed
            monthlyUsed != null -> monthlyUsed
            else -> null
        }

        return DeviceSnapshot(
            protocol = DeviceProtocol.ASR,
            model = model,
            networkType = networkType,
            carrier = carrier,
            band = band,
            signalDbm = signalPair.first,
            signalLevel = signalPair.second,
            iccid = iccid,
            imei = imei,
            wanIp = wanIp,
            wanConnected = wanConnected,
            batteryPercent = battery,
            charging = charging,
            wifiSsid = ssid,
            wifiPassword = wifiPassword,
            clients = clients,
            monthlyUsedBytes = monthlyUsed,
            sessionRxBytes = sessionRx,
            sessionTxBytes = sessionTx,
            sessionUsedBytes = if (sessionRx != null || sessionTx != null) {
                (sessionRx ?: 0L) + (sessionTx ?: 0L)
            } else {
                null
            },
            totalRxBytes = totalRx,
            totalTxBytes = totalTx,
            realtimeRxBps = asLong(wan?.rawValue("rx_rate")),
            realtimeTxBps = asLong(wan?.rawValue("tx_rate")),
            counterBytes = counterBytes,
            hardwareVersion = hwVersion,
            softwareVersion = swVersion,
            uptimeSeconds = uptime,
            connectedCount = connectedCount,
        )
    }

    private fun securityNode(root: XmlNode): XmlNode =
        root.child("wlan_security") ?: root

    fun extractWifiPassword(security: XmlNode?): String? {
        if (security == null) return null
        val mode = cleanValue(security.value("mode"))
        if (mode != null) {
            val nodeName = mode.replace(Regex("[^A-Za-z0-9_-]"), "")
            if (nodeName.isNotEmpty()) {
                val key = security.child(nodeName)?.value("key")
                if (!key.isNullOrBlank()) return key
            }
        }
        val keyNode = security.descendants("key").firstOrNull() ?: return null
        return cleanValue(keyNode.text)
    }

    private fun buildUptime(lan: XmlNode?, pdp: XmlNode?): Long? {
        val curConn = asLong(pdp?.value("curconntime"))
        if (curConn != null && curConn > 0) return curConn
        val days = asLong(lan?.value("run_days")) ?: 0
        val hours = asLong(lan?.value("run_hours")) ?: 0
        val minutes = asLong(lan?.value("run_minutes")) ?: 0
        val seconds = asLong(lan?.value("run_seconds")) ?: 0
        val total = days * 86400 + hours * 3600 + minutes * 60 + seconds
        return total.takeIf { it > 0 }
    }
}
