package io.ahxxm.ic.domain

import java.util.Locale

fun formatBytes(bytes: Long, signed: Boolean): String {
    val sign = when {
        bytes < 0 -> "-"
        signed && bytes > 0 -> "+"
        else -> ""
    }
    val abs = kotlin.math.abs(bytes)
    if (abs < 1024) return "$sign$abs B"
    val kb = abs / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%s%.1f KB", sign, kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%s%.1f MB", sign, mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%s%.2f GB", sign, gb)
}
