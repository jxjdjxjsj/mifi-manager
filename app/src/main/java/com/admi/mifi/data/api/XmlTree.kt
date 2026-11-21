package com.admi.mifi.data.api

import com.admi.mifi.data.WifiClient
import com.admi.mifi.util.asBoolean
import com.admi.mifi.util.asLong
import com.admi.mifi.util.cleanValue
import com.admi.mifi.util.firstNumber
import com.admi.mifi.util.signalLevelFromDbm
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

class XmlNode(val name: String) {
    val children = mutableListOf<XmlNode>()
    private val textBuilder = StringBuilder()

    val text: String
        get() = textBuilder.toString().trim()

    fun addText(value: String) {
        textBuilder.append(value)
    }

    fun child(name: String): XmlNode? =
        children.firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun path(vararg names: String): XmlNode? {
        var current: XmlNode = this
        for (name in names) {
            current = current.child(name) ?: return null
        }
        return current
    }

    fun value(vararg names: String): String? {
        for (name in names) {
            val node = child(name) ?: continue
            cleanValue(node.text)?.let { return it }
        }
        return null
    }

    /** Returns the trimmed text even when it is "0"; useful for boolean flags. */
    fun rawValue(vararg names: String): String? {
        for (name in names) {
            val node = child(name) ?: continue
            node.text.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return null
    }

    fun descendants(name: String): List<XmlNode> {
        val result = mutableListOf<XmlNode>()
        fun visit(node: XmlNode) {
            for (child in node.children) {
                if (child.name.equals(name, ignoreCase = true)) result += child
                visit(child)
            }
        }
        visit(this)
        return result
    }

    fun flatten(prefix: String = "", output: MutableMap<String, String> = LinkedHashMap()): Map<String, String> {
        val path = if (prefix.isEmpty()) name else "$prefix.$name"
        val value = cleanValue(text)
        if (value != null) {
            output[path.lowercase(Locale.ROOT)] = value
            output[name.lowercase(Locale.ROOT)] = value
        }
        for (child in children) {
            child.flatten(path, output)
        }
        return output
    }

    fun findValueByTag(vararg names: String): String? {
        val wanted = names.map { it.lowercase(Locale.ROOT) }.toSet()
        fun visit(node: XmlNode): String? {
            if (node.name.lowercase(Locale.ROOT) in wanted) {
                cleanValue(node.text)?.let { return it }
            }
            for (child in node.children) {
                visit(child)?.let { return it }
            }
            return null
        }
        return visit(this)
    }
}

fun parseXmlDocument(xml: String): XmlNode? {
    val start = xml.indexOf('<')
    if (start < 0) return null
    val end = xml.lastIndexOf('>')
    if (end <= start) return null
    val document = xml.substring(start, end + 1)
    return runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        }
        val builder = factory.newDocumentBuilder()
        val parsed = builder.parse(InputSource(StringReader(document)))
        val rootElement = parsed.documentElement ?: return@runCatching null
        convertElement(rootElement)
    }.getOrNull()
}

private fun convertElement(element: Element): XmlNode {
    val node = XmlNode(element.tagName ?: "node")
    val children = element.childNodes
    for (index in 0 until children.length) {
        when (val child = children.item(index)) {
            is Element -> node.children += convertElement(child)
            else -> if (child.nodeType == Node.TEXT_NODE || child.nodeType == Node.CDATA_SECTION_NODE) {
                node.addText(child.nodeValue ?: "")
            }
        }
    }
    return node
}

fun parseXmlClients(xml: XmlNode?): List<WifiClient> {
    if (xml == null) return emptyList()
    val candidateNodes = mutableListOf<XmlNode>()
    val containerNames = setOf(
        "client_list", "station_list", "device_list", "host_list",
        "wlan_cli_list", "associated_device", "clients", "stations",
    )
    val itemNames = setOf("item", "client", "station", "device", "host", "entry", "user")

    fun collect(container: XmlNode) {
        fun visit(node: XmlNode, depth: Int) {
            if (depth > 0 && node.name.lowercase(Locale.ROOT) in itemNames) {
                candidateNodes += node
                return
            }
            for (child in node.children) visit(child, depth + 1)
        }
        visit(container, 0)
    }

    for (container in xml.descendants("client_list") + xml.descendants("station_list") +
        xml.descendants("device_list") + xml.descendants("host_list")) {
        collect(container)
    }
    if (candidateNodes.isEmpty()) {
        for (name in containerNames) {
            xml.descendants(name).forEach { collect(it) }
        }
    }
    if (candidateNodes.isEmpty()) {
        candidateNodes += xml.children.filter {
            it.name.lowercase(Locale.ROOT) in itemNames && it.children.isNotEmpty()
        }
    }

    return candidateNodes.mapNotNull { node ->
        val mac = node.findValueByTag("mac", "mac_address", "macaddr", "macaddress", "mac_addr", "hwaddr", "bssid")
        val ip = node.findValueByTag("ip_address", "ipaddress", "ip_addr", "ip", "ipaddr", "ipv4")
        val name = node.findValueByTag("name", "hostname", "host_name", "device_name", "ssid")
        if (mac == null && ip == null && name == null) return@mapNotNull null
        val statusRaw = listOf("status", "online", "connected", "active", "connect_status")
            .firstNotNullOfOrNull { tag ->
                node.descendants(tag).firstOrNull()?.text?.takeIf { it.isNotEmpty() }
            }
        WifiClient(
            name = name,
            mac = mac?.uppercase(Locale.ROOT),
            ip = ip,
            connected = asBoolean(statusRaw) ?: true,
            signalLevel = parseClientSignal(node.findValueByTag("signal", "rssi", "signal_level", "signal_strength")),
            rxBytes = asLong(node.findValueByTag("rx_data_used", "rxbytes", "rx_bytes", "download")),
            txBytes = asLong(node.findValueByTag("tx_data_used", "txbytes", "tx_bytes", "upload")),
            onlineSince = cleanValue(node.findValueByTag("conn_time_at", "connect_time", "online_time")),
        )
    }.distinctBy { it.mac ?: it.ip ?: it.displayName }
}

fun parseClientJsonOrText(raw: String?): List<WifiClient> {
    val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
    val clients = mutableListOf<WifiClient>()

    fun addFromObject(obj: org.json.JSONObject) {
        val mac = cleanValue(
            obj.optString("mac")
                .ifBlank { obj.optString("mac_address") }
                .ifBlank { obj.optString("mac_addr") }
                .ifBlank { obj.optString("macaddress") }
                .ifBlank { obj.optString("hwaddr") },
        )
        val ip = cleanValue(
            obj.optString("ip")
                .ifBlank { obj.optString("ip_address") }
                .ifBlank { obj.optString("ip_addr") }
                .ifBlank { obj.optString("ipaddress") }
                .ifBlank { obj.optString("ipaddr") },
        )
        val name = cleanValue(
            obj.optString("name")
                .ifBlank { obj.optString("hostname") }
                .ifBlank { obj.optString("host_name") }
                .ifBlank { obj.optString("device_name") },
        )
        if (mac == null && ip == null && name == null) return
        clients += WifiClient(
            name = name,
            mac = mac?.uppercase(Locale.ROOT),
            ip = ip,
            connected = asBoolean(
                obj.optString("status")
                    .ifBlank { obj.optString("online") }
                    .ifBlank { obj.optString("connected") }
                    .ifBlank { obj.optString("active") },
            ) ?: true,
            signalLevel = parseClientSignal(obj.optString("signal").ifBlank { obj.optString("rssi") }),
            rxBytes = asLong(obj.optString("rx_bytes").ifBlank { obj.optString("rx_data_used") }),
            txBytes = asLong(obj.optString("tx_bytes").ifBlank { obj.optString("tx_data_used") }),
            onlineSince = cleanValue(obj.optString("conn_time_at").ifBlank { obj.optString("connect_time") }),
        )
    }

    val json = runCatching { org.json.JSONTokener(text).nextValue() }.getOrNull()
    when (json) {
        is org.json.JSONArray -> {
            for (index in 0 until json.length()) {
                when (val item = json.opt(index)) {
                    is org.json.JSONObject -> addFromObject(item)
                    is String -> addFromObject(org.json.JSONObject(item))
                }
            }
        }
        is org.json.JSONObject -> {
            // Some firmwares return {"1":{"mac":...}} or {"mac": "ip;name"}.
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                when (val value = json.opt(key)) {
                    is org.json.JSONObject -> addFromObject(value)
                    is org.json.JSONArray -> {
                        for (index in 0 until value.length()) {
                            (value.opt(index) as? org.json.JSONObject)?.let { addFromObject(it) }
                        }
                    }
                    is String -> {
                        val mac = key.takeIf { it.matches(Regex("(?i)([0-9a-f]{2}[:-]){5}[0-9a-f]{2}")) }
                        if (mac != null) {
                            val parts = value.split(';', ',', '|').map { it.trim() }.filter { it.isNotEmpty() }
                            clients += WifiClient(
                                name = parts.getOrNull(1),
                                mac = mac.uppercase(Locale.ROOT),
                                ip = parts.firstOrNull(),
                                connected = true,
                            )
                        }
                    }
                }
            }
        }
    }

    if (clients.isEmpty()) {
        val macRegex = Regex("(?i)([0-9a-f]{2}[:-]){5}[0-9a-f]{2}")
        for (match in macRegex.findAll(text)) {
            val mac = match.value
            val around = text.substring(
                maxOf(0, match.range.first - 32),
                minOf(text.length, match.range.last + 64),
            )
            val ipMatch = Regex("(\\d{1,3}(?:\\.\\d{1,3}){3})").find(around)
            clients += WifiClient(
                name = null,
                mac = mac.uppercase(Locale.ROOT),
                ip = ipMatch?.groupValues?.getOrNull(1),
                connected = true,
            )
        }
    }

    return clients.distinctBy { it.mac ?: it.ip ?: it.displayName }
}

private fun parseClientSignal(raw: String?): Int? {
    val value = firstNumber(raw)?.toInt() ?: return null
    return when {
        value in 0..5 -> value.coerceIn(0, 4)
        value in -140..-30 -> signalLevelFromDbm(value).coerceIn(0, 4)
        else -> null
    }
}
