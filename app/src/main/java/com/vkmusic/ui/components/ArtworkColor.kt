package com.vkmusic.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Главный цвет обложки — им подкрашивается фон плеера. null, пока обложка не загружена или её нет;
 * при смене трека прежний цвет держится до нового, чтобы фон не мигал.
 */
@Composable
fun rememberArtworkColor(url: String?): Color? {
    val context = LocalPlatformContext.current
    val color by produceState<Color?>(null, url) {
        if (url == null) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.Default) {
            // Картинка уже лежит в кэше Coil после показа обложки; жёсткий битмап пиксели не отдаёт.
            val request = ImageRequest.Builder(context).data(url).size(SAMPLE_SIZE).allowHardware(false).build()
            val bitmap = runCatching { SingletonImageLoader.get(context).execute(request).image?.toBitmap() }.getOrNull()
                ?: return@withContext null
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            dominantColor(pixels)?.let { Color.hsv(it.hue, it.saturation, it.value) }
        }
    }
    return color
}

/**
 * Преобладающий насыщенный оттенок: пиксели раскладываются по секторам цветового круга с весом
 * «насыщенность × яркость», берётся самый тяжёлый сектор. Серые и почти чёрные пиксели не участвуют —
 * иначе у тёмных обложек фон выходил бы грязно-серым. null — обложка бесцветная.
 */
internal fun dominantColor(pixels: IntArray): Hsv? {
    val weights = FloatArray(HUE_BUCKETS)
    val hue = FloatArray(HUE_BUCKETS)
    val saturation = FloatArray(HUE_BUCKETS)
    for (pixel in pixels) {
        val hsv = pixel.toHsv()
        if (hsv.saturation < MIN_SATURATION || hsv.value < MIN_VALUE) continue
        val weight = hsv.saturation * hsv.value
        val bucket = (hsv.hue / 360f * HUE_BUCKETS).toInt().coerceIn(0, HUE_BUCKETS - 1)
        weights[bucket] += weight
        hue[bucket] += hsv.hue * weight
        saturation[bucket] += hsv.saturation * weight
    }
    val best = weights.indices.maxBy { weights[it] }
    val weight = weights[best]
    // Цветных пикселей — единицы: это шум на чёрно-белой обложке, а не её цвет.
    if (weight < pixels.size * MIN_COLORED_SHARE) return null
    // Яркость фиксирована: фон должен оставаться фоном, какой бы светлой ни была обложка.
    return Hsv(hue[best] / weight, (saturation[best] / weight).coerceAtMost(MAX_SATURATION), TINT_VALUE)
}

internal data class Hsv(val hue: Float, val saturation: Float, val value: Float)

// Свой перевод вместо android.graphics.Color: функция остаётся чистой и проверяется обычным тестом.
private fun Int.toHsv(): Hsv {
    val r = (this shr 16 and 0xFF) / 255f
    val g = (this shr 8 and 0xFF) / 255f
    val b = (this and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val delta = max - minOf(r, g, b)
    val hue = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta).mod(6f))
        max == g -> 60f * ((b - r) / delta + 2f)
        else -> 60f * ((r - g) / delta + 4f)
    }
    return Hsv(hue, if (max == 0f) 0f else delta / max, max)
}

private const val SAMPLE_SIZE = 48
private const val HUE_BUCKETS = 12
private const val MIN_SATURATION = 0.2f
private const val MIN_VALUE = 0.2f
private const val MIN_COLORED_SHARE = 0.02f
private const val MAX_SATURATION = 0.8f
private const val TINT_VALUE = 0.75f
