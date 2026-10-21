package com.vkmusic.player.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Процессор, который обрабатывает PCM 16 бит или float как чередующиеся float-отсчёты в [-1, 1].
 * Формат на выходе не меняется; прочие кодировки пропускаются (процессор становится неактивным).
 */
@UnstableApi
abstract class FloatPcmAudioProcessor : BaseAudioProcessor() {

    protected var sampleRate = 0
        private set
    protected var channelCount = 0
        private set

    private var scratch = FloatArray(0)

    /** Обработать [frames] кадров в [samples] (чередование по каналам) на месте. */
    protected abstract fun process(samples: FloatArray, frames: Int)

    /** Сбросить состояние фильтров: новый формат или полный сброс плеера. */
    protected abstract fun resetState()

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            return AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        resetState()
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val isFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val sampleCount = remaining / if (isFloat) 4 else 2
        if (scratch.size < sampleCount) scratch = FloatArray(sampleCount)

        for (i in 0 until sampleCount) {
            scratch[i] = if (isFloat) inputBuffer.getFloat() else inputBuffer.getShort() / 32768f
        }
        process(scratch, sampleCount / channelCount)

        val output = replaceOutputBuffer(remaining)
        for (i in 0 until sampleCount) {
            val sample = scratch[i].coerceIn(-1f, 1f)
            if (isFloat) output.putFloat(sample) else output.putShort((sample * 32767f).toInt().toShort())
        }
        output.flip()
    }

    override fun onReset() {
        resetState()
    }
}
