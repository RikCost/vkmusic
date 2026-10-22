package com.vkmusic.player.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FloatPcmAudioProcessorTest {

    // Запоминает, в каком формате обработан каждый буфер.
    private class Recorder : FloatPcmAudioProcessor() {
        val seen = mutableListOf<Triple<Int, Int, Int>>()
        var resets = 0

        override val isEnabled = true

        override fun process(samples: FloatArray, frames: Int) {
            seen += Triple(sampleRate, channelCount, frames)
        }

        override fun resetState() {
            resets++
        }
    }

    @Test
    fun `new format applies only on flush`() {
        val processor = Recorder()
        processor.configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT))
        processor.flush()

        // Новый формат пришёл, но до flush() идут буферы прежнего: 16 бит стерео.
        processor.configure(AudioProcessor.AudioFormat(48_000, 1, C.ENCODING_PCM_FLOAT))
        processor.queueInput(buffer(bytes = 400))
        assertEquals(Triple(44_100, 2, 100), processor.seen.last())

        processor.flush()
        processor.queueInput(buffer(bytes = 400))
        assertEquals(Triple(48_000, 1, 100), processor.seen.last())
    }

    @Test
    fun `seek flush keeps filter state`() {
        val processor = Recorder()
        processor.configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT))
        processor.flush()
        val resets = processor.resets

        processor.flush()
        assertEquals(resets, processor.resets)
    }

    private fun buffer(bytes: Int): ByteBuffer = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())
}
