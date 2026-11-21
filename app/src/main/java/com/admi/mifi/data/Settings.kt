package com.admi.mifi.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore by preferencesDataStore(name = "mifi_settings")

data class AppSettings(
    val host: String = "192.168.0.1",
    val password: String = "",
    val protocol: DeviceProtocol = DeviceProtocol.AUTO,
    val designStyle: DesignStyle = DesignStyle.MIUIX,
    val themeMode: ThemeModeOption = ThemeModeOption.SYSTEM,
    val md3ColorStyle: Md3ColorStyle = Md3ColorStyle.TONAL_SPOT,
    val accentArgb: Long = 0xFF3568F4L,
    val monetEnabled: Boolean = false,
    val palette: MiuixPalette = MiuixPalette.DEFAULT,
    val liquidGlass: Boolean = true,
    val floatingBottomBar: Boolean = true,
    val autoRefresh: Boolean = true,
) {
    val connectionKey: String get() = "$host\u0000$password\u0000${protocol.storage}"
    val accentInt: Int get() = accentArgb.toInt()
}

private object Keys {
    val host = stringPreferencesKey("host")
    val password = stringPreferencesKey("password")
    val protocol = stringPreferencesKey("protocol")
    val designStyle = stringPreferencesKey("design_style")
    val templateUi = booleanPreferencesKey("template_ui_v3")
    val themeMode = stringPreferencesKey("theme_mode")
    val md3ColorStyle = stringPreferencesKey("md3_color_style")
    val accent = longPreferencesKey("accent")
    val monet = booleanPreferencesKey("monet")
    val palette = stringPreferencesKey("palette")
    val liquidGlass = booleanPreferencesKey("liquid_glass")
    val floatingBottomBar = booleanPreferencesKey("floating_bottom_bar")
    // Older keys are kept for rollback, but v2 is the source of truth: MD3E
    // ships with the MD3 floating bar enabled by default.
    val floatingBottomBarMd3 = booleanPreferencesKey("floating_bottom_bar_md3")
    val floatingBottomBarMd3V2 = booleanPreferencesKey("floating_bottom_bar_md3_v2")
    val autoRefresh = booleanPreferencesKey("auto_refresh")
}

class SettingsRepository(private val context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    val settings: Flow<AppSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs ->
            AppSettings(
                host = prefs[Keys.host] ?: "192.168.0.1",
                password = prefs[Keys.password] ?: "",
                protocol = DeviceProtocol.from(prefs[Keys.protocol]),
                designStyle = if (prefs[Keys.templateUi] == true) {
                    DesignStyle.from(prefs[Keys.designStyle])
                } else {
                    DesignStyle.MIUIX
                },
                themeMode = ThemeModeOption.from(prefs[Keys.themeMode]),
                md3ColorStyle = Md3ColorStyle.from(prefs[Keys.md3ColorStyle]),
                accentArgb = prefs[Keys.accent] ?: 0xFF3568F4L,
                monetEnabled = prefs[Keys.monet] ?: false,
                palette = MiuixPalette.from(prefs[Keys.palette]),
                liquidGlass = if (prefs[Keys.templateUi] == true) {
                    prefs[Keys.liquidGlass] ?: true
                } else {
                    true
                },
                floatingBottomBar = prefs[Keys.floatingBottomBarMd3V2] ?: true,
                autoRefresh = prefs[Keys.autoRefresh] ?: true,
            )
        }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val current = AppSettings(
                host = prefs[Keys.host] ?: "192.168.0.1",
                password = prefs[Keys.password] ?: "",
                protocol = DeviceProtocol.from(prefs[Keys.protocol]),
                designStyle = if (prefs[Keys.templateUi] == true) {
                    DesignStyle.from(prefs[Keys.designStyle])
                } else {
                    DesignStyle.MIUIX
                },
                themeMode = ThemeModeOption.from(prefs[Keys.themeMode]),
                md3ColorStyle = Md3ColorStyle.from(prefs[Keys.md3ColorStyle]),
                accentArgb = prefs[Keys.accent] ?: 0xFF3568F4L,
                monetEnabled = prefs[Keys.monet] ?: false,
                palette = MiuixPalette.from(prefs[Keys.palette]),
                liquidGlass = if (prefs[Keys.templateUi] == true) {
                    prefs[Keys.liquidGlass] ?: true
                } else {
                    true
                },
                floatingBottomBar = prefs[Keys.floatingBottomBarMd3V2] ?: true,
                autoRefresh = prefs[Keys.autoRefresh] ?: true,
            )
            val next = transform(current)
            prefs[Keys.host] = next.host
            prefs[Keys.password] = next.password
            prefs[Keys.protocol] = next.protocol.storage
            prefs[Keys.designStyle] = next.designStyle.storage
            prefs[Keys.templateUi] = true
            prefs[Keys.themeMode] = next.themeMode.storage
            prefs[Keys.md3ColorStyle] = next.md3ColorStyle.storage
            prefs[Keys.accent] = next.accentArgb
            prefs[Keys.monet] = next.monetEnabled
            prefs[Keys.palette] = next.palette.storage
            prefs[Keys.liquidGlass] = next.liquidGlass
            prefs[Keys.floatingBottomBar] = next.floatingBottomBar
            prefs[Keys.floatingBottomBarMd3] = next.floatingBottomBar
            prefs[Keys.floatingBottomBarMd3V2] = next.floatingBottomBar
            prefs[Keys.autoRefresh] = next.autoRefresh
        }
    }

    suspend fun saveConnection(host: String, password: String, protocol: DeviceProtocol) {
        update { it.copy(host = host.trim(), password = password, protocol = protocol) }
    }
}
