package com.admi.mifi.data.api

import com.admi.mifi.data.DeviceProtocol
import com.admi.mifi.data.DeviceSnapshot
import com.admi.mifi.data.WifiClient
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sin
import kotlin.random.Random

/**
 * A fully local demo source so the complete UI can be explored without hardware.
 */
class DemoApi : DeviceApi {

    override val protocol: DeviceProtocol = DeviceProtocol.DEMO

    private val startedAt = System.currentTimeMillis()
    private val lastReadAt = AtomicLong(startedAt)
    private var downCounter = 6_482_913_047L
    private var upCounter = 483_912_338L
    private var monthlyDown = 42_000_000_000L
    private var monthlyUp = 4_812_045_312L
    private var sessionDown = 824_913_408L
    private var sessionUp = 62_004_881L
    private var random = Random(System.currentTimeMillis())
    private var tick = 0

    override suspend fun read(config: HostConfig, password: String): DeviceSnapshot {
        val now = System.currentTimeMillis()
        val elapsed = ((now - lastReadAt.getAndSet(now)).coerceIn(200L, 8_000L)) / 1000.0
        tick++

        val baseDown = 3_600_000.0 + sin(tick / 3.1) * 1_700_000.0 + random.nextDouble(0.0, 1_400_000.0)
        val baseUp = 380_000.0 + sin(tick / 4.4 + 1.0) * 160_000.0 + random.nextDouble(0.0, 120_000.0)
        val downDelta = (baseDown * elapsed).toLong().coerceAtLeast(0)
        val upDelta = (baseUp * elapsed).toLong().coerceAtLeast(0)
        downCounter += downDelta
        upCounter += upDelta
        monthlyDown += downDelta
        monthlyUp += upDelta
        sessionDown += downDelta
        sessionUp += upDelta

        val signal = -72 + (sin(tick / 5.0) * 7).toInt() + random.nextInt(-2, 3)
        val battery = 86 - ((now - startedAt) / 180_000).toInt().coerceIn(0, 5)
        val ssid = "MiFi_5G_8A2C"
        val clients = listOf(
            WifiClient(
                name = "MacBook Pro",
                mac = "3C:22:FB:8A:10:2C",
                ip = "192.168.0.102",
                connected = true,
                signalLevel = 4,
                rxBytes = 18_004_221_884L,
                txBytes = 1_204_118_932L,
                onlineSince = "2 小时 18 分",
            ),
            WifiClient(
                name = "iPhone 15",
                mac = "A4:83:E7:52:9F:11",
                ip = "192.168.0.101",
                connected = true,
                signalLevel = 3,
                rxBytes = 8_612_892_004L,
                txBytes = 712_004_912L,
                onlineSince = "1 小时 46 分",
            ),
            WifiClient(
                name = "iPad Air",
                mac = "F0:18:98:2C:71:BD",
                ip = "192.168.0.103",
                connected = true,
                signalLevel = 3,
                rxBytes = 3_119_224_800L,
                txBytes = 201_884_120L,
                onlineSince = "48 分钟",
            ),
        )

        return DeviceSnapshot(
            protocol = DeviceProtocol.DEMO,
            model = "ASR1803 5G MiFi",
            networkType = "5G NR",
            carrier = "中国移动",
            band = "n78",
            signalDbm = signal,
            signalLevel = com.admi.mifi.util.signalLevelFromDbm(signal),
            iccid = "89860620982640172845",
            imei = "869320048219607",
            wanIp = "10.82.16.204",
            wanConnected = true,
            batteryPercent = battery,
            charging = false,
            wifiSsid = ssid,
            wifiPassword = "mifi2026",
            clients = clients,
            monthlyRxBytes = monthlyDown,
            monthlyTxBytes = monthlyUp,
            monthlyUsedBytes = monthlyDown + monthlyUp,
            sessionUsedBytes = sessionDown + sessionUp,
            sessionRxBytes = sessionDown,
            sessionTxBytes = sessionUp,
            totalRxBytes = downCounter,
            totalTxBytes = upCounter,
            counterBytes = downCounter + upCounter,
            hardwareVersion = "ASR1803_V1.2",
            softwareVersion = "UAPXB_2.6.18 (2026.09.18)",
            uptimeSeconds = (now - startedAt) / 1000 + 284_912,
            connectedCount = clients.size,
            timestamp = now,
        )
    }

    override suspend fun reboot(config: HostConfig, password: String): Boolean = true

    override suspend fun shutdown(config: HostConfig, password: String): Boolean = true
}
