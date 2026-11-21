package com.admi.mifi.data.api

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GoformApiTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = GoformDispatcher()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        Http.cookieJar.clear()
    }

    @Test
    fun logsInWithReferenceDefaultAndReadsZteFields() = runBlocking {
        val host = HostConfig.parse(server.url("/").toString())!!
        val snapshot = GoformApi().read(host, password = "")

        assertEquals("ZXW-1", snapshot.model)
        assertEquals("4G LTE", snapshot.networkType)
        assertEquals("中国联通", snapshot.carrier)
        assertEquals("B1", snapshot.band)
        assertEquals(-74, snapshot.signalDbm)
        assertEquals(3, snapshot.signalLevel)
        assertEquals("89860112345678901234", snapshot.iccid)
        assertEquals("861234567890123", snapshot.imei)
        assertEquals("10.1.2.3", snapshot.wanIp)
        assertTrue(snapshot.wanConnected)
        assertEquals(88, snapshot.batteryPercent)
        assertEquals(true, snapshot.charging)
        assertEquals("MyWiFi", snapshot.wifiSsid)
        assertEquals("pass1234", snapshot.wifiPassword)
        assertEquals(1_000_000L, snapshot.monthlyRxBytes)
        assertEquals(500_000L, snapshot.monthlyTxBytes)
        assertEquals(1_500_000L, snapshot.monthlyTotalBytes)
        assertEquals(7_000L, snapshot.sessionRxBytes)
        assertEquals(3_000L, snapshot.sessionTxBytes)
        assertEquals(10_000L, snapshot.sessionTotalBytes)
        assertEquals(2_048L, snapshot.realtimeRxBps)
        assertEquals(1_024L, snapshot.realtimeTxBps)
        assertEquals("HW-V1", snapshot.hardwareVersion)
        assertEquals("SW-V2", snapshot.softwareVersion)
        assertEquals(3_661L, snapshot.uptimeSeconds)
        assertEquals(2, snapshot.connectedCount)
        assertEquals(1, snapshot.clients.size)
        assertEquals("Tablet", snapshot.clients.first().name)
        assertEquals("192.168.0.3", snapshot.clients.first().ip)
        assertFalse(snapshot.clients.first().mac.isNullOrBlank())
    }

    private class GoformDispatcher : Dispatcher() {
        private var loggedIn = false

        override fun dispatch(request: RecordedRequest): MockResponse {
            val url = request.requestUrl ?: return MockResponse().setResponseCode(400)
            return when (url.encodedPath) {
                "/goform/goform_get_cmd_process" -> handleGet(request)
                "/goform/goform_set_cmd_process" -> handleLogin(request)
                else -> MockResponse().setResponseCode(404)
            }
        }

        private fun handleGet(request: RecordedRequest): MockResponse {
            if (!loggedIn || request.getHeader("Cookie")?.contains("SESSION=ok") != true) {
                return MockResponse().setResponseCode(401)
            }
            val command = request.requestUrl?.queryParameter("cmd").orEmpty()
            if (command == "station_list") {
                val item = JSONObject()
                    .put("hostname", "Tablet")
                    .put("mac", "AA:BB:CC:DD:EE:01")
                    .put("ip_address", "192.168.0.3")
                    .put("status", "1")
                return json(JSONObject().put("station_list", JSONArray().put(item)))
            }
            val requested = command.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            val result = JSONObject()
            for (name in requested) {
                values[name]?.let { result.put(name, it) }
            }
            return json(result)
        }

        private fun handleLogin(request: RecordedRequest): MockResponse {
            val body = request.body.readUtf8()
            if (!body.contains("goformId=LOGIN") || !body.contains("YWRtaW4%3D")) {
                return MockResponse().setResponseCode(401)
            }
            loggedIn = true
            return json(JSONObject().put("result", "0"))
                .addHeader("Set-Cookie", "SESSION=ok; Path=/")
        }

        private fun json(value: JSONObject): MockResponse = MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json; charset=utf-8")
            .setBody(value.toString())

        private companion object {
            val values = mapOf(
                "network_type" to "LTE",
                "sub_network_type" to "LTE",
                "network_provider" to "CMCC",
                "battery_vol_percent" to "88",
                "battery_charging" to "1",
                "realtime_tx_thrpt" to "1024",
                "realtime_rx_thrpt" to "2048",
                "monthly_rx_bytes" to "1000000",
                "monthly_tx_bytes" to "500000",
                "realtime_tx_bytes" to "3000",
                "realtime_rx_bytes" to "7000",
                "imei" to "861234567890123",
                "lte_rsrp" to "66",
                "ziccid" to "89860112345678901234",
                "ssid1" to "MyWiFi",
                "wpapsk1_encode" to "cGFzczEyMzQ=",
                "wan_ipaddr" to "10.1.2.3",
                "sta_count" to "2",
                "lte_band" to "1",
                "hw_version" to "HW-V1",
                "cr_version" to "SW-V2",
                "realtime_time" to "3661",
                "device_model" to "ZXW-1",
            )
        }
    }
}
