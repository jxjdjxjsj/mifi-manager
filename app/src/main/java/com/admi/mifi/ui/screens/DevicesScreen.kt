package com.admi.mifi.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.admi.mifi.data.DeviceSnapshot
import com.admi.mifi.data.WifiClient
import com.admi.mifi.ui.AppUiState
import com.admi.mifi.ui.components.EmptyState
import com.admi.mifi.ui.components.MifiCard
import com.admi.mifi.ui.components.MiniTag
import com.admi.mifi.ui.components.ScreenHeader
import com.admi.mifi.ui.components.SignalBars
import com.admi.mifi.ui.components.StatusDot
import com.admi.mifi.util.formatBytes
import com.admi.mifi.util.formatClock

@Composable
fun DevicesScreen(
    state: AppUiState,
    onRefresh: () -> Unit,
    onOpenAdmin: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val snapshot = state.snapshot
    val clients = remember(snapshot?.clients) {
        snapshot?.clients.orEmpty().sortedWith(
            compareByDescending<WifiClient> { it.connected }.thenBy { it.displayName.lowercase() },
        )
    }
    val online = clients.count { it.connected }
    val displayCount = snapshot?.displayConnectedCount ?: online
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                title = "设备",
                subtitle = if (snapshot == null) {
                    "尚未读取到设备列表"
                } else if (clients.isEmpty() && displayCount > 0) {
                    "$displayCount 台在线 · 设备未提供客户端明细"
                } else {
                    "$displayCount 台在线 · 共 ${clients.size} 台曾连接"
                },
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
        }
        item {
            WifiNetworkCard(
                snapshot = snapshot,
                onOpenAdmin = onOpenAdmin,
                onCopy = { label, value ->
                    copyText(context, label, value)
                    onMessage("已复制$label")
                },
            )
        }
        if (clients.isEmpty()) {
            item {
                MifiCard(contentPadding = PaddingValues(0.dp)) {
                    EmptyState(
                        icon = if (snapshot == null) Icons.Rounded.WifiOff else Icons.Rounded.Devices,
                        title = when {
                            snapshot == null -> "还没有连接设备"
                            displayCount > 0 -> "已连接 $displayCount 台设备"
                            else -> "暂无已连接设备"
                        },
                        description = when {
                            snapshot == null ->
                                "请先在设置中填写管理地址和密码，然后刷新。也可以先体验演示模式。"
                            displayCount > 0 ->
                                "当前固件只上报了在线设备数量，没有提供客户端名称、MAC 和 IP 明细。连接状态仍会随刷新更新。"
                            else ->
                                "当前 WiFi 没有活跃的客户端。连接手机或电脑后下拉刷新。"
                        },
                        action = {
                            Button(onClick = onRefresh) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("刷新列表")
                            }
                        },
                    )
                }
            }
        } else {
            items(clients) { client ->
                ClientCard(client)
            }
        }
        item {
            Text(
                text = "设备列表来自随身 WiFi 的 DHCP / 客户端管理接口，可能存在数秒延迟。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun WifiNetworkCard(
    snapshot: DeviceSnapshot?,
    onOpenAdmin: () -> Unit,
    onCopy: (String, String) -> Unit,
) {
    var showPassword by remember { mutableStateOf(false) }
    val password = snapshot?.wifiPassword
    val online = snapshot?.displayConnectedCount ?: 0
    MifiCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Wifi,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(25.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = snapshot?.wifiSsid ?: "未读取到 WiFi 名称",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(active = snapshot != null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (snapshot != null) "随身 WiFi 热点" else "等待连接",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            MiniTag(text = "$online 台在线")
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                    RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "WiFi 密码",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = when {
                        password.isNullOrBlank() -> "设备未提供"
                        showPassword -> password
                        else -> "•".repeat(password.length.coerceIn(6, 16))
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (!password.isNullOrBlank()) {
                IconButton(onClick = { showPassword = !showPassword }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (showPassword) "隐藏密码" else "显示密码",
                        modifier = Modifier.size(19.dp),
                    )
                }
                TextButton(
                    onClick = { onCopy("WiFi 密码", password) },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text("复制", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Router,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "更新于 ${formatClock(snapshot?.timestamp ?: 0L)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenAdmin) {
                Text("管理 WiFi", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun ClientCard(client: WifiClient) {
    val scheme = MaterialTheme.colorScheme
    val avatarColor = remember(client.mac ?: client.ip) {
        val palette = listOf(
            Color(0xFF4F7DF3),
            Color(0xFF13A598),
            Color(0xFFE0833A),
            Color(0xFFB25BD3),
            Color(0xFFD94B6B),
            Color(0xFF4B9E52),
        )
        palette[(client.mac ?: client.ip ?: client.displayName).hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) } % palette.size]
    }
    MifiCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).background(avatarColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = client.displayName.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    color = avatarColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = client.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = buildString {
                        append(client.displayIp)
                        client.mac?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            MiniTag(
                text = if (client.connected) "在线" else "离线",
                containerColor = if (client.connected) {
                    scheme.tertiaryContainer.copy(alpha = 0.7f)
                } else {
                    scheme.surfaceContainerHighest
                },
                contentColor = if (client.connected) scheme.onTertiaryContainer else scheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (client.signalLevel != null) {
                SignalBars(level = client.signalLevel, dbm = null, modifier = Modifier.height(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "信号 ${client.signalLevel}/4",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            } else {
                Icon(
                    Icons.Rounded.Wifi,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = if (client.connected) "已连接" else "已断开",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            ClientTraffic(Icons.Rounded.ArrowDownward, client.rxBytes, scheme.primary)
            Spacer(Modifier.width(12.dp))
            ClientTraffic(Icons.Rounded.ArrowUpward, client.txBytes, scheme.tertiary)
        }
    }
}

@Composable
private fun ClientTraffic(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    bytes: Long?,
    color: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp), tint = color)
        Spacer(Modifier.width(3.dp))
        Text(
            text = formatBytes(bytes, short = true),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun copyText(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
}
