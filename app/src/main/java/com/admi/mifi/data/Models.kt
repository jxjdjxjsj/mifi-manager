package com.admi.mifi.data

enum class DeviceProtocol(val storage: String, val label: String) {
    AUTO("auto", "自动识别"),
    ASR("asr", "ASR 芯片"),
    GOFORM("goform", "Goform 接口"),
    DEMO("demo", "演示模式");

    companion object {
        fun from(value: String?): DeviceProtocol =
            entries.firstOrNull { it.storage == value } ?: AUTO
    }
}

enum class DesignStyle(val storage: String, val label: String) {
    MD3E("md3e", "MD3 Expressive"),
    MIUIX("miuix", "MiuiX");

    companion object {
        fun from(value: String?): DesignStyle =
            entries.firstOrNull { it.storage == value } ?: MD3E
    }
}

enum class ThemeModeOption(val storage: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色");

    companion object {
        fun from(value: String?): ThemeModeOption =
            entries.firstOrNull { it.storage == value } ?: SYSTEM
    }
}

enum class Md3ColorStyle(
    val storage: String,
    val label: String,
    val description: String,
) {
    TONAL_SPOT("tonal_spot", "色调", "均衡舒展"),
    VIBRANT("vibrant", "活力", "明快浓烈"),
    EXPRESSIVE("expressive", "表现力", "大胆跳跃"),
    NEUTRAL("neutral", "中性", "克制低饱和"),
    MONOCHROME("monochrome", "单色", "纯粹黑白"),
    FIDELITY("fidelity", "柔和", "贴近种子色");

    companion object {
        fun from(value: String?): Md3ColorStyle =
            entries.firstOrNull { it.storage == value } ?: TONAL_SPOT
    }
}

enum class MiuixPalette(
    val storage: String,
    val label: String,
    val description: String,
) {
    DEFAULT("default", "默认", "清透、通透"),
    MORANDI("morandi", "莫兰迪", "低饱和高级灰"),
    MACARON("macaron", "马卡龙", "明快、甜美"),
    CYBER("cyber", "赛博", "霓虹反差"),
    INK("ink", "水墨", "近乎无彩");

    companion object {
        fun from(value: String?): MiuixPalette =
            entries.firstOrNull { it.storage == value } ?: DEFAULT
    }
}

data class WifiClient(
    val name: String? = null,
    val mac: String? = null,
    val ip: String? = null,
    val connected: Boolean = true,
    val signalLevel: Int? = null,
    val rxBytes: Long? = null,
    val txBytes: Long? = null,
    val onlineSince: String? = null,
) {
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() && it != "NA" } ?: "未命名设备"

    val displayIp: String
        get() = ip?.takeIf { it.isNotBlank() && it != "NA" } ?: "—"
}

data class DeviceSnapshot(
    val protocol: DeviceProtocol = DeviceProtocol.AUTO,
    val model: String? = null,
    val networkType: String? = null,
    val carrier: String? = null,
    val band: String? = null,
    val signalDbm: Int? = null,
    val signalLevel: Int = 0,
    val iccid: String? = null,
    val imei: String? = null,
    val wanIp: String? = null,
    val wanConnected: Boolean = true,
    val batteryPercent: Int? = null,
    val charging: Boolean? = null,
    val wifiSsid: String? = null,
    val wifiPassword: String? = null,
    val clients: List<WifiClient> = emptyList(),
    val monthlyRxBytes: Long? = null,
    val monthlyTxBytes: Long? = null,
    val monthlyUsedBytes: Long? = null,
    val sessionRxBytes: Long? = null,
    val sessionTxBytes: Long? = null,
    val sessionUsedBytes: Long? = null,
    val totalRxBytes: Long? = null,
    val totalTxBytes: Long? = null,
    /** Cumulative counter used to calculate live throughput when the device exposes one. */
    val counterBytes: Long? = null,
    val realtimeRxBps: Long? = null,
    val realtimeTxBps: Long? = null,
    val hardwareVersion: String? = null,
    val softwareVersion: String? = null,
    val uptimeSeconds: Long? = null,
    val connectedCount: Int? = null,
    val timestamp: Long = System.currentTimeMillis(),
) {
    val monthlyTotalBytes: Long?
        get() = monthlyUsedBytes
            ?: if (monthlyRxBytes == null && monthlyTxBytes == null) {
                null
            } else {
                (monthlyRxBytes ?: 0L) + (monthlyTxBytes ?: 0L)
            }

    val sessionTotalBytes: Long?
        get() = sessionUsedBytes
            ?: if (sessionRxBytes == null && sessionTxBytes == null) {
                null
            } else {
                (sessionRxBytes ?: 0L) + (sessionTxBytes ?: 0L)
            }

    val displayConnectedCount: Int
        get() = connectedCount ?: clients.count { it.connected }

    fun hasCoreData(): Boolean =
        networkType != null || carrier != null || wanIp != null || imei != null ||
            iccid != null || signalDbm != null || wifiSsid != null || model != null
}

enum class DeviceAction { REBOOT, SHUTDOWN }

data class SpeedState(
    val downBps: Long = 0L,
    val upBps: Long = 0L,
    val downHistory: List<Long> = List(HISTORY_SIZE) { 0L },
    val upHistory: List<Long> = List(HISTORY_SIZE) { 0L },
    val peakDownBps: Long = 0L,
    val peakUpBps: Long = 0L,
) {
    fun push(down: Long, up: Long): SpeedState {
        val downValues = (downHistory + down).takeLast(HISTORY_SIZE)
        val upValues = (upHistory + up).takeLast(HISTORY_SIZE)
        return copy(
            downBps = down,
            upBps = up,
            downHistory = downValues,
            upHistory = upValues,
            peakDownBps = maxOf(peakDownBps, down),
            peakUpBps = maxOf(peakUpBps, up),
        )
    }

    companion object {
        const val HISTORY_SIZE = 36
    }
}

data class DeviceConfig(
    val host: String = "192.168.0.1",
    val password: String = "",
    val protocol: DeviceProtocol = DeviceProtocol.AUTO,
) {
    val key: String get() = "$host\u0000$password\u0000${protocol.storage}"
}
