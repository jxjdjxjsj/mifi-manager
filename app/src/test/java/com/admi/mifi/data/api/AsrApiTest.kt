package com.admi.mifi.data.api

import com.admi.mifi.util.md5Hex
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AsrApiTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = AsrDispatcher()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        Http.cookieJar.clear()
    }

    @Test
    fun parsesReferenceStatusXmlDirectly() {
        val root = parseXmlDocument(STATUS_XML) ?: error("status XML not parsed")
        val snapshot = AsrStatusParser.parse(
            root = root,
            clientsXml = CLIENTS_XML,
            securityXml = SECURITY_XML,
        )

        assertEquals("LV01", snapshot.model)
        assertEquals("4G LTE", snapshot.networkType)
        assertEquals("中国移动", snapshot.carrier)
        assertEquals("B3", snapshot.band)
        assertEquals(-71, snapshot.signalDbm)
        assertEquals("89860012345678901234", snapshot.iccid)
        assertEquals("861234567890123", snapshot.imei)
        assertEquals("10.23.45.67", snapshot.wanIp)
        assertEquals(86, snapshot.batteryPercent)
        assertEquals(true, snapshot.charging)
        assertEquals("MiFi-5G", snapshot.wifiSsid)
        assertEquals("12345678", snapshot.wifiPassword)
        assertEquals("NZ_SSG_V20", snapshot.hardwareVersion)
        assertEquals("NZ_NZ_CP_2.174.000X", snapshot.softwareVersion)
        assertEquals(3, snapshot.connectedCount)
    }

    @Test
    fun readsReferenceStatusFieldsWithDefaultCredentials() = runBlocking {
        val host = HostConfig.parse(server.url("/").toString())!!
        val snapshot = AsrApi().read(host, password = "")

        assertEquals("LV01", snapshot.model)
        assertEquals("4G LTE", snapshot.networkType)
        assertEquals("中国移动", snapshot.carrier)
        assertEquals("B3", snapshot.band)
        assertEquals(-71, snapshot.signalDbm)
        assertEquals(3, snapshot.signalLevel)
        assertEquals("89860012345678901234", snapshot.iccid)
        assertEquals("861234567890123", snapshot.imei)
        assertEquals("10.23.45.67", snapshot.wanIp)
        assertTrue(snapshot.wanConnected)
        assertEquals(86, snapshot.batteryPercent)
        assertEquals(true, snapshot.charging)
        assertEquals(4096L, snapshot.realtimeRxBps)
        assertEquals(2048L, snapshot.realtimeTxBps)
        assertEquals(1_048_576L, snapshot.sessionRxBytes)
        assertEquals(524_288L, snapshot.sessionTxBytes)
        assertEquals(1_572_864L, snapshot.sessionTotalBytes)
        assertEquals(20_971_520L, snapshot.totalRxBytes)
        assertEquals(10_485_760L, snapshot.totalTxBytes)
        assertEquals(31_457_280L, snapshot.counterBytes)
        assertNull(snapshot.monthlyUsedBytes)
        assertEquals("MiFi-5G", snapshot.wifiSsid)
        assertEquals("12345678", snapshot.wifiPassword)
        assertEquals("NZ_SSG_V20", snapshot.hardwareVersion)
        assertEquals("NZ_NZ_CP_2.174.000X", snapshot.softwareVersion)
        assertEquals(3725L, snapshot.uptimeSeconds)
        assertEquals(3, snapshot.connectedCount)
        assertEquals(1, snapshot.clients.size)
        assertEquals("Phone", snapshot.clients.first().name)
        assertEquals("192.168.100.2", snapshot.clients.first().ip)
        assertFalse(snapshot.clients.first().mac.isNullOrBlank())
    }

    private class AsrDispatcher : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val url = request.requestUrl ?: return MockResponse().setResponseCode(400)
            return when (url.encodedPath) {
                "/login.cgi" -> handleLogin(request)
                "/xml_action.cgi" -> handleXml(request)
                else -> MockResponse().setResponseCode(404)
            }
        }

        private fun handleLogin(request: RecordedRequest): MockResponse {
            val url = request.requestUrl ?: return MockResponse().setResponseCode(400)
            if (url.queryParameter("Action") != "Digest") return challenge()
            if (!validQueryDigest(url.toString()) || !validAuthorization(request)) return challenge()
            return MockResponse()
                .setResponseCode(200)
                .setBody("200 OK")
        }

        private fun handleXml(request: RecordedRequest): MockResponse {
            if (!validAuthorization(request)) return challenge()
            val url = request.requestUrl ?: return MockResponse().setResponseCode(400)
            return when (url.queryParameter("file")) {
                "status1" -> xmlResponse(STATUS_XML)
                "uapxb_wlan_security_settings" -> xmlResponse(SECURITY_XML)
                "device_management_all" -> xmlResponse(CLIENTS_XML)
                else -> MockResponse().setResponseCode(404)
            }
        }

        private fun challenge(): MockResponse = MockResponse()
            .setResponseCode(401)
            .addHeader(
                "WWW-Authenticate",
                "Digest realm=\"Highwmg\", nonce=\"$NONCE\", qop=\"auth\"",
            )

        private fun validQueryDigest(rawUrl: String): Boolean {
            val url = rawUrl.toHttpUrlOrNull() ?: return false
            val username = url.queryParameter("username") ?: return false
            val password = passwords[username] ?: return false
            val nonce = url.queryParameter("nonce") ?: return false
            val nc = url.queryParameter("nc") ?: return false
            val cnonce = url.queryParameter("cnonce") ?: return false
            val qop = url.queryParameter("qop") ?: "auth"
            val actual = url.queryParameter("response") ?: return false
            val ha1 = md5Hex("$username:$REALM:$password")
            val expected = md5Hex(
                "$ha1:$nonce:$nc:$cnonce:$qop:${md5Hex("GET:/cgi/protected.cgi")}",
            )
            return actual == expected
        }

        private fun validAuthorization(request: RecordedRequest): Boolean {
            val auth = request.getHeader("Authorization") ?: return false
            if (!auth.startsWith("Digest ", ignoreCase = true)) return false
            val values = parseDigest(auth)
            val username = values["username"] ?: return false
            val password = passwords[username] ?: return false
            val nonce = values["nonce"] ?: return false
            if (nonce != NONCE) return false
            val uri = values["uri"] ?: return false
            val nc = values["nc"] ?: return false
            val cnonce = values["cnonce"] ?: return false
            val qop = values["qop"] ?: "auth"
            val actual = values["response"] ?: return false
            val ha1 = md5Hex("$username:$REALM:$password")
            val expected = md5Hex(
                "$ha1:$nonce:$nc:$cnonce:$qop:${md5Hex("GET:$uri")}",
            )
            return actual == expected
        }

        private fun parseDigest(header: String): Map<String, String> {
            val result = mutableMapOf<String, String>()
            val regex = Regex("""(\w+)=("([^"]*)"|([^,\s]+))""")
            regex.findAll(header).forEach { match ->
                val key = match.groupValues[1].lowercase()
                val value = match.groupValues[3].ifEmpty { match.groupValues[4] }
                result[key] = value
            }
            return result
        }

        private fun xmlResponse(body: String): MockResponse = MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/xml; charset=utf-8")
            .setBody(body)
    }

    private companion object {
        const val REALM = "Highwmg"
        const val NONCE = "0123456789abcdef"
        val passwords = mapOf("admin" to "admin", "sysadmin" to "sysadmin")

        val STATUS_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <RGW>
              <wan>
                <sys_mode>17</sys_mode>
                <network_name>CMCC</network_name>
                <ICCID>89860012345678901234</ICCID>
                <IMEI>861234567890123</IMEI>
                <lte_band>3</lte_band>
                <Battery_voltage>86</Battery_voltage>
                <Battery_charge>1</Battery_charge>
                <tx_rate>2048</tx_rate>
                <rx_rate>4096</rx_rate>
                <cellular>
                  <rssi>69</rssi>
                  <sim_status>0</sim_status>
                  <pdp_context_list>
                    <Item>
                      <default>1</default>
                      <ipv4>10.23.45.67</ipv4>
                      <curconntime>3725</curconntime>
                    </Item>
                  </pdp_context_list>
                </cellular>
              </wan>
              <statistics>
                <WanStatistics>
                  <rx_byte>1048576</rx_byte>
                  <tx_byte>524288</tx_byte>
                  <rx_byte_all>20971520</rx_byte_all>
                  <tx_byte_all>10485760</tx_byte_all>
                </WanStatistics>
              </statistics>
              <device_management>
                <nr_connected_dev>3</nr_connected_dev>
              </device_management>
              <sysinfo>
                <model_name>LV01</model_name>
                <hardware_version>NZ_SSG_V20</hardware_version>
                <version_num>NZ_NZ_CP_2.174.000X</version_num>
              </sysinfo>
            </RGW>
        """.trimIndent()

        val SECURITY_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <RGW>
              <wlan_security>
                <ssid>004D006900460069002D00350047</ssid>
                <mode>WPA2_PSK</mode>
                <WPA2_PSK>
                  <key>12345678</key>
                </WPA2_PSK>
              </wlan_security>
            </RGW>
        """.trimIndent()

        val CLIENTS_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <RGW>
              <device_management_all>
                <client_list>
                  <Item>
                    <name>Phone</name>
                    <mac>AA:BB:CC:DD:EE:FF</mac>
                    <ip_address>192.168.100.2</ip_address>
                    <status>1</status>
                  </Item>
                </client_list>
              </device_management_all>
            </RGW>
        """.trimIndent()
    }
}
