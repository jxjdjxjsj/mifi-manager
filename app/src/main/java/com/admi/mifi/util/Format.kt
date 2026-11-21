package com.admi.mifi.util

import java.util.Locale

fun formatBytes(bytes: Long?, short: Boolean = false): String {
    if (bytes == null) return "—"
    if (bytes < 0) return "—"
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB", "PB")
    var value = bytes.toDouble() / 1024.0
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    val decimals = when {
        short -> if (value >= 100) 0 else 1
        value >= 100 -> 0
        value >= 10 -> 1
        else -> 2
    }
    var number = String.format(Locale.CHINA, "%.${decimals}f", value)
    if (number.contains('.')) {
        number = number.trimEnd('0').trimEnd('.')
    }
    return "$number ${units[index]}"
}

fun formatSpeed(bytesPerSecond: Long?): String {
    if (bytesPerSecond == null || bytesPerSecond < 0) return "—"
    if (bytesPerSecond < 1024) return "$bytesPerSecond B/s"
    return "${formatBytes(bytesPerSecond, short = true)}/s"
}

fun formatSignal(dbm: Int?): String {
    if (dbm == null) return "—"
    return "$dbm dBm"
}

fun formatUptime(totalSeconds: Long?): String {
    if (totalSeconds == null || totalSeconds <= 0) return "—"
    val days = totalSeconds / 86400
    val hours = (totalSeconds % 86400) / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        days > 0 -> "${days}天 ${hours}时"
        hours > 0 -> "${hours}时 ${minutes}分"
        minutes > 0 -> "${minutes}分"
        else -> "${totalSeconds}秒"
    }
}

fun formatClock(timestamp: Long): String {
    if (timestamp <= 0L) return "—"
    return java.text.SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(java.util.Date(timestamp))
}

fun formatPercent(value: Int?): String = if (value == null) "—" else "$value%"

fun formatBatteryState(percent: Int?, charging: Boolean?): String = when {
    percent == null -> "—"
    charging == true -> "$percent% · 充电中"
    else -> "$percent%"
}
