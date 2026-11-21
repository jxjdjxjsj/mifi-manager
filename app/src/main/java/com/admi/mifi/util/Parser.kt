package com.admi.mifi.util

import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.random.Random

fun cleanValue(value: String?): String? {
    val text = value?.trim() ?: return null
    if (text.isEmpty()) return null
    val lower = text.lowercase(Locale.ROOT)
    if (lower in setOf("0", "null", "nil", "none", "unknown", "n/a", "na", "--", "-", "undefined")) {
        return null
    }
    return text
}

fun firstNumber(value: String?): Double? {
    val match = Regex("-?\\d+(?:\\.\\d+)?").find(value ?: return null) ?: return null
    return match.value.toDoubleOrNull()
}

fun asLong(value: String?): Long? {
    val text = value?.trim() ?: return null
    if (text.isEmpty()) return null
    val lower = text.lowercase(Locale.ROOT)
    if (lower in setOf("null", "nil", "none", "unknown", "n/a", "na", "--", "-", "undefined")) {
        return null
    }
    val direct = text.toLongOrNull()
    if (direct != null) return direct
    return firstNumber(text)?.toLong()
}

fun asInt(value: String?): Int? = asLong(value)?.let {
    if (it > Int.MAX_VALUE) null else it.toInt()
}

fun asBoolean(value: String?): Boolean? {
    val text = value?.trim()?.lowercase(Locale.ROOT) ?: return null
    if (text.isEmpty()) return null
    return when (text) {
        "1", "true", "yes", "on", "enable", "enabled", "connected", "connect", "charging", "open" -> true
        "0", "false", "no", "off", "disable", "disabled", "disconnected", "disconnect", "close" -> false
        else -> null
    }
}

fun carrierFromIccid(rawValue: String?): String? {
    val raw = cleanValue(rawValue) ?: return null
    val digits = raw.filter { it.isDigit() }
    return when {
        digits.startsWith("898600") || digits.startsWith("898602") ||
            digits.startsWith("898604") || digits.startsWith("898607") -> "中国移动"
        digits.startsWith("898601") || digits.startsWith("898606") ||
            digits.startsWith("898609") -> "中国联通"
        digits.startsWith("898603") || digits.startsWith("898611") -> "中国电信"
        digits.startsWith("898615") -> "中国广电"
        else -> null
    }
}

fun mapCarrier(rawValue: String?): String? {
    val raw = cleanValue(rawValue) ?: return null
    val upper = raw.uppercase(Locale.ROOT)
    val plmn = Regex("(460\\d{2})").find(upper)?.groupValues?.get(1)
    return when {
        plmn in setOf("46000", "46002", "46004", "46007", "46008", "46020") -> "中国移动"
        plmn in setOf("46001", "46006", "46009", "46010") -> "中国联通"
        plmn in setOf("46003", "46005", "46011", "46012") -> "中国电信"
        plmn == "46015" -> "中国广电"
        upper.contains("CHINA MOBILE") || upper.contains("CMCC") || raw.contains("移动") -> "中国移动"
        upper.contains("UNICOM") || upper.contains("CUCC") || raw.contains("联通") -> "中国联通"
        upper.contains("CHINA TELECOM") || upper.contains("CTCC") || raw.contains("电信") -> "中国电信"
        upper.contains("BROADNET") || upper.contains("CBN") || raw.contains("广电") -> "中国广电"
        else -> raw
    }
}

fun mapCarrier(iccid: String?, networkName: String?): String? =
    carrierFromIccid(iccid) ?: mapCarrier(networkName)

private val sysModeNames = mapOf(
    "0" to "无服务",
    "1" to "2G GSM",
    "2" to "2G GPRS",
    "3" to "2G EDGE",
    "4" to "3G WCDMA",
    "5" to "3G HSDPA",
    "6" to "3G HSUPA",
    "7" to "3G HSPA",
    "8" to "3G TD-SCDMA",
    "9" to "3G HSPA+",
    "10" to "4G LTE",
    "11" to "3G EVDO",
    "12" to "4G LTE-A",
    "13" to "4G",
    "14" to "4G+",
    "15" to "3G TD-SCDMA",
    "16" to "4G LTE",
    "17" to "4G LTE",
    "18" to "5G NSA",
    "19" to "5G SA",
    "20" to "5G",
)

fun mapNetworkType(rawValue: String?, bandHint: String? = null): String? {
    if (rawValue?.trim() == "0") return "无服务"
    val raw = cleanValue(rawValue)
    if (raw == null) {
        return when {
            bandHint?.startsWith("n", ignoreCase = true) == true -> "5G NR"
            bandHint?.startsWith("B", ignoreCase = true) == true -> "4G LTE"
            else -> null
        }
    }
    val upper = raw.uppercase(Locale.ROOT)
    sysModeNames[raw]?.let { return it }
    return when {
        upper == "NR" || upper.contains("NR5G") || upper.contains("5G") || upper.contains("NR_SA") -> "5G NR"
        upper.contains("LTE-A") || upper.contains("LTE+") || upper.contains("4G+") -> "4G+ LTE-A"
        upper.contains("LTE") || upper == "4G" -> "4G LTE"
        upper.contains("HSPA") -> "3G HSPA+"
        upper.contains("WCDMA") || upper.contains("UMTS") || upper == "3G" -> "3G WCDMA"
        upper.contains("TD-SCDMA") -> "3G TD-SCDMA"
        upper.contains("EVDO") -> "3G EVDO"
        upper.contains("EDGE") -> "2G EDGE"
        upper.contains("GPRS") -> "2G GPRS"
        upper.contains("GSM") || upper == "2G" -> "2G GSM"
        upper.contains("NO SERVICE") || upper.contains("无服务") -> "无服务"
        else -> raw
    }
}

fun formatBand(rawValue: String?, is5g: Boolean = false): String? {
    val raw = cleanValue(rawValue) ?: return null
    val upper = raw.uppercase(Locale.ROOT)
    if (upper == "AUTO" || upper == "自动") return "自动"
    val parts = raw.split(',', '/', '，', ' ').map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.isEmpty()) return raw
    val formatted = parts.mapNotNull { part ->
        val number = part.uppercase(Locale.ROOT)
            .removePrefix("BAND")
            .removePrefix("B")
            .removePrefix("N")
            .trim()
            .toIntOrNull()
        when {
            number == null -> part
            part.startsWith("n", true) || is5g || number > 64 -> "n$number"
            else -> "B$number"
        }
    }
    return if (formatted.isEmpty()) raw else formatted.distinct().joinToString("/")
}

fun signalLevelFromDbm(dbm: Int?): Int = when {
    dbm == null -> 0
    dbm >= -55 -> 5
    dbm >= -65 -> 4
    dbm >= -75 -> 3
    dbm >= -85 -> 2
    dbm >= -95 -> 1
    else -> 0
}

fun signalDbmFromBars(bars: Int): Int = when (bars.coerceIn(0, 5)) {
    5 -> -52
    4 -> -62
    3 -> -72
    2 -> -82
    1 -> -92
    else -> -105
}

/**
 * Accepts dBm, ASR raw RSRP/RSRP-like values, signal bars (0-5) and percentages.
 */
fun normalizeSignal(rawValue: String?, asrRawRsrp: Boolean = false): Pair<Int?, Int> {
    val value = firstNumber(cleanValue(rawValue)) ?: return null to 0
    if (asrRawRsrp && value in 0.0..97.0) {
        val dbm = (-140 + value).toInt().coerceIn(-140, -30)
        return dbm to signalLevelFromDbm(dbm)
    }
    if (value in -140.0..-30.0) {
        val dbm = value.toInt()
        return dbm to signalLevelFromDbm(dbm)
    }
    if (value in 0.0..5.0) {
        val bars = value.toInt().coerceIn(0, 5)
        return signalDbmFromBars(bars) to bars
    }
    if (value in 6.0..100.0) {
        val percent = value.toInt().coerceIn(0, 100)
        val bars = ceil(percent / 20.0).toInt().coerceIn(0, 5)
        val dbm = (-110 + (percent * 0.6)).toInt()
        return dbm to bars
    }
    return null to 0
}

fun normalizeAsrRssi(rawValue: String?, sysMode: String? = null): Int? {
    val value = firstNumber(cleanValue(rawValue)) ?: return null
    if (value in -140.0..-30.0) return value.toInt()
    if (value !in 0.0..97.0) return null
    val mode = sysMode?.trim()?.toIntOrNull()
    return when {
        // ASR reports raw RSRP in LTE/NR modes and raw RSSI on 2G/3G.
        mode in setOf(10, 12, 13, 14, 16, 17, 18, 19, 20) -> (value - 140).toInt()
        mode == null -> (-110 + value).toInt()
        else -> (2 * value - 113).toInt()
    }
}

fun normalizeBatteryPercent(rawValue: String?): Int? {
    val value = firstNumber(rawValue) ?: return null
    return when {
        value in 0.0..100.0 -> value.toInt()
        value in 3000.0..4500.0 -> {
            // Approximate a single-cell Li-ion curve from millivolts.
            val percent = ((value - 3300.0) / (4200.0 - 3300.0) * 100.0).toInt()
            percent.coerceIn(0, 100)
        }
        value in 3.0..4.5 -> {
            val percent = ((value * 1000.0 - 3300.0) / (4200.0 - 3300.0) * 100.0).toInt()
            percent.coerceIn(0, 100)
        }
        else -> null
    }
}

fun decodeUnicodeHex(value: String?): String? {
    val text = cleanValue(value) ?: return null
    if (!Regex("^(?:[0-9a-fA-F]{4})+$").matches(text)) return text
    return runCatching {
        val chars = text.chunked(4).map { it.toInt(16).toChar() }.toCharArray()
        String(chars).trim('\u0000')
    }.getOrNull()?.takeIf { it.isNotBlank() && it.none { char -> char.isISOControl() } } ?: text
}

fun md5Hex(value: String): String {
    val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

fun randomHex(length: Int): String {
    val chars = "0123456789abcdef"
    return buildString(length) {
        repeat(length) { append(chars[Random.nextInt(chars.length)]) }
    }
}

data class DigestChallenge(
    val realm: String,
    val nonce: String,
    val qop: String,
)

fun parseDigestChallenge(header: String?): DigestChallenge? {
    val value = header ?: return null
    if (!value.startsWith("Digest", ignoreCase = true)) return null
    val realm = Regex("realm=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
        .find(value)?.groupValues?.getOrNull(1) ?: return null
    val nonce = Regex("nonce=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
        .find(value)?.groupValues?.getOrNull(1) ?: return null
    val qop = Regex("qop=\"?([^,\"]+)\"?", RegexOption.IGNORE_CASE)
        .find(value)?.groupValues?.getOrNull(1)?.substringBefore(',') ?: "auth"
    return DigestChallenge(realm = realm, nonce = nonce, qop = qop)
}

fun humanizeRelative(timestamp: Long): String {
    if (timestamp <= 0) return "尚未刷新"
    val seconds = (System.currentTimeMillis() - timestamp).coerceAtLeast(0) / 1000
    return when {
        seconds < 5 -> "刚刚更新"
        seconds < 60 -> "${seconds} 秒前更新"
        seconds < 3600 -> "${seconds / 60} 分钟前更新"
        else -> "${seconds / 3600} 小时前更新"
    }
}

fun clampPercentage(value: Float): Float = value.coerceIn(0f, 1f)

fun approximateDbmDelta(a: Int?, b: Int?): Int = abs((a ?: 0) - (b ?: 0))
