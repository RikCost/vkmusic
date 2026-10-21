package com.vkmusic.ui

import java.util.Calendar
import java.util.Locale

fun formatDuration(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0) / 1000)
    return String.format(Locale.ROOT, "%d:%02d", totalSec / 60, totalSec % 60)
}

// Формулировки Meridius (langs/ru.json → PLAYLIST.DURATION).
fun formatTotalDuration(totalSec: Long): String {
    val hours = totalSec / 3600
    val minutes = totalSec % 3600 / 60
    val seconds = totalSec % 60
    return when {
        hours == 0L && minutes == 0L -> "$seconds сек."
        hours == 0L -> "$minutes мин. $seconds сек."
        hours < 24 -> "$hours ч. $minutes мин."
        else -> "больше 24 часов"
    }
}

private val MONTHS = listOf("янв", "фев", "мар", "апр", "май", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")

/** «26 июл 2019» — как «Обновлено …» в шапке плейлиста Meridius. */
fun formatDate(epochSec: Long): String {
    val calendar = Calendar.getInstance().apply { timeInMillis = epochSec * 1000 }
    return "${calendar.get(Calendar.DAY_OF_MONTH)} ${MONTHS[calendar.get(Calendar.MONTH)]} ${calendar.get(Calendar.YEAR)}"
}

/** «1 584 013» — разряды через неразрывный пробел. */
fun formatCount(value: Long): String = String.format(Locale.ROOT, "%,d", value).replace(',', ' ')
