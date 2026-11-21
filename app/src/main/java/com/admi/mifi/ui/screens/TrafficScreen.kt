package com.admi.mifi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.admi.mifi.ui.AppUiState
import com.admi.mifi.ui.components.DualSparkline
import com.admi.mifi.ui.components.MifiCard
import com.admi.mifi.ui.components.MiniTag
import com.admi.mifi.ui.components.ScreenHeader
import com.admi.mifi.ui.components.SectionHeader
import com.admi.mifi.ui.components.TrafficDonut
import com.admi.mifi.util.formatBytes
import com.admi.mifi.util.formatClock
import com.admi.mifi.util.formatSpeed
import java.time.YearMonth

@Composable
fun TrafficScreen(
    state: AppUiState,
    onRefresh: () -> Unit,
) {
    val snapshot = state.snapshot
    val monthlyDown = snapshot?.monthlyRxBytes ?: 0L
    val monthlyUp = snapshot?.monthlyTxBytes ?: 0L
    val sessionDown = snapshot?.sessionRxBytes ?: 0L
    val sessionUp = snapshot?.sessionTxBytes ?: 0L
    val monthLabel = YearMonth.now().let { "${it.year} 年 ${it.monthValue} 月" }
    val monthText = formatBytes(snapshot?.monthlyTotalBytes ?: 0L)
    val sessionText = formatBytes(snapshot?.sessionTotalBytes ?: 0L)
    val totalRx = snapshot?.totalRxBytes
    val totalTx = snapshot?.totalTxBytes
    val lifetime = if (totalRx == null && totalTx == null) null else (totalRx ?: 0L) + (totalTx ?: 0L)
    val averageDown = if (state.speed.downHistory.isEmpty()) 0L else state.speed.downHistory.average().toLong()
    val averageUp = if (state.speed.upHistory.isEmpty()) 0L else state.speed.upHistory.average().toLong()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(top = 18.dp, bottom = 130.dp),
    ) {
        ScreenHeader(
            title = "流量",
            subtitle = "更新于 ${formatClock(state.lastUpdated)} · 实时统计",
            actions = {
                IconButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                    if (state.isRefreshing) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新")
                    }
                }
            },
        )
        Spacer(Modifier.height(14.dp))
        MifiCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrafficDonut(
                    downBytes = monthlyDown,
                    upBytes = monthlyUp,
                    centerLabel = "本月总量",
                    centerValue = monthText,
                    modifier = Modifier.size(150.dp),
                )
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = monthLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = monthText,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (state.monthlyIsLocal) {
                        Spacer(Modifier.height(8.dp))
                        MiniTag(
                            text = "应用本地统计",
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    BreakdownRow(
                        color = MaterialTheme.colorScheme.primary,
                        label = "下载",
                        value = formatBytes(monthlyDown),
                    )
                    Spacer(Modifier.height(5.dp))
                    BreakdownRow(
                        color = MaterialTheme.colorScheme.tertiary,
                        label = "上传",
                        value = formatBytes(monthlyUp),
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                icon = Icons.Rounded.DataUsage,
                label = "本次使用",
                value = sessionText,
                hint = if (state.sessionIsLocal) "本地累计" else "设备统计",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                icon = Icons.Rounded.Schedule,
                label = "累计使用",
                value = formatBytes(lifetime),
                hint = "设备生命周期",
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(14.dp))
        MifiCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.DataUsage,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(7.dp))
                Text("流量构成", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                MiniTag(
                    text = if (state.monthlyIsLocal) "本地观测" else "设备上报",
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            UsageBar(
                label = "本月下载",
                value = monthlyDown,
                total = (monthlyDown + monthlyUp).coerceAtLeast(1),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(13.dp))
            UsageBar(
                label = "本月上传",
                value = monthlyUp,
                total = (monthlyDown + monthlyUp).coerceAtLeast(1),
                color = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.height(13.dp))
            UsageBar(
                label = "本次下载",
                value = sessionDown,
                total = (sessionDown + sessionUp).coerceAtLeast(1),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
            )
            Spacer(Modifier.height(13.dp))
            UsageBar(
                label = "本次上传",
                value = sessionUp,
                total = (sessionDown + sessionUp).coerceAtLeast(1),
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.72f),
            )
        }
        Spacer(Modifier.height(24.dp))
        SectionHeader(title = "实时趋势", subtitle = "最近 ${com.admi.mifi.data.SpeedState.HISTORY_SIZE} 次采样的上传与下载速度")
        Spacer(Modifier.height(10.dp))
        MifiCard {
            Row {
                TrendValue(
                    label = "当前下载",
                    value = formatSpeed(state.speed.downBps),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                TrendValue(
                    label = "当前上传",
                    value = formatSpeed(state.speed.upBps),
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(18.dp))
            DualSparkline(
                downValues = state.speed.downHistory,
                upValues = state.speed.upHistory,
                downColor = MaterialTheme.colorScheme.primary,
                upColor = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.fillMaxWidth().height(120.dp),
            )
            Spacer(Modifier.height(14.dp))
            Row {
                TrendValue(
                    label = "平均下载",
                    value = formatSpeed(averageDown),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TrendValue(
                    label = "平均上传",
                    value = formatSpeed(averageUp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        MifiCard(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.38f)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Rounded.Info,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text = "当月流量优先读取设备统计；如果设备不提供，则使用应用在本次安装期间观测到的数据，并标注为本地统计。换月后本地统计会自动归零。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.9f),
                )
            }
        }
    }
}

@Composable
private fun MetricCard(
    icon: ImageVector,
    label: String,
    value: String,
    hint: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    MifiCard(modifier = modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        Box(
            modifier = Modifier.size(34.dp).background(tint.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(11.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = hint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BreakdownRow(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun UsageBar(
    label: String,
    value: Long,
    total: Long,
    color: Color,
) {
    val fraction = (value.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    Column {
        Row {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatBytes(value, short = true),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(7.dp).clipBar(),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }
}

private fun Modifier.clipBar(): Modifier = this.clip(RoundedCornerShape(50))

@Composable
private fun TrendValue(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
        )
    }
}
