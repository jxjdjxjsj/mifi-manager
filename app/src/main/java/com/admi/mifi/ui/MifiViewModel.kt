package com.admi.mifi.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.admi.mifi.data.AppSettings
import com.admi.mifi.data.DesignStyle
import com.admi.mifi.data.DeviceAction
import com.admi.mifi.data.DeviceRepository
import com.admi.mifi.data.DeviceSnapshot
import com.admi.mifi.data.Md3ColorStyle
import com.admi.mifi.data.MiuixPalette
import com.admi.mifi.data.SettingsRepository
import com.admi.mifi.data.SpeedState
import com.admi.mifi.data.ThemeModeOption
import com.admi.mifi.data.DeviceProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

data class AppUiState(
    val settings: AppSettings = AppSettings(),
    val settingsLoaded: Boolean = false,
    val snapshot: DeviceSnapshot? = null,
    val speed: SpeedState = SpeedState(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val lastUpdated: Long = 0L,
    val monthlyIsLocal: Boolean = false,
    val sessionIsLocal: Boolean = false,
    val resolvedAdminUrl: String? = null,
    val actionInProgress: DeviceAction? = null,
    val testInProgress: Boolean = false,
    val testMessage: String? = null,
) {
    val detectedProtocol: DeviceProtocol?
        get() = snapshot?.protocol?.takeIf { it != DeviceProtocol.AUTO }
}

sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
}

class MifiViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val repository = DeviceRepository(application)
    private val refreshMutex = Mutex()

    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private var pollingJob: Job? = null
    private var foreground = false
    private var lastConnectionKey: String? = null

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                val previous = lastConnectionKey
                val first = previous == null
                val connectionChanged = previous != null && previous != settings.connectionKey
                lastConnectionKey = settings.connectionKey
                _state.update { it.copy(settings = settings, settingsLoaded = true) }
                if (first || connectionChanged) {
                    if (connectionChanged) {
                        repository.reset()
                        _state.update {
                            it.copy(
                                snapshot = null,
                                speed = SpeedState(),
                                error = null,
                                isLoading = true,
                                monthlyIsLocal = false,
                                sessionIsLocal = false,
                                resolvedAdminUrl = null,
                                lastUpdated = 0L,
                            )
                        }
                    }
                    restartPolling()
                }
            }
        }
    }

    fun onForeground() {
        foreground = true
        restartPolling()
    }

    fun onBackground() {
        foreground = false
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun restartPolling() {
        pollingJob?.cancel()
        pollingJob = null
        if (!foreground || !_state.value.settingsLoaded) return
        pollingJob = viewModelScope.launch {
            refreshInternal(manual = _state.value.snapshot == null)
            while (true) {
                delay(if (_state.value.settings.autoRefresh) 2800L else 20_000L)
                refreshInternal(manual = false)
            }
        }
    }

    private suspend fun refreshInternal(manual: Boolean) {
        refreshMutex.withLock {
            if (!_state.value.settingsLoaded) return
            if (manual) {
                _state.update { it.copy(isRefreshing = true, error = null) }
            }
            try {
                val result = repository.read(_state.value.settings)
                val now = System.currentTimeMillis()
                _state.update { current ->
                    current.copy(
                        snapshot = result.snapshot,
                        speed = current.speed.push(result.downBps, result.upBps),
                        isLoading = false,
                        isRefreshing = false,
                        error = null,
                        lastUpdated = now,
                        monthlyIsLocal = result.monthlyIsLocal,
                        sessionIsLocal = result.sessionIsLocal,
                        resolvedAdminUrl = result.resolvedAdminUrl,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = error.message ?: "刷新失败，请检查设备连接",
                    )
                }
            } finally {
                _state.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { refreshInternal(manual = true) }
    }

    fun saveConnection(host: String, password: String, protocol: DeviceProtocol) {
        val next = _state.value.settings.copy(
            host = host.trim(),
            password = password,
            protocol = protocol,
        )
        _state.update {
            it.copy(
                settings = next,
                snapshot = null,
                speed = SpeedState(),
                error = null,
                isLoading = true,
                monthlyIsLocal = false,
                sessionIsLocal = false,
                lastUpdated = 0L,
            )
        }
        lastConnectionKey = next.connectionKey
        viewModelScope.launch {
            settingsRepository.saveConnection(next.host, next.password, next.protocol)
            repository.reset()
            restartPolling()
            _events.tryEmit(UiEvent.Message("连接设置已保存"))
        }
    }

    fun testConnection(host: String, password: String, protocol: DeviceProtocol) {
        if (_state.value.testInProgress) return
        viewModelScope.launch {
            _state.update { it.copy(testInProgress = true, testMessage = null) }
            try {
                val testRepository = DeviceRepository(getApplication())
                val result = withTimeout(40_000) {
                    testRepository.read(
                        _state.value.settings.copy(
                            host = host.trim(),
                            password = password,
                            protocol = protocol,
                        ),
                    )
                }
                val snapshot = result.snapshot
                val summary = buildString {
                    append("连接成功 · ")
                    append(snapshot.protocol.label)
                    snapshot.networkType?.let { append(" · $it") }
                    snapshot.carrier?.let { append(" · $it") }
                }
                _state.update { it.copy(testInProgress = false, testMessage = summary) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        testInProgress = false,
                        testMessage = "连接失败：${error.message ?: "未知错误"}",
                    )
                }
            }
        }
    }

    fun clearTestMessage() {
        _state.update { it.copy(testMessage = null) }
    }

    fun performAction(action: DeviceAction) {
        if (_state.value.actionInProgress != null) return
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = action) }
            try {
                val success = repository.performAction(_state.value.settings, action)
                if (success) {
                    val message = when (action) {
                        DeviceAction.REBOOT -> "重启指令已发送，设备将在约一分钟后恢复"
                        DeviceAction.SHUTDOWN -> "关机指令已发送，请按机身电源键重新开机"
                    }
                    _state.update {
                        it.copy(
                            snapshot = it.snapshot?.copy(wanConnected = false),
                            error = null,
                        )
                    }
                    _events.tryEmit(UiEvent.Message(message))
                    if (action == DeviceAction.REBOOT) {
                        delay(6_000)
                        refresh()
                    }
                } else {
                    _events.tryEmit(UiEvent.Message("设备未确认操作，请稍后重试"))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _events.tryEmit(UiEvent.Message(error.message ?: "操作失败，请稍后重试"))
            } finally {
                _state.update { it.copy(actionInProgress = null) }
            }
        }
    }

    fun enableDemo() {
        saveConnection(
            host = _state.value.settings.host,
            password = _state.value.settings.password,
            protocol = DeviceProtocol.DEMO,
        )
    }

    fun useAutoDetect() {
        saveConnection(
            host = _state.value.settings.host,
            password = _state.value.settings.password,
            protocol = DeviceProtocol.AUTO,
        )
    }

    fun setDesignStyle(style: DesignStyle) = updateSettings { it.copy(designStyle = style) }

    fun setThemeMode(mode: ThemeModeOption) = updateSettings { it.copy(themeMode = mode) }

    fun setMd3ColorStyle(style: Md3ColorStyle) = updateSettings { it.copy(md3ColorStyle = style) }

    fun setAccentColor(argb: Long) = updateSettings { it.copy(accentArgb = argb) }

    fun setMonetEnabled(enabled: Boolean) = updateSettings { it.copy(monetEnabled = enabled) }

    fun setPalette(palette: MiuixPalette) = updateSettings { it.copy(palette = palette) }

    fun setLiquidGlass(enabled: Boolean) = updateSettings { it.copy(liquidGlass = enabled) }

    fun setFloatingBottomBar(enabled: Boolean) = updateSettings { it.copy(floatingBottomBar = enabled) }

    fun setAutoRefresh(enabled: Boolean) {
        updateSettings { it.copy(autoRefresh = enabled) }
        if (foreground) restartPolling()
    }

    fun resetAppearance() = updateSettings {
        it.copy(
            designStyle = DesignStyle.MD3E,
            themeMode = ThemeModeOption.SYSTEM,
            md3ColorStyle = Md3ColorStyle.TONAL_SPOT,
            accentArgb = 0xFF3568F4L,
            monetEnabled = false,
            palette = MiuixPalette.DEFAULT,
            liquidGlass = true,
            floatingBottomBar = true,
        )
    }

    private fun updateSettings(transform: (AppSettings) -> AppSettings) {
        _state.update { current -> current.copy(settings = transform(current.settings)) }
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    override fun onCleared() {
        pollingJob?.cancel()
        super.onCleared()
    }
}
