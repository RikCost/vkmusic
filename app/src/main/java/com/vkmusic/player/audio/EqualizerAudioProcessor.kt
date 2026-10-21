package com.vkmusic.player.audio

import androidx.media3.common.util.UnstableApi
import com.vkmusic.data.repository.EqualizerSettings
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * 18-полосный эквалайзер Meridius: каскад peaking-биквадов (формулы RBJ, те же, что у
 * `BiquadFilterNode` типа `peaking` в WebAudio) на частотах [EqualizerPresets.frequencies], Q = 1.
 */
@UnstableApi
class EqualizerAudioProcessor(private val effects: AudioEffects) : FloatPcmAudioProcessor() {

    private class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double)

    private var appliedSettings: EqualizerSettings? = null
    private var filters: List<Biquad> = emptyList()

    // Состояние Direct Form I: [фильтр][канал] -> x1, x2, y1, y2.
    private var state = Array(0) { Array(0) { DoubleArray(4) } }

    override fun process(samples: FloatArray, frames: Int) {
        val settings = effects.equalizer
        if (settings !== appliedSettings) rebuild(settings)
        if (filters.isEmpty()) return

        for (frame in 0 until frames) {
            for (channel in 0 until channelCount) {
                val index = frame * channelCount + channel
                var x = samples[index].toDouble()
                for (f in filters.indices) {
                    val c = filters[f]
                    val s = state[f][channel]
                    val y = c.b0 * x + c.b1 * s[0] + c.b2 * s[1] - c.a1 * s[2] - c.a2 * s[3]
                    s[1] = s[0]
                    s[0] = x
                    s[3] = s[2]
                    s[2] = y
                    x = y
                }
                samples[index] = x.toFloat()
            }
        }
    }

    override fun resetState() {
        appliedSettings = null
        filters = emptyList()
    }

    private fun rebuild(settings: EqualizerSettings) {
        appliedSettings = settings
        val nyquist = sampleRate / 2.0
        filters = if (!settings.enabled || sampleRate == 0) {
            emptyList()
        } else {
            EqualizerPresets.frequencies.indices
                .filter { settings.levels.getOrElse(it) { 0f } != 0f && EqualizerPresets.frequencies[it] < nyquist }
                .map { peaking(EqualizerPresets.frequencies[it].toDouble(), settings.levels[it].toDouble()) }
        }
        state = Array(filters.size) { Array(channelCount) { DoubleArray(4) } }
    }

    private fun peaking(frequency: Double, gainDb: Double): Biquad {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * frequency / sampleRate
        val alpha = sin(w0) / (2.0 * EqualizerPresets.Q)
        val cosW0 = cos(w0)
        val a0 = 1.0 + alpha / a
        return Biquad(
            b0 = (1.0 + alpha * a) / a0,
            b1 = (-2.0 * cosW0) / a0,
            b2 = (1.0 - alpha * a) / a0,
            a1 = (-2.0 * cosW0) / a0,
            a2 = (1.0 - alpha / a) / a0,
        )
    }
}
