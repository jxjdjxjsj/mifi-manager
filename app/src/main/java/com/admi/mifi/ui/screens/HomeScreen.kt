package com.admi.mifi.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.admi.mifi.data.DesignStyle
import com.admi.mifi.data.DeviceAction
import com.admi.mifi.data.DeviceProtocol
import com.admi.mifi.data.DeviceSnapshot
import com.admi.mifi.ui.AppUiState
import com.admi.mifi.ui.MainTab
import com.admi.mifi.ui.components.ClickableSettingRow
import com.admi.mifi.ui.components.DualSparkline
import com.admi.mifi.ui.components.InfoRow
import com.admi.mifi.ui.components.MifiCard
import com.admi.mifi.ui.components.MiniTag
import com.admi.mifi.ui.components.QuickAction
import com.admi.mifi.ui.components.ScreenHeader
import com.admi.mifi.ui.components.SectionHeader
import com.admi.mifi.ui.components.SignalBars
import com.admi.mifi.ui.components.StatusDot
import com.admi.mifi.ui.components.TrafficDonut
import com.admi.mifi.ui.theme.LocalDesignStyle
import com.admi.mifi.util.formatBatteryState
import com.admi.mifi.util.formatBytes
import com.admi.mifi.util.formatClock
import com.admi.mifi.util.formatSignal
import com.admi.mifi.util.formatSpeed
import com.admi.mifi.util.formatUptime

@Composable
fun HomeScreen(
    state: AppUiState,
    onRefresh: () -> Unit,
    onAction: (DeviceAction) -> Unit,
    onNavigate: (MainTab) -> Unit,
    onEnableDemo: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    val snapshot = state.snapshot
    val context = LocalContext.current
    val refreshState = rememberScrollState()
    val miuix = LocalDesignStyle.current == DesignStyle.MIUIX
    val subtitle = when {
        state.isLoading && snapshot == null -> "正在读取设备信息…"
        snapshot == null -> "尚未连接设备"
        else -> "更新于 ${formatClock(state.lastUpdated)} · ${state.detectedProtocol?.label ?: state.settings.protocol.label}"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(refreshState)
            .padding(horizontal = 22.dp)
            .padding(top = 18.dp, bottom = 130.dp),
    ) {
        ScreenHeader(
            title = "随身 WiFi",
            subtitle = subtitle,
            actions = {
                IconButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                    if (state.isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新")
                    }
                }
            },
        )
        if (state.isRefreshing && snapshot != null) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            )
        }
        Spacer(Modifier.height(14.dp))
        HeroCard(snapshot = snapshot, state = state)
        if (state.error != null) {
            Spacer(Modifier.height(12.dp))
            ErrorCard(
                message = state.error,
                isDemo = state.settings.protocol == DeviceProtocol.DEMO,
                onRetry = onRefresh,
                onEnableDemo = onEnableDemo,
            )
        }
        Spacer(Modifier.height(12.dp))
        MifiCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                QuickAction(
                    icon = Icons.Rounded.Refresh,
                    label = "刷新",
                    onClick = onRefresh,
                    enabled = !state.isRefreshing,
                    modifier = Modifier.weight(1f),
                )
                QuickAction(
                    icon = Icons.Rounded.RestartAlt,
                    label = "重启",
                    onClick = { onAction(DeviceAction.REBOOT) },
                    enabled = state.actionInProgress == null && snapshot != null,
                    modifier = Modifier.weight(1f),
                )
                QuickAction(
                    icon = Icons.Rounded.PowerSettingsNew,
                    label = "关机",
                    onClick = { onAction(DeviceAction.SHUTDOWN) },
                    enabled = state.actionInProgress == null && snapshot != null,
                    modifier = Modifier.weight(1f),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(Modifier.height(if (miuix) 14.dp else 24.dp))
        if (!miuix) {
            SectionHeader(title = "实时速度", subtitle = "根据设备采样自动计算，每 2.8 秒刷新")
            Spacer(Modifier.height(10.dp))
        }
        SpeedCard(state)
        Spacer(Modifier.height(if (miuix) 14.dp else 24.dp))
        if (!miuix) {
            SectionHeader(
                title = "流量",
                subtitle = if (state.monthlyIsLocal) "设备未提供当月流量，当前为应用本地统计" else "来自设备的流量统计",
                trailing = {
                    TextButton(onClick = { onNavigate(MainTab.TRAFFIC) }) {
                        Text("详情")
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                },
            )
            Spacer(Modifier.height(10.dp))
        }
        TrafficSummaryCard(
            state = state,
            showHeader = miuix,
            onDetails = { onNavigate(MainTab.TRAFFIC) },
        )
        Spacer(Modifier.height(if (miuix) 14.dp else 24.dp))
        if (!miuix) {
            SectionHeader(title = "设备信息", subtitle = "网卡、SIM 卡与系统版本")
            Spacer(Modifier.height(10.dp))
        }
        DeviceInfoCard(
            snapshot = snapshot,
            onCopy = { label, value ->
                copyText(context, label, value)
                onMessage("已复制$label")
            },
        )
        Spacer(Modifier.height(if (miuix) 14.dp else 24.dp))
        if (!miuix) {
            SectionHeader(title = "更多设置")
            Spacer(Modifier.height(10.dp))
        }
        MifiCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
            ClickableSettingRow(
                title = "网络测速",
                subtitle = "打开南京大学测速网 test.nju.edu.cn",
                leading = { LeadingIcon(Icons.Rounded.Speed) },
                trailing = { Chevron() },
                onClick = { onOpenUrl("https://test.nju.edu.cn/") },
            )
            ClickableSettingRow(
                title = "管理后台",
                subtitle = adminUrl(state.resolvedAdminUrl ?: state.settings.host),
                leading = { LeadingIcon(Icons.Rounded.Router) },
                trailing = { Chevron() },
                onClick = { onOpenUrl(adminUrl(state.resolvedAdminUrl ?: state.settings.host)) },
            )
            ClickableSettingRow(
                title = "已连接设备",
                subtitle = "${snapshot?.displayConnectedCount ?: 0} 台设备在线",
                leading = { LeadingIcon(Icons.Rounded.Devices) },
                trailing = { Chevron() },
                onClick = { onNavigate(MainTab.DEVICES) },
            )
            ClickableSettingRow(
                title = "全部设置",
                subtitle = "外观、连接与刷新选项",
                leading = { LeadingIcon(Icons.Rounded.Info) },
                trailing = { Chevron() },
                onClick = { onNavigate(MainTab.SETTINGS) },
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = "数据直接读取随身 WiFi 的管理页面，不经过云端。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun HeroCard(snapshot: DeviceSnapshot?, state: AppUiState) {
    if (LocalDesignStyle.current == DesignStyle.MIUIX) {
        MiuixHeroCard(snapshot = snapshot, state = state)
    } else {
        Md3HeroCard(snapshot = snapshot, state = state)
    }
}

/** MD3E status card — intentionally unchanged from the previous layout. */
@Composable
private fun Md3HeroCard(snapshot: DeviceSnapshot?, state: AppUiState) {
    val scheme = MaterialTheme.colorScheme
    val connected = snapshot?.wanConnected == true
    val container = if (connected) scheme.primaryContainer else scheme.errorContainer
    val content = if (connected) scheme.onPrimaryContainer else scheme.onErrorContainer
    val accent = if (connected) scheme.primary else scheme.error
    val title = when {
        snapshot == null -> "等待连接"
        connected -> "网络已连接"
        else -> "网络未连接"
    }
    val detail = when {
        snapshot == null && state.isLoading -> "正在读取设备信息…"
        snapshot == null -> "请先在设置中填写管理地址"
        else -> listOfNotNull(
            snapshot.networkType?.takeIf { it.isNotBlank() },
            snapshot.band?.takeIf { it.isNotBlank() },
        ).joinToString(" · ").ifBlank { "随身 WiFi 热点" }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = container,
        contentColor = content,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(active = connected, color = accent)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = content,
                )
                Spacer(Modifier.weight(1f))
                if (snapshot != null) {
                    Icon(
                        Icons.Rounded.Devices,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = content.copy(alpha = 0.56f),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${snapshot.displayConnectedCount} 台",
                        style = MaterialTheme.typography.labelMedium,
                        color = content.copy(alpha = 0.62f),
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = content.copy(alpha = 0.68f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (snapshot != null) {
                Spacer(Modifier.height(12.dp))
                HeroInfoRow(
                    icon = Icons.Rounded.SimCard,
                    label = "运营商",
                    value = snapshot.carrier?.takeIf { it.isNotBlank() } ?: "—",
                    contentColor = content,
                )
                HeroInfoRow(
                    icon = Icons.Rounded.SignalCellularAlt,
                    label = "信号强度",
                    contentColor = content,
                ) {
                    SignalBars(
                        level = snapshot.signalLevel ?: 0,
                        dbm = snapshot.signalDbm,
                        activeColor = content.copy(alpha = 0.88f),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = formatSignal(snapshot.signalDbm),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = content,
                    )
                }
                HeroInfoRow(
                    icon = Icons.Rounded.BatteryChargingFull,
                    label = "电量",
                    value = formatBatteryState(snapshot.batteryPercent, snapshot.charging),
                    contentColor = content,
                )
            }
        }
    }
}

/**
 * MiuiX status card following the SukiSU reference: tonal green card, small
 * metadata tags, a large status title and a clipped check mark that appears
 * once the device has been detected.
 */
@Composable
private fun MiuixHeroCard(snapshot: DeviceSnapshot?, state: AppUiState) {
    val scheme = MaterialTheme.colorScheme
    val connected = snapshot?.wanConnected == true
    val dark = scheme.surface.luminance() < 0.4f
    val container = when {
        connected -> if (dark) Color(0xFF17331F) else Color(0xFFDFFAE4)
        snapshot != null -> if (dark) Color(0xFF3B2223) else scheme.errorContainer
        else -> scheme.surfaceContainerLowest
    }
    val content = if (snapshot != null && !connected) scheme.onErrorContainer else scheme.onSurface
    val accent = when {
        connected -> if (dark) Color(0xFF4ADE80) else Color(0xFF1FB95C)
        snapshot != null -> scheme.error
        else -> scheme.primary
    }
    val title = when {
        snapshot == null -> "等待连接"
        connected -> "网络已连接"
        else -> "网络未连接"
    }
    val subtitle = when {
        snapshot == null && state.isLoading -> "正在读取设备信息…"
        snapshot == null -> "请先在设置中填写管理地址"
        else -> "${snapshot.displayConnectedCount} 台设备在线 · 更新于 ${formatClock(state.lastUpdated)}"
    }
    val tags = buildList {
        snapshot?.networkType?.takeIf { it.isNotBlank() }?.let {
            add(Triple(it, accent, if (dark) Color(0xFF0B1F12) else Color.White))
        }
        snapshot?.carrier?.takeIf { it.isNotBlank() }?.let {
            add(Triple(it, content.copy(alpha = 0.08f), content.copy(alpha = 0.62f)))
        }
        snapshot?.band?.takeIf { it.isNotBlank() }?.let {
            add(Triple(it, scheme.error.copy(alpha = 0.14f), scheme.error))
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = container,
            contentColor = content,
            tonalElevation = 0.dp,
        ) {
            Box(Modifier.fillMaxWidth().height(200.dp)) {
                if (snapshot != null) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircleOutline,
                        contentDescription = "设备已检测",
                        tint = accent,
                        modifier = Modifier
                            .size(128.dp)
                            .align(Alignment.BottomEnd)
                            .offset(x = 26.dp, y = 24.dp),
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tags.forEach { (text, tagContainer, tagContent) ->
                            MiuixTag(
                                text = text,
                                containerColor = tagContainer,
                                contentColor = tagContent,
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = title,
                        fontSize = 30.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = content,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = content.copy(alpha = 0.58f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(0.60f),
                    )
                }
            }
        }
        if (snapshot != null) {
            MifiCard(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 18.dp,
                    vertical = 6.dp,
                ),
            ) {
                HeroInfoRow(
                    icon = Icons.Rounded.SimCard,
                    label = "运营商",
                    value = snapshot.carrier?.takeIf { it.isNotBlank() } ?: "—",
                    contentColor = scheme.onSurface,
                )
                HeroInfoRow(
                    icon = Icons.Rounded.SignalCellularAlt,
                    label = "信号强度",
                    contentColor = scheme.onSurface,
                ) {
                    SignalBars(
                        level = snapshot.signalLevel ?: 0,
                        dbm = snapshot.signalDbm,
                        activeColor = scheme.primary,
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = formatSignal(snapshot.signalDbm),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = scheme.onSurface,
                    )
                }
                HeroInfoRow(
                    icon = Icons.Rounded.BatteryChargingFull,
                    label = "电量",
                    value = formatBatteryState(snapshot.batteryPercent, snapshot.charging),
                    contentColor = scheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun MiuixTag(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

/** Template-style info row: leading icon, small label and a larger value. */
@Composable
private fun HeroInfoRow(
    icon: ImageVector,
    label: String,
    contentColor: Color,
    value: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = contentColor.copy(alpha = 0.60f),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.60f),
            )
            Spacer(Modifier.height(2.dp))
            if (trailing != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    trailing()
                }
            } else {
                Text(
                    text = value ?: "—",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    isDemo: Boolean,
    onRetry: () -> Unit,
    onEnableDemo: () -> Unit,
) {
    MifiCard(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "无法读取设备",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onRetry) { Text("重试") }
            if (!isDemo) {
                Button(onClick = onEnableDemo) { Text("体验演示模式") }
            }
        }
    }
}

@Composable
private fun SpeedCard(state: AppUiState) {
    val down = state.speed.downBps
    val up = state.speed.upBps
    val miuix = LocalDesignStyle.current == DesignStyle.MIUIX
    MifiCard {
        if (miuix) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "实时速度",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "自动刷新",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(14.dp))
        }
        Row {
            SpeedValue(
                icon = Icons.Rounded.ArrowDownward,
                label = "下载",
                value = formatSpeed(down),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            SpeedValue(
                icon = Icons.Rounded.ArrowUpward,
                label = "上传",
                value = formatSpeed(up),
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(16.dp))
        DualSparkline(
            downValues = state.speed.downHistory,
            upValues = state.speed.upHistory,
            downColor = MaterialTheme.colorScheme.primary,
            upColor = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.fillMaxWidth().height(84.dp),
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendDot(MaterialTheme.colorScheme.primary, "下载峰值 ${formatSpeed(state.speed.peakDownBps)}")
            Spacer(Modifier.width(16.dp))
            LegendDot(MaterialTheme.colorScheme.tertiary, "上传峰值 ${formatSpeed(state.speed.peakUpBps)}")
        }
    }
}

@Composable
private fun SpeedValue(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(26.dp).background(color.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(7.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TrafficSummaryCard(
    state: AppUiState,
    showHeader: Boolean,
    onDetails: () -> Unit,
) {
    val snapshot = state.snapshot
    val down = snapshot?.monthlyRxBytes ?: 0L
    val up = snapshot?.monthlyTxBytes ?: 0L
    MifiCard {
        if (showHeader) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "流量",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (state.monthlyIsLocal) "本地统计" else "设备上报",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDetails) {
                    Text("详情")
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TrafficDonut(
                downBytes = down,
                upBytes = up,
                centerLabel = "当月",
                centerValue = formatBytes(snapshot?.monthlyTotalBytes, short = true),
                modifier = Modifier.size(122.dp),
            )
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "本次使用",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = formatBytes(snapshot?.sessionTotalBytes),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (state.sessionIsLocal) {
                    Spacer(Modifier.height(5.dp))
                    MiniTag(text = "本地统计", containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f))
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    SmallLegend("下载", formatBytes(down, short = true), MaterialTheme.colorScheme.primary)
                    SmallLegend("上传", formatBytes(up, short = true), MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
}

@Composable
private fun SmallLegend(label: String, value: String, color: Color) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(color, CircleShape))
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun DeviceInfoCard(snapshot: DeviceSnapshot?, onCopy: (String, String) -> Unit) {
    var passwordVisible by remember { mutableStateOf(false) }
    var detailsExpanded by remember { mutableStateOf(false) }
    val wifiPassword = snapshot?.wifiPassword
    val actionStart = if (LocalDesignStyle.current == DesignStyle.MIUIX) 36.dp else 49.dp
    MifiCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 12.dp)) {
        InfoRow(
            icon = Icons.Rounded.Wifi,
            label = "WiFi 名称",
            value = snapshot?.wifiSsid ?: "—",
            onCopy = snapshot?.wifiSsid?.let { value -> { onCopy("WiFi 名称", value) } },
        )
        InfoRow(
            icon = Icons.Rounded.NetworkCheck,
            label = "WiFi 密码",
            value = when {
                wifiPassword == null -> "—"
                passwordVisible -> wifiPassword
                else -> "•".repeat(wifiPassword.length.coerceIn(6, 16))
            },
            onCopy = wifiPassword?.let { value -> { onCopy("WiFi 密码", value) } },
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = actionStart, bottom = 4.dp),
        ) {
            TextButton(
                onClick = { passwordVisible = !passwordVisible },
                enabled = wifiPassword != null,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
            ) {
                Icon(
                    if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(if (passwordVisible) "隐藏密码" else "显示密码", style = MaterialTheme.typography.labelMedium)
            }
            if (wifiPassword != null) {
                TextButton(
                    onClick = { onCopy("WiFi 密码", wifiPassword) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                ) {
                    Text("复制密码", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        InfoRow(
            icon = Icons.Rounded.Public,
            label = "WAN IP",
            value = snapshot?.wanIp ?: "—",
            onCopy = snapshot?.wanIp?.let { value -> { onCopy("WAN IP", value) } },
        )
        InfoRow(
            icon = Icons.Rounded.SignalCellularAlt,
            label = "网络连接",
            value = when {
                snapshot == null -> "—"
                snapshot.wanConnected -> "已连接"
                else -> "未连接"
            },
            accent = if (snapshot?.wanConnected == true) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
        )
        InfoRow(
            icon = Icons.Rounded.WifiTethering,
            label = "已连接设备",
            value = "${snapshot?.displayConnectedCount ?: 0} 台",
        )
        if (detailsExpanded) {
            InfoRow(
                icon = Icons.Rounded.SimCard,
                label = "ICCID",
                value = snapshot?.iccid ?: "—",
                onCopy = snapshot?.iccid?.let { value -> { onCopy("ICCID", value) } },
            )
            InfoRow(
                icon = Icons.Rounded.Fingerprint,
                label = "IMEI",
                value = snapshot?.imei ?: "—",
                onCopy = snapshot?.imei?.let { value -> { onCopy("IMEI", value) } },
            )
            InfoRow(
                icon = Icons.Rounded.Memory,
                label = "硬件版本",
                value = snapshot?.hardwareVersion ?: "—",
            )
            InfoRow(
                icon = Icons.Rounded.SystemUpdate,
                label = "软件版本",
                value = snapshot?.softwareVersion ?: "—",
            )
            InfoRow(
                icon = Icons.Rounded.Router,
                label = "设备型号",
                value = snapshot?.model ?: "—",
            )
            InfoRow(
                icon = Icons.Rounded.Timer,
                label = "运行时长",
                value = formatUptime(snapshot?.uptimeSeconds),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            TextButton(onClick = { detailsExpanded = !detailsExpanded }) {
                Text(if (detailsExpanded) "收起设备信息" else "展开全部设备信息")
                Icon(
                    if (detailsExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

@Composable
private fun LeadingIcon(icon: ImageVector) {
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
private fun Chevron() {
    Icon(
        Icons.Rounded.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
    )
}

private fun copyText(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
}

private fun adminUrl(host: String): String {
    val trimmed = host.trim()
    return when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        else -> "http://${trimmed.trimEnd('/')}/"
    }
}
