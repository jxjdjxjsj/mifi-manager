package com.admi.mifi.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.admi.mifi.BuildConfig
import com.admi.mifi.data.AppSettings
import com.admi.mifi.data.DesignStyle
import com.admi.mifi.data.DeviceProtocol
import com.admi.mifi.data.Md3ColorStyle
import com.admi.mifi.data.MiuixPalette
import com.admi.mifi.data.ThemeModeOption
import com.admi.mifi.ui.AppUiState
import com.admi.mifi.ui.MifiViewModel
import com.admi.mifi.ui.components.ClickableSettingRow
import com.admi.mifi.ui.components.MifiCard
import com.admi.mifi.ui.components.MiniTag
import com.admi.mifi.ui.components.ScreenHeader
import com.admi.mifi.ui.components.SectionHeader
import com.admi.mifi.ui.components.SelectableIndicator
import com.admi.mifi.ui.theme.LocalDesignStyle
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeExpressive
import com.materialkolor.scheme.SchemeFidelity
import com.materialkolor.scheme.SchemeFruitSalad
import com.materialkolor.scheme.SchemeMonochrome
import com.materialkolor.scheme.SchemeNeutral
import com.materialkolor.scheme.SchemeRainbow
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.scheme.SchemeVibrant

private val accentPresets = listOf(
    0xFF3568F4L,
    0xFF7257E8L,
    0xFFD64A8AL,
    0xFFE05A3AL,
    0xFFE08A24L,
    0xFF2B9A72L,
    0xFF1686A8L,
    0xFF5F7186L,
)

@Composable
fun SettingsScreen(
    state: AppUiState,
    viewModel: MifiViewModel,
    onOpenUrl: (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    val settings = state.settings
    val miuix = LocalDesignStyle.current == DesignStyle.MIUIX
    var showConnectionDialog by remember { mutableStateOf(false) }
    var showAccentDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(top = 18.dp, bottom = 130.dp),
    ) {
        ScreenHeader(
            title = "设置",
            subtitle = "连接设备、切换设计风格与调整外观",
        )
        Spacer(Modifier.height(if (miuix) 16.dp else 20.dp))

        if (!miuix) {
            SectionHeader(title = "连接", subtitle = "从随身 WiFi 的管理页面读取数据")
            Spacer(Modifier.height(10.dp))
        }
        MifiCard(contentPadding = PaddingValues(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Router,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = settings.host.ifBlank { "未设置管理地址" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MiniTag(text = protocolLabel(state))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (settings.password.isBlank()) "无密码" else "已设置密码",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(
                    onClick = {
                        viewModel.clearTestMessage()
                        showConnectionDialog = true
                    },
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("修改")
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        viewModel.clearTestMessage()
                        viewModel.testConnection(settings.host, settings.password, settings.protocol)
                    },
                    enabled = !state.testInProgress,
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.testInProgress) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(7.dp))
                    } else {
                        Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text("测试连接")
                }
                OutlinedButton(
                    onClick = { onOpenUrl(adminUrl(settings.host)) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("管理页面")
                }
            }
            val testMessage = state.testMessage
            if (testMessage != null) {
                Spacer(Modifier.height(10.dp))
                val failed = testMessage.startsWith("连接失败")
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (failed) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                    },
                ) {
                    Text(
                        text = testMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (failed) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(if (miuix) 14.dp else 24.dp))
        if (!miuix) {
            SectionHeader(title = "外观", subtitle = "选择 MD3 Expressive 或 MiuiX 视觉风格")
            Spacer(Modifier.height(10.dp))
        }
        MifiCard(contentPadding = PaddingValues(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StyleOption(
                    style = DesignStyle.MD3E,
                    title = "MD3 Expressive",
                    description = "大胆、圆润、有表现力",
                    selected = settings.designStyle == DesignStyle.MD3E,
                    onClick = { viewModel.setDesignStyle(DesignStyle.MD3E) },
                    modifier = Modifier.weight(1f),
                )
                StyleOption(
                    style = DesignStyle.MIUIX,
                    title = "MiuiX",
                    description = "浅灰背景、白色卡片",
                    selected = settings.designStyle == DesignStyle.MIUIX,
                    onClick = { viewModel.setDesignStyle(DesignStyle.MIUIX) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (settings.designStyle == DesignStyle.MD3E) {
                SwitchRow(
                    title = "悬浮底栏",
                    subtitle = "Material3 Expressive 圆角悬浮导航，药丸指示器跟随图标",
                    checked = settings.floatingBottomBar,
                    onCheckedChange = viewModel::setFloatingBottomBar,
                )
                Spacer(Modifier.height(18.dp))
            }
            Text(
                text = "深色模式",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeModeOption.entries.forEach { mode ->
                    ChoicePill(
                        text = mode.label,
                        selected = settings.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            if (settings.designStyle == DesignStyle.MD3E) {
                Text(
                    text = "色彩风格",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Md3ColorGrid(settings = settings, onSelect = viewModel::setMd3ColorStyle)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "主题种子色",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                AccentRow(
                    selected = settings.accentArgb,
                    onSelect = viewModel::setAccentColor,
                    onCustom = { showAccentDialog = true },
                )
            } else {
                SwitchRow(
                    title = "Monet 动态取色",
                    subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        "从壁纸提取颜色，开启后覆盖强调色与调色盘"
                    } else {
                        "需要 Android 12 及以上，当前设备将使用强调色"
                    },
                    checked = settings.monetEnabled,
                    onCheckedChange = viewModel::setMonetEnabled,
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "强调色",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                AccentRow(
                    selected = settings.accentArgb,
                    onSelect = viewModel::setAccentColor,
                    onCustom = { showAccentDialog = true },
                    enabled = !settings.monetEnabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.S,
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "调色盘",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                MiuixPaletteGrid(settings = settings, onSelect = viewModel::setPalette)
                Spacer(Modifier.height(10.dp))
                SwitchRow(
                    title = "液态玻璃底栏",
                    subtitle = "使用背景模糊、折射与高光描边，Android 12 以下自动降级",
                    checked = settings.liquidGlass,
                    onCheckedChange = viewModel::setLiquidGlass,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.DarkMode,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "外观设置会立即生效并自动保存",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::resetAppearance) {
                    Text("恢复默认")
                }
            }
        }

        Spacer(Modifier.height(if (miuix) 14.dp else 24.dp))
        if (!miuix) {
            SectionHeader(title = "数据刷新")
            Spacer(Modifier.height(10.dp))
        }
        MifiCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
            SwitchRow(
                title = "自动刷新",
                subtitle = "前台每 2.8 秒读取一次设备状态；关闭后每 20 秒刷新",
                checked = settings.autoRefresh,
                onCheckedChange = viewModel::setAutoRefresh,
            )
            ClickableSettingRow(
                title = "立即刷新",
                subtitle = state.lastUpdated.takeIf { it > 0 }?.let { "上次更新 ${com.admi.mifi.util.formatClock(it)}" }
                    ?: "尚未成功读取",
                leading = { SettingIcon(Icons.Rounded.Refresh) },
                trailing = {
                    if (state.isRefreshing) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                onClick = viewModel::refresh,
            )
        }

        Spacer(Modifier.height(if (miuix) 14.dp else 24.dp))
        if (!miuix) {
            SectionHeader(title = "更多")
            Spacer(Modifier.height(10.dp))
        }
        MifiCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)) {
            ClickableSettingRow(
                title = "网络测速",
                subtitle = "跳转南京大学测速网 test.nju.edu.cn",
                leading = { SettingIcon(Icons.Rounded.Speed) },
                trailing = { ChevronIcon() },
                onClick = { onOpenUrl("https://test.nju.edu.cn/") },
            )
            ClickableSettingRow(
                title = "管理地址",
                subtitle = adminUrl(settings.host),
                leading = { SettingIcon(Icons.AutoMirrored.Rounded.OpenInNew) },
                trailing = { ChevronIcon() },
                onClick = { onOpenUrl(adminUrl(settings.host)) },
            )
            ClickableSettingRow(
                title = if (settings.protocol == DeviceProtocol.DEMO) "退出演示模式" else "体验演示模式",
                subtitle = if (settings.protocol == DeviceProtocol.DEMO) {
                    "重新使用真实设备数据"
                } else {
                    "无需硬件即可查看完整界面与动态数据"
                },
                leading = { SettingIcon(Icons.Rounded.Science) },
                trailing = { ChevronIcon() },
                onClick = {
                    if (settings.protocol == DeviceProtocol.DEMO) {
                        viewModel.useAutoDetect()
                        onMessage("已退出演示模式")
                    } else {
                        viewModel.enableDemo()
                        onMessage("已进入演示模式")
                    }
                },
            )
            ClickableSettingRow(
                title = "复制诊断信息",
                subtitle = "复制地址、协议与当前状态，便于排查",
                leading = { SettingIcon(Icons.Rounded.Info) },
                trailing = { ChevronIcon() },
                onClick = {
                    copyDiagnostics(context, state)
                    onMessage("诊断信息已复制")
                },
            )
        }

        Spacer(Modifier.height(if (miuix) 14.dp else 24.dp))
        if (!miuix) {
            SectionHeader(title = "关于")
            Spacer(Modifier.height(10.dp))
        }
        MifiCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary,
                                ),
                            ),
                            RoundedCornerShape(17.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Devices,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(27.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("随身 WiFi 管家", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "版本 ${BuildConfig.VERSION_NAME} · 支持 ASR / Goform",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MiniTag(text = protocolLabel(state))
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "所有数据直接在本机与随身 WiFi 之间传输，不上传云端，也不采集个人信息。演示模式仅用于界面预览。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = "MD3 Expressive × MiuiX · MaterialKolor · Haze",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }

    if (showConnectionDialog) {
        ConnectionDialog(
            settings = settings,
            testInProgress = state.testInProgress,
            testMessage = state.testMessage,
            onTest = viewModel::testConnection,
            onClearTest = viewModel::clearTestMessage,
            onSave = { host, password, protocol ->
                viewModel.saveConnection(host, password, protocol)
                showConnectionDialog = false
            },
            onDismiss = {
                viewModel.clearTestMessage()
                showConnectionDialog = false
            },
        )
    }

    if (showAccentDialog) {
        AccentHexDialog(
            current = settings.accentArgb,
            onConfirm = { argb ->
                viewModel.setAccentColor(argb)
                showAccentDialog = false
            },
            onDismiss = { showAccentDialog = false },
        )
    }
}

@Composable
private fun StyleOption(
    style: DesignStyle,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) scheme.primary.copy(alpha = 0.09f) else scheme.surfaceContainerHigh.copy(alpha = 0.62f),
            )
            .clickable(onClick = onClick)
            .padding(13.dp),
    ) {
        if (style == DesignStyle.MD3E) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf(scheme.primary, scheme.secondary, scheme.tertiary).forEachIndexed { index, color ->
                    Box(
                        Modifier
                            .size(if (index == 0) 22.dp else 16.dp)
                            .background(color, if (index == 1) CircleShape else RoundedCornerShape(8.dp))
                            .align(Alignment.Bottom),
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(scheme.background),
            ) {
                Column(Modifier.fillMaxWidth().padding(7.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(13.dp)
                            .background(Color(0xFFDFF6E5), RoundedCornerShape(4.dp)),
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .background(scheme.surfaceContainerLowest, RoundedCornerShape(3.dp)),
                    )
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp)
                        .width(36.dp)
                        .height(10.dp)
                        .background(scheme.surfaceContainerLowest, RoundedCornerShape(5.dp)),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(5.dp).background(scheme.primary, CircleShape))
                    Spacer(Modifier.width(3.dp))
                    Box(Modifier.size(3.dp).background(scheme.onSurfaceVariant, CircleShape))
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = scheme.primary,
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 2,
        )
    }
}

@Composable
private fun ChoicePill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun Md3ColorGrid(settings: AppSettings, onSelect: (Md3ColorStyle) -> Unit) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.4f
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Md3ColorStyle.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { style ->
                    val selected = settings.md3ColorStyle == style
                    val colors = remember(style, settings.accentArgb, dark) {
                        previewColors(style, settings.accentArgb, dark)
                    }
                    OptionTile(
                        title = style.label,
                        description = style.description,
                        colors = colors,
                        selected = selected,
                        onClick = { onSelect(style) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MiuixPaletteGrid(settings: AppSettings, onSelect: (MiuixPalette) -> Unit) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.4f
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MiuixPalette.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { palette ->
                    val selected = settings.palette == palette
                    val colors = remember(palette, settings.accentArgb, dark) {
                        previewPaletteColors(palette, settings.accentArgb, dark)
                    }
                    OptionTile(
                        title = palette.label,
                        description = palette.description,
                        colors = colors,
                        selected = selected,
                        onClick = { onSelect(palette) },
                        modifier = Modifier.weight(1f),
                        enabled = !settings.monetEnabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.S,
                    )
                }
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun OptionTile(
    title: String,
    description: String,
    colors: List<Color>,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (selected) scheme.secondaryContainer.copy(alpha = 0.6f) else scheme.surfaceContainerHighest.copy(alpha = 0.4f))
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) scheme.primary else scheme.outlineVariant.copy(alpha = 0.4f),
                shape = shape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            colors.take(3).forEach { color ->
                Box(Modifier.size(11.dp).background(color, CircleShape))
            }
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = scheme.primary,
            )
        }
    }
}

@Composable
private fun AccentRow(
    selected: Long,
    onSelect: (Long) -> Unit,
    onCustom: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        accentPresets.forEach { argb ->
            val isSelected = selected == argb
            val color = Color(argb.toInt())
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (isSelected) {
                            Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        } else {
                            Modifier
                        },
                    )
                    .clickable(enabled = enabled) { onSelect(argb) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                        tint = Color.White,
                    )
                }
            }
        }
        Surface(
            onClick = onCustom,
            enabled = enabled,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(34.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.ColorLens,
                    contentDescription = "自定义颜色",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.55f),
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun SettingIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    if (LocalDesignStyle.current == DesignStyle.MIUIX) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f),
            modifier = Modifier.size(22.dp),
        )
    } else {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun ChevronIcon() {
    Icon(
        Icons.Rounded.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
    )
}

@Composable
private fun ConnectionDialog(
    settings: AppSettings,
    testInProgress: Boolean,
    testMessage: String?,
    onTest: (String, String, DeviceProtocol) -> Unit,
    onClearTest: () -> Unit,
    onSave: (String, String, DeviceProtocol) -> Unit,
    onDismiss: () -> Unit,
) {
    var host by remember { mutableStateOf(settings.host) }
    var password by remember { mutableStateOf(settings.password) }
    var protocol by remember { mutableStateOf(settings.protocol) }
    var passwordVisible by remember { mutableStateOf(false) }
    var hostError by remember { mutableStateOf(false) }
    val effectiveHost = if (protocol == DeviceProtocol.DEMO && host.isBlank()) "192.168.0.1" else host.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("连接随身 WiFi") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 520.dp),
            ) {
                Text(
                    text = "输入设备管理页面的地址与登录密码。应用会直接访问局域网设备。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = host,
                    onValueChange = {
                        host = it
                        hostError = false
                    },
                    label = { Text("管理地址") },
                    placeholder = { Text("192.168.0.1") },
                    singleLine = true,
                    isError = hostError,
                    supportingText = if (hostError) {
                        { Text("请输入有效的管理地址") }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("管理密码") },
                    placeholder = { Text("设备登录密码，可为空") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "接口协议",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(7.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DeviceProtocol.entries.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { option ->
                                ProtocolOption(
                                    protocol = option,
                                    selected = protocol == option,
                                    onClick = {
                                        protocol = option
                                        onClearTest()
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { onTest(effectiveHost, password, protocol) },
                    enabled = !testInProgress && (protocol == DeviceProtocol.DEMO || host.isNotBlank()),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (testInProgress) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(7.dp))
                    } else {
                        Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(if (testInProgress) "正在连接…" else "测试连接")
                }
                if (testMessage != null) {
                    Spacer(Modifier.height(9.dp))
                    val failed = testMessage.startsWith("连接失败")
                    Text(
                        text = testMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
                if (protocol == DeviceProtocol.DEMO) {
                    Spacer(Modifier.height(9.dp))
                    Text(
                        text = "演示模式会显示一组模拟的 5G 设备数据，不需要真实硬件。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (protocol != DeviceProtocol.DEMO && host.isBlank()) {
                        hostError = true
                    } else {
                        onSave(effectiveHost.ifBlank { "192.168.0.1" }, password, protocol)
                    }
                },
            ) { Text("保存并连接") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun ProtocolOption(
    protocol: DeviceProtocol,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.36f)
                },
            )
            .border(
                width = if (selected) 1.4.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectableIndicator(selected = selected, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(7.dp))
        Text(
            text = protocol.label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AccentHexDialog(
    current: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var hex by remember { mutableStateOf(String.format("#%06X", current and 0xFFFFFF)) }
    var invalid by remember { mutableStateOf(false) }
    val parsed = parseHexColor(hex)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义强调色") },
        text = {
            Column {
                Text(
                    text = "输入 6 位十六进制颜色，例如 #3568F4。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = hex,
                    onValueChange = {
                        hex = it
                        invalid = false
                    },
                    label = { Text("颜色值") },
                    singleLine = true,
                    isError = invalid,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .background(
                                parsed?.let { Color(it.toInt()) } ?: MaterialTheme.colorScheme.surfaceContainerHighest,
                                CircleShape,
                            ),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = parsed?.let { String.format("#%06X", it and 0xFFFFFF) } ?: "无效颜色",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (parsed == null) invalid = true else onConfirm(parsed)
                },
            ) { Text("使用此颜色") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private fun parseHexColor(input: String): Long? {
    val clean = input.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
    if (clean.length != 6) return null
    val value = clean.toLongOrNull(16) ?: return null
    return 0xFF000000L or value
}

private fun protocolLabel(state: AppUiState): String =
    state.detectedProtocol?.label ?: state.settings.protocol.label

private fun adminUrl(host: String): String {
    val trimmed = host.trim()
    return when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        else -> "http://${trimmed.trimEnd('/')}/"
    }
}

private fun copyDiagnostics(context: android.content.Context, state: AppUiState) {
    val snapshot = state.snapshot
    val text = buildString {
        appendLine("随身 WiFi 管家诊断信息")
        appendLine("地址：${state.settings.host}")
        appendLine("协议：${protocolLabel(state)}")
        appendLine("密码：${if (state.settings.password.isBlank()) "无" else "已设置"}")
        appendLine("网络：${snapshot?.networkType ?: "—"}")
        appendLine("运营商：${snapshot?.carrier ?: "—"}")
        appendLine("信号：${snapshot?.signalDbm?.let { "$it dBm" } ?: "—"}")
        appendLine("WAN IP：${snapshot?.wanIp ?: "—"}")
        appendLine("最近错误：${state.error ?: "无"}")
        append("上次更新：${com.admi.mifi.util.formatClock(state.lastUpdated)}")
    }
    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("诊断信息", text))
}

private fun previewColors(style: Md3ColorStyle, accent: Long, dark: Boolean): List<Color> {
    val seed = Hct.fromInt(accent.toInt())
    val scheme = when (style) {
        Md3ColorStyle.TONAL_SPOT -> SchemeTonalSpot(seed, dark, 0.0)
        Md3ColorStyle.VIBRANT -> SchemeVibrant(seed, dark, 0.0)
        Md3ColorStyle.EXPRESSIVE -> SchemeExpressive(seed, dark, 0.0)
        Md3ColorStyle.NEUTRAL -> SchemeNeutral(seed, dark, 0.0)
        Md3ColorStyle.MONOCHROME -> SchemeMonochrome(seed, dark, 0.0)
        Md3ColorStyle.FIDELITY -> SchemeFidelity(seed, dark, 0.0)
    }
    return listOf(Color(scheme.primary), Color(scheme.secondary), Color(scheme.tertiary))
}

private fun previewPaletteColors(palette: MiuixPalette, accent: Long, dark: Boolean): List<Color> {
    val seed = Hct.fromInt(accent.toInt())
    val scheme = when (palette) {
        MiuixPalette.DEFAULT -> SchemeFidelity(seed, dark, 0.0)
        MiuixPalette.MORANDI -> SchemeNeutral(seed, dark, 0.0)
        MiuixPalette.MACARON -> SchemeFruitSalad(seed, dark, 0.0)
        MiuixPalette.CYBER -> SchemeRainbow(seed, dark, 0.1)
        MiuixPalette.INK -> SchemeMonochrome(seed, dark, 0.0)
    }
    return listOf(Color(scheme.primary), Color(scheme.secondary), Color(scheme.tertiary))
}
