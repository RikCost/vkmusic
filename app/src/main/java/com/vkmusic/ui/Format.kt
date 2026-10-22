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

/** Русская форма слова для числа: 1 трек, 2 трека, 5 треков, 11 треков, 21 трек. */
fun plural(count: Long, one: String, few: String, many: String): String {
    val mod10 = count % 10
    val mod100 = count % 100
    return when {
        mod10 == 1L && mod100 != 11L -> one
        mod10 in 2..4 && mod100 !in 12..14 -> few
        else -> many
    }
}

fun formatTracks(count: Int): String = "$count ${plural(count.toLong(), "трек", "трека", "треков")}"
