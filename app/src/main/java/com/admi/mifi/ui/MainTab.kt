package com.admi.mifi.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class MainTab(
    val label: String,
    val icon: ImageVector,
) {
    HOME("首页", Icons.Rounded.Home),
    DEVICES("设备", Icons.Rounded.Devices),
    TRAFFIC("流量", Icons.Rounded.DataUsage),
    SETTINGS("设置", Icons.Rounded.Settings),
}
