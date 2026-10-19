package com.vkmusic.ui

import java.util.Locale

fun formatDuration(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0) / 1000)
    return String.format(Locale.ROOT, "%d:%02d", totalSec / 60, totalSec % 60)
}
