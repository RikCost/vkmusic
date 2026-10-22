package com.vkmusic.player.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.vkmusic.data.repository.EqualizerSettings
import com.vkmusic.data.repository.NormalizerSettings
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class AudioProcessorsTest {

    private val format = AudioProcessor.AudioFormat(SAMPLE_RATE, 2, C.ENCODING_PCM_16BIT)

    @Test
    fun `pcm16 survives float round trip exactly`() {
        for (value in Short.MIN_VALUE..Short.MAX_VALUE) {
            val short = value.toShort()
            assertEquals(short, FloatPcmAudioProcessor.toPcm16(FloatPcmAudioProcessor.fromPcm16(short)))
        }
    }

    @Test
    fun `disabled effects pass audio through byte for byte`() {
        val input = sine(peak = 0.7f, seconds = 0.5)
        val effects = AudioEffects()
        val eq = EqualizerAudioProcessor(effects)
        val normalizer = LoudnessNormalizerAudioProcessor(effects)
        assertArrayEquals(input, run(normalizer, run(eq, input)))
    }

    @Test
    fun `normalizer boost never clips loud peaks`() {
        // Тихий по громкости, но с ударами почти у предела: нормализатор захочет поднять на +10 дБ.
        val input = sine(peak = 0.15f, seconds = 4.0, hitPeak = 0.95f)
        val effects = AudioEffects().apply { normalizer = NormalizerSettings(enabled = true, targetLufs = -5f, maxGainDb = 10f) }
        val output = samples(run(LoudnessNormalizerAudioProcessor(effects), input))

        val tail = output.drop(output.size / 2)
        assertTrue("усиление должно сработать", tail.maxOf { abs(it.toInt()) } > 0.9 * Short.MAX_VALUE)
        assertTrue("пики не доходят до предела", tail.none { abs(it.toInt()) >= Short.MAX_VALUE - 1 })
    }

    @Test
    fun `equalizer boost is compensated by preamp`() {
        val input = sine(peak = 0.95f, seconds = 1.0, frequency = 1000.0)
        val levels = EqualizerPresets.flat.toMutableList().also { it[EqualizerPresets.frequencies.indexOf(1000)] = 12f }
        val effects = AudioEffects().apply { equalizer = EqualizerSettings(enabled = true, preset = EqualizerPresets.CUSTOM, levels = levels) }
        val output = samples(run(EqualizerAudioProcessor(effects), input))

        assertTrue(output.none { abs(it.toInt()) >= Short.MAX_VALUE - 1 })
    }

    private fun run(processor: AudioProcessor, input: ByteArray): ByteArray {
        processor.configure(format)
        processor.flush()
        val out = ByteArrayOutputStream()
        var offset = 0
        while (offset < input.size) {
            val size = minOf(BUFFER_BYTES, input.size - offset)
            val chunk = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
            chunk.put(input, offset, size).flip()
            processor.queueInput(chunk)
            val result = processor.output
            out.write(ByteArray(result.remaining()).also(result::get))
            offset += size
        }
        return out.toByteArray()
    }

    // Синус; если задан [hitPeak] — с короткими «ударами» такой амплитуды раз в блок: громкость низкая, пики высокие.
    private fun sine(peak: Float, seconds: Double, frequency: Double = 440.0, hitPeak: Float? = null): ByteArray {
        val frames = (SAMPLE_RATE * seconds).toInt()
        val buffer = ByteBuffer.allocate(frames * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until frames) {
            val amplitude = if (hitPeak != null && i % BLOCK_FRAMES < HIT_FRAMES) hitPeak else peak
            val value = (amplitude * sin(2 * PI * frequency * i / SAMPLE_RATE) * Short.MAX_VALUE).toInt().toShort()
            buffer.putShort(value).putShort(value)
        }
        return buffer.array()
    }

    private fun samples(bytes: ByteArray): ShortArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).asShortBuffer()
        return ShortArray(buffer.remaining()).also(buffer::get)
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val BUFFER_BYTES = 4096
        const val BLOCK_FRAMES = 4410
        const val HIT_FRAMES = 200
    }
}
