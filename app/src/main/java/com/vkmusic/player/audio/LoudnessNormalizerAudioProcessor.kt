package com.vkmusic.player.audio

import androidx.media3.common.util.UnstableApi
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.tan

/**
 * Нормализация громкости по алгоритму Meridius (renderer `calculateGain`):
 * громкость считается в LUFS по K-взвешенному сигналу, усиление
 * `gainDb = clamp(target − loudness, −max, +max)`.
 *
 * Meridius меряет каждый HLS-сегмент целиком до воспроизведения; здесь звук приходит потоком, поэтому
 * громкость оценивается по скользящему окну последних [WINDOW_BLOCKS] блоков по 100 мс (кратковременная
 * громкость BS.1770), а усиление плавно подтягивается к цели, чтобы не было скачков.
 */
@UnstableApi
class LoudnessNormalizerAudioProcessor(private val effects: AudioEffects) : FloatPcmAudioProcessor() {

    private class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double)

    private var shelf = IDENTITY
    private var highPass = IDENTITY
    private var filterState = Array(0) { DoubleArray(8) }

    private val blocks = DoubleArray(WINDOW_BLOCKS)
    private var blockCount = 0
    private var blockIndex = 0
    private var blockSum = 0.0
    private var blockFrames = 0
    private var framesPerBlock = 0

    private var currentGain = 1.0
    private var targetGain = 1.0
    private var smoothing = 0.0

    override fun process(samples: FloatArray, frames: Int) {
        val settings = effects.normalizer
        if (!settings.enabled) {
            currentGain = 1.0
            targetGain = 1.0
            return
        }
        for (frame in 0 until frames) {
            var weighted = 0.0
            for (channel in 0 until channelCount) {
                val index = frame * channelCount + channel
                val k = kWeight(samples[index].toDouble(), filterState[channel])
                // BS.1770: каналы L/R/C с весом 1 — для стерео это просто сумма квадратов.
                weighted += k * k
            }
            blockSum += weighted
            if (++blockFrames >= framesPerBlock) closeBlock(settings.targetLufs.toDouble(), settings.maxGainDb.toDouble())

            currentGain += (targetGain - currentGain) * smoothing
            for (channel in 0 until channelCount) {
                val index = frame * channelCount + channel
                samples[index] = (samples[index] * currentGain).toFloat()
            }
        }
    }

    override fun resetState() {
        if (sampleRate > 0) {
            shelf = highShelf(sampleRate.toDouble())
            highPass = highPass(sampleRate.toDouble())
            framesPerBlock = sampleRate / 10
            smoothing = 1.0 - exp(-1.0 / (GAIN_TIME_CONSTANT_SEC * sampleRate))
        }
        filterState = Array(channelCount) { DoubleArray(8) }
        blocks.fill(0.0)
        blockCount = 0
        blockIndex = 0
        blockSum = 0.0
        blockFrames = 0
    }

    private fun closeBlock(target: Double, maxGainDb: Double) {
        blocks[blockIndex] = blockSum / blockFrames
        blockIndex = (blockIndex + 1) % WINDOW_BLOCKS
        if (blockCount < WINDOW_BLOCKS) blockCount++
        blockSum = 0.0
        blockFrames = 0
        if (blockCount < MIN_BLOCKS) return

        // Абсолютный порог −70 LUFS: тишина не должна раскачивать усиление (как и в Meridius, где
        // нечисловая громкость оставляет прошлый коэффициент).
        var sum = 0.0
        var used = 0
        for (i in 0 until blockCount) {
            if (loudness(blocks[i]) > ABSOLUTE_GATE_LUFS) {
                sum += blocks[i]
                used++
            }
        }
        if (used == 0) return
        val gainDb = (target - loudness(sum / used)).coerceIn(-maxGainDb, maxGainDb)
        targetGain = 10.0.pow(gainDb / 20.0)
    }

    private fun kWeight(x: Double, s: DoubleArray): Double {
        val y1 = shelf.b0 * x + shelf.b1 * s[0] + shelf.b2 * s[1] - shelf.a1 * s[2] - shelf.a2 * s[3]
        s[1] = s[0]
        s[0] = x
        s[3] = s[2]
        s[2] = y1
        val y2 = highPass.b0 * y1 + highPass.b1 * s[4] + highPass.b2 * s[5] - highPass.a1 * s[6] - highPass.a2 * s[7]
        s[5] = s[4]
        s[4] = y1
        s[7] = s[6]
        s[6] = y2
        return y2
    }

    private fun loudness(meanSquare: Double): Double = if (meanSquare <= 0.0) Double.NEGATIVE_INFINITY else -0.691 + 10 * log10(meanSquare)

    private companion object {
        val IDENTITY = Biquad(1.0, 0.0, 0.0, 0.0, 0.0)

        const val WINDOW_BLOCKS = 30
        const val MIN_BLOCKS = 4
        const val ABSOLUTE_GATE_LUFS = -70.0
        const val GAIN_TIME_CONSTANT_SEC = 0.5

        // Коэффициенты K-фильтра BS.1770, пересчитанные под частоту дискретизации
        // (как в libebur128: полка 1681.97 Гц +4 дБ, ФВЧ 38.13 Гц).
        fun highShelf(rate: Double): Biquad {
            val g = 3.999843853973347
            val q = 0.7071752369554196
            val k = tan(PI * 1681.974450955533 / rate)
            val vh = 10.0.pow(g / 20.0)
            val vb = vh.pow(0.4996667741545416)
            val a0 = 1.0 + k / q + k * k
            return Biquad(
                b0 = (vh + vb * k / q + k * k) / a0,
                b1 = 2.0 * (k * k - vh) / a0,
                b2 = (vh - vb * k / q + k * k) / a0,
                a1 = 2.0 * (k * k - 1.0) / a0,
                a2 = (1.0 - k / q + k * k) / a0,
            )
        }

        fun highPass(rate: Double): Biquad {
            val q = 0.5003270373238773
            val k = tan(PI * 38.13547087602444 / rate)
            val a0 = 1.0 + k / q + k * k
            // Числитель ненормирован (1, −2, 1) — так в libebur128, под это откалибрована константа −0.691.
            return Biquad(
                b0 = 1.0,
                b1 = -2.0,
                b2 = 1.0,
                a1 = 2.0 * (k * k - 1.0) / a0,
                a2 = (1.0 - k / q + k * k) / a0,
            )
        }
    }
}
