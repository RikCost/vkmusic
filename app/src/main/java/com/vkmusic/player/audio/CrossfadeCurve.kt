package com.vkmusic.player.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Равномощная кривая перехода: cos²+sin² = 1, поэтому суммарная мощность двух некоррелированных треков
 * не проседает посередине, как при линейном переходе.
 */
object CrossfadeCurve {
    /** @return громкость уходящего и входящего трека при прогрессе перехода [progress] от 0 до 1. */
    fun gains(progress: Float): Pair<Float, Float> {
        val angle = progress.coerceIn(0f, 1f) * PI / 2
        return cos(angle).toFloat() to sin(angle).toFloat()
    }
}
