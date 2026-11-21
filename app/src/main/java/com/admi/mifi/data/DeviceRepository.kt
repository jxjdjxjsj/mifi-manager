package com.admi.mifi.data

import android.content.Context
import androidx.core.content.edit
import com.admi.mifi.data.api.AsrApi
import com.admi.mifi.data.api.DemoApi
import com.admi.mifi.data.api.DeviceApi
import com.admi.mifi.data.api.GoformApi
import com.admi.mifi.data.api.HostConfig
import com.admi.mifi.data.api.HostResolver
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.Calendar
import java.util.Locale

data class DeviceReadResult(
    val snapshot: DeviceSnapshot,
    val downBps: Long,
    val upBps: Long,
    val monthlyIsLocal: Boolean,
    val sessionIsLocal: Boolean,
    /** Address that actually answered, including an optional configured path prefix. */
    val resolvedAdminUrl: String? = null,
)

class DeviceRepository(context: Context) {

    private val appContext = context.applicationContext
    private val goform = GoformApi()
    private val asr = AsrApi()
    private val demo = DemoApi()
    private val hosts = HostResolver(appContext)

    private val readMutex = Mutex()
    private var engineKey: String = ""
    private var activeApi: DeviceApi? = null
    private var activeHost: HostConfig? = null

    private var lastReadAt = 0L
    private var lastRxCounter: Long? = null
    private var lastTxCounter: Long? = null
    private var lastCombinedCounter: Long? = null

    private var sessionRx = 0L
    private var sessionTx = 0L
    private var sessionCombined = 0L

    private val monthKey: String
        get() {
            val calendar = Calendar.getInstance()
            return String.format(
                Locale.ROOT,
                "%04d-%02d",
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
            )
        }

    @Synchronized
    fun reset(clearSession: Boolean = true) {
        engineKey = ""
        activeApi = null
        activeHost = null
        lastReadAt = 0L
        lastRxCounter = null
        lastTxCounter = null
        lastCombinedCounter = null
        if (clearSession) {
            sessionRx = 0
            sessionTx = 0
            sessionCombined = 0
        }
    }

    suspend fun read(settings: AppSettings): DeviceReadResult = readMutex.withLock {
        readInternal(settings)
    }

    suspend fun performAction(settings: AppSettings, action: DeviceAction): Boolean = readMutex.withLock {
        if (settings.protocol == DeviceProtocol.DEMO) return@withLock true
        val candidates = hosts.candidates(settings.host)
        if (candidates.isEmpty()) throw IOException("管理地址格式不正确")
        val (api, host) = resolveForAction(settings, candidates)
        invokeAction(api, host, settings.password, action)
    }

    private suspend fun invokeAction(
        api: DeviceApi,
        host: HostConfig,
        password: String,
        action: DeviceAction,
    ): Boolean = when (action) {
        DeviceAction.REBOOT -> api.reboot(host, password)
        DeviceAction.SHUTDOWN -> api.shutdown(host, password)
    }

    private suspend fun readInternal(settings: AppSettings): DeviceReadResult {
        val candidates = hosts.candidates(settings.host)
        if (candidates.isEmpty()) {
            throw IOException("管理地址格式不正确，例如 192.168.100.1")
        }
        val configKey = settings.connectionKey
        if (engineKey != configKey) {
            engineKey = configKey
            activeApi = null
            activeHost = null
            lastReadAt = 0L
            lastRxCounter = null
            lastTxCounter = null
            lastCombinedCounter = null
            sessionRx = 0
            sessionTx = 0
            sessionCombined = 0
        }

        val started = System.currentTimeMillis()
        val (snapshot, usedHost) = when (settings.protocol) {
            DeviceProtocol.DEMO -> {
                val host = HostConfig.parse(settings.host) ?: candidates.first()
                demo.read(host, settings.password) to host
            }
            DeviceProtocol.GOFORM -> readProtocol(goform, candidates, settings.password)
            DeviceProtocol.ASR -> readProtocol(asr, candidates, settings.password)
            DeviceProtocol.AUTO -> readAuto(candidates, settings.password)
        }
        val finished = System.currentTimeMillis()
        val dt = if (lastReadAt > 0 && finished > lastReadAt) {
            (finished - lastReadAt).coerceIn(250L, 15_000L) / 1000.0
        } else {
            0.0
        }

        val rxDelta = counterDelta(snapshot.totalRxBytes, lastRxCounter)
        val txDelta = counterDelta(snapshot.totalTxBytes, lastTxCounter)
        val combinedDelta = counterDelta(snapshot.counterBytes, lastCombinedCounter)

        var downBps = snapshot.realtimeRxBps ?: 0L
        var upBps = snapshot.realtimeTxBps ?: 0L
        if (snapshot.realtimeRxBps == null && snapshot.realtimeTxBps == null) {
            when {
                rxDelta != null && txDelta != null && dt > 0 -> {
                    downBps = (rxDelta / dt).toLong()
                    upBps = (txDelta / dt).toLong()
                }
                combinedDelta != null && dt > 0 -> {
                    downBps = (combinedDelta / dt).toLong()
                    upBps = 0L
                }
                rxDelta != null && dt > 0 -> {
                    downBps = (rxDelta / dt).toLong()
                }
                txDelta != null && dt > 0 -> {
                    upBps = (txDelta / dt).toLong()
                }
            }
        }

        val deviceSessionPresent = snapshot.sessionTotalBytes != null
        if (!deviceSessionPresent) {
            when {
                rxDelta != null || txDelta != null -> {
                    sessionRx += (rxDelta ?: 0L).coerceAtLeast(0)
                    sessionTx += (txDelta ?: 0L).coerceAtLeast(0)
                }
                combinedDelta != null -> {
                    sessionCombined += combinedDelta.coerceAtLeast(0)
                }
                dt > 0 && (downBps > 0 || upBps > 0) -> {
                    sessionRx += (downBps * dt).toLong().coerceAtLeast(0)
                    sessionTx += (upBps * dt).toLong().coerceAtLeast(0)
                }
            }
        }

        val localMonthlyBefore = readMonthlyLocal()
        if (snapshot.monthlyTotalBytes == null) {
            val deltaDown: Long
            val deltaUp: Long
            when {
                rxDelta != null || txDelta != null -> {
                    deltaDown = (rxDelta ?: 0L).coerceAtLeast(0)
                    deltaUp = (txDelta ?: 0L).coerceAtLeast(0)
                }
                combinedDelta != null -> {
                    deltaDown = combinedDelta.coerceAtLeast(0)
                    deltaUp = 0L
                }
                dt > 0 -> {
                    deltaDown = (downBps * dt).toLong().coerceAtLeast(0)
                    deltaUp = (upBps * dt).toLong().coerceAtLeast(0)
                }
                else -> {
                    deltaDown = 0L
                    deltaUp = 0L
                }
            }
            if (deltaDown > 0 || deltaUp > 0) {
                writeMonthlyLocal(
                    LocalMonthly(
                        total = localMonthlyBefore.total + deltaDown + deltaUp,
                        rx = localMonthlyBefore.rx + deltaDown,
                        tx = localMonthlyBefore.tx + deltaUp,
                    ),
                )
            }
        }
        val localMonthly = readMonthlyLocal()

        lastReadAt = finished
        snapshot.totalRxBytes?.let { lastRxCounter = it }
        snapshot.totalTxBytes?.let { lastTxCounter = it }
        snapshot.counterBytes?.let { lastCombinedCounter = it }

        val localSessionRx = (sessionRx + sessionCombined).takeIf { it > 0 }
        val localSessionTx = sessionTx.takeIf { it > 0 }
        val enriched = snapshot.copy(
            monthlyUsedBytes = snapshot.monthlyTotalBytes ?: localMonthly.total.takeIf { it > 0 },
            monthlyRxBytes = snapshot.monthlyRxBytes ?: localMonthly.rx.takeIf { it > 0 },
            monthlyTxBytes = snapshot.monthlyTxBytes ?: localMonthly.tx.takeIf { it > 0 },
            sessionUsedBytes = snapshot.sessionTotalBytes
                ?: (sessionRx + sessionTx + sessionCombined).takeIf { it > 0 },
            sessionRxBytes = snapshot.sessionRxBytes ?: localSessionRx,
            sessionTxBytes = snapshot.sessionTxBytes ?: localSessionTx,
        )

        return DeviceReadResult(
            snapshot = enriched,
            downBps = downBps.coerceAtLeast(0),
            upBps = upBps.coerceAtLeast(0),
            monthlyIsLocal = snapshot.monthlyTotalBytes == null,
            sessionIsLocal = !deviceSessionPresent,
            resolvedAdminUrl = usedHost.origin + usedHost.pathPrefix.trimEnd('/') + "/",
        )
    }

    private fun counterDelta(current: Long?, previous: Long?): Long? {
        if (current == null || previous == null) return null
        val delta = current - previous
        return delta.takeIf { it >= 0 }
    }

    private suspend fun readAuto(
        candidates: List<HostConfig>,
        password: String,
    ): Pair<DeviceSnapshot, HostConfig> {
        val attempted = mutableSetOf<String>()
        activeApi?.let { api ->
            activeHost?.let { host ->
                val key = "${api.protocol.storage}|${host.baseUrl}"
                attempted += key
                try {
                    val result = api.read(host, password)
                    if (result.hasCoreData()) return result to host
                } catch (error: Exception) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                }
                activeApi = null
                activeHost = null
            }
        }

        val errors = mutableListOf<String>()
        for (host in candidates) {
            for (api in listOf<DeviceApi>(goform, asr)) {
                val key = "${api.protocol.storage}|${host.baseUrl}"
                if (!attempted.add(key)) continue
                try {
                    val result = api.read(host, password)
                    if (result.hasCoreData()) {
                        activeApi = api
                        activeHost = host
                        return result to host
                    }
                    errors += "${api.protocol.label}（${host.displayHost}）：未返回可识别数据"
                } catch (error: Exception) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    errors += "${api.protocol.label}（${host.displayHost}）：${error.message ?: "连接失败"}"
                }
            }
        }
        activeApi = null
        activeHost = null
        throw IOException(connectionError("无法读取设备信息。", candidates, errors))
    }

    private suspend fun readProtocol(
        api: DeviceApi,
        candidates: List<HostConfig>,
        password: String,
    ): Pair<DeviceSnapshot, HostConfig> {
        if (activeApi === api) {
            val host = activeHost
            if (host != null) {
                try {
                    val result = api.read(host, password)
                    if (result.hasCoreData()) return result to host
                } catch (error: Exception) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                }
                activeApi = null
                activeHost = null
            }
        }

        val errors = mutableListOf<String>()
        for (host in candidates) {
            try {
                val result = api.read(host, password)
                if (result.hasCoreData()) {
                    activeApi = api
                    activeHost = host
                    return result to host
                }
                errors += "${host.displayHost}：未返回可识别数据"
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                errors += "${host.displayHost}：${error.message ?: "连接失败"}"
            }
        }
        activeApi = null
        activeHost = null
        throw IOException(connectionError("${api.protocol.label}：无法读取设备信息。", candidates, errors))
    }

    private suspend fun resolveForAction(
        settings: AppSettings,
        candidates: List<HostConfig>,
    ): Pair<DeviceApi, HostConfig> {
        val currentApi = activeApi
        val currentHost = activeHost
        if (currentApi != null && currentHost != null &&
            (settings.protocol == DeviceProtocol.AUTO || currentApi.protocol == settings.protocol)
        ) {
            return currentApi to currentHost
        }
        return when (settings.protocol) {
            DeviceProtocol.AUTO -> {
                readAuto(candidates, settings.password)
                val api = activeApi
                val host = activeHost
                if (api != null && host != null) {
                    api to host
                } else {
                    throw IOException("尚未连接设备，无法发送操作指令")
                }
            }
            DeviceProtocol.GOFORM -> {
                readProtocol(goform, candidates, settings.password)
                goform to requireNotNull(activeHost)
            }
            DeviceProtocol.ASR -> {
                readProtocol(asr, candidates, settings.password)
                asr to requireNotNull(activeHost)
            }
            DeviceProtocol.DEMO -> throw IOException("演示模式不支持设备操作")
        }
    }

    private fun connectionError(
        title: String,
        candidates: List<HostConfig>,
        errors: List<String>,
    ): String = buildString {
        append(title)
        if (errors.isNotEmpty()) {
            append("\n")
            append(errors.distinct().take(4).joinToString("\n"))
            if (errors.distinct().size > 4) append("\n…")
        }
        if (candidates.size > 1) {
            append("\n已尝试：")
            append(candidates.joinToString("、") { it.displayHost })
        }
        append("\n请确认已连接随身 WiFi，并在设置中检查管理地址和密码。")
    }

    private fun prefs() = appContext.getSharedPreferences("mifi_traffic_ledger", Context.MODE_PRIVATE)

    private data class LocalMonthly(val total: Long, val rx: Long, val tx: Long)

    private fun readMonthlyLocal(): LocalMonthly {
        val prefs = prefs()
        val storedMonth = prefs.getString("month", null)
        if (storedMonth != monthKey) return LocalMonthly(0L, 0L, 0L)
        return LocalMonthly(
            total = prefs.getLong("bytes", 0L),
            rx = prefs.getLong("rx_bytes", 0L),
            tx = prefs.getLong("tx_bytes", 0L),
        )
    }

    private fun writeMonthlyLocal(value: LocalMonthly) {
        prefs().edit {
            putString("month", monthKey)
            putLong("bytes", value.total.coerceAtLeast(0))
            putLong("rx_bytes", value.rx.coerceAtLeast(0))
            putLong("tx_bytes", value.tx.coerceAtLeast(0))
        }
    }
}
