package com.admi.mifi.data.api

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.util.Locale

/**
 * Chooses useful management-address candidates.
 *
 * Real portable WiFi routers often use a LAN subnet that differs from the
 * hard-coded vendor default.  When the phone is connected to the router's
 * hotspot, the DHCP gateway is the most reliable address.  Legacy/default
 * values are treated as "auto" so users upgrading from older builds also
 * benefit without editing settings first.
 */
class HostResolver(context: Context) {

    private val appContext = context.applicationContext

    fun candidates(configured: String): List<HostConfig> {
        val trimmed = configured.trim()
        val configuredConfig = HostConfig.parse(trimmed)
        val configuredKey = configuredConfig?.let {
            "${it.baseUrl.host.lowercase(Locale.ROOT)}:${it.baseUrl.port}"
        }
        val auto = trimmed.isBlank() || configuredKey in defaultHostKeys
        val gateway = currentWifiGateway()

        val result = mutableListOf<HostConfig>()
        val seen = mutableSetOf<String>()

        fun add(raw: String?) {
            val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return
            val config = HostConfig.parse(value) ?: return
            val key = "${config.baseUrl.host.lowercase(Locale.ROOT)}:${config.baseUrl.port}|${config.pathPrefix}"
            if (seen.add(key)) result += config
        }

        if (!auto) add(trimmed)
        add(gateway)
        add(trimmed)
        if (auto) {
            defaultHosts.forEach(::add)
        } else if (result.isEmpty()) {
            defaultHosts.forEach(::add)
        }
        if (result.isEmpty()) defaultHosts.forEach(::add)
        return result
    }

    fun currentWifiGateway(): String? {
        val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        return runCatching {
            manager.allNetworks.firstNotNullOfOrNull { network ->
                val capabilities = manager.getNetworkCapabilities(network) ?: return@firstNotNullOfOrNull null
                if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    return@firstNotNullOfOrNull null
                }
                val link = manager.getLinkProperties(network) ?: return@firstNotNullOfOrNull null
                val routeGateway = link.routes
                    .asSequence()
                    .sortedByDescending { it.isDefaultRoute }
                    .mapNotNull { it.gateway }
                    .filterIsInstance<Inet4Address>()
                    .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress && it.isSiteLocalAddress }
                    ?.hostAddress
                val dhcpGateway = (link.dhcpServerAddress as? Inet4Address)
                    ?.takeIf { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                    ?.hostAddress
                routeGateway ?: dhcpGateway
            }
        }.getOrNull()
    }

    private companion object {
        val defaultHosts = listOf("192.168.100.1", "192.168.0.1", "m.home")
        val defaultHostKeys = setOf(
            "192.168.100.1:80",
            "192.168.0.1:80",
            "m.home:80",
        )
    }
}
