package com.vkmusic.player.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * Процессор, который обрабатывает PCM 16 бит или float как чередующиеся float-отсчёты в [-1, 1].
 * Формат на выходе не меняется; прочие кодировки пропускаются (процессор становится неактивным).
 * Пока эффект выключен ([isEnabled]), звук проходит байт в байт — без перевода во float и обратно.
 */
@UnstableApi
abstract class FloatPcmAudioProcessor : BaseAudioProcessor() {

    protected var sampleRate = 0
        private set
    protected var channelCount = 0
        private set

    private var scratch = FloatArray(0)

    /** Включён ли эффект сейчас; читается на каждом буфере. */
    protected abstract val isEnabled: Boolean

    /** Обработать [frames] кадров в [samples] (чередование по каналам) на месте. */
    protected abstract fun process(samples: FloatArray, frames: Int)

    /** Сбросить состояние фильтров: новый формат, полный сброс плеера или выключение эффекта. */
    protected abstract fun resetState()

    private var bypassing = false

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
        if (!isEnabled) {
            // Эффект выключили — при следующем включении фильтры стартуют с чистого состояния.
            if (!bypassing) resetState()
            bypassing = true
            replaceOutputBuffer(remaining).put(inputBuffer).flip()
            return
        }
        bypassing = false

        val isFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val sampleCount = remaining / if (isFloat) 4 else 2
        if (scratch.size < sampleCount) scratch = FloatArray(sampleCount)

        for (i in 0 until sampleCount) {
            scratch[i] = if (isFloat) inputBuffer.getFloat() else inputBuffer.getShort() / PCM16_SCALE
        }
        process(scratch, sampleCount / channelCount)

        val output = replaceOutputBuffer(remaining)
        for (i in 0 until sampleCount) {
            val sample = scratch[i].coerceIn(-1f, 1f)
            if (isFloat) output.putFloat(sample) else output.putShort(toPcm16(sample))
        }
        output.flip()
    }

    override fun onReset() {
        resetState()
    }

    companion object {
        private const val PCM16_SCALE = 32768f

        // Тот же масштаб, что при чтении, и округление: нетронутый отсчёт возвращается точно таким же.
        // Отбрасывание дробной части сдвигало каждый отсчёт к нулю на единицу младшего разряда — это шорох.
        fun toPcm16(sample: Float): Short = (sample * PCM16_SCALE).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

        fun fromPcm16(value: Short): Float = value / PCM16_SCALE
    }
}
