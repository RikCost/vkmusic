package com.vkmusic.data.api.fake

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

internal object ToneTrackGenerator {
    private const val SAMPLE_RATE = 16_000
    private const val NOTE_SEC = 0.25

    fun generate(file: File, rootMidi: Int, minor: Boolean, durationSec: Int) {
        if (file.exists() && file.length() > 44) return
        file.parentFile?.mkdirs()

        val third = if (minor) 3 else 4
        val pattern = intArrayOf(0, third, 7, 12, 7, third)
        val totalSamples = SAMPLE_RATE * durationSec
        val samplesPerNote = (SAMPLE_RATE * NOTE_SEC).toInt()
        val pcm = ByteBuffer.allocate(totalSamples * 2).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until totalSamples) {
            val noteIndex = i / samplesPerNote
            val bar = noteIndex / pattern.size
            val shift = if (bar % 4 == 2) 5 else if (bar % 4 == 3) 7 else 0
            val freq = midiToHz(rootMidi + shift + pattern[noteIndex % pattern.size])
            val bass = midiToHz(rootMidi + shift - 12)
            val tNote = (i % samplesPerNote).toDouble() / SAMPLE_RATE
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = exp(-tNote * 6)
            val fade = minOf(1.0, t / 0.5, (durationSec - t) / 1.0)
            val value = (0.45 * envelope * sin(2 * PI * freq * t) + 0.2 * sin(2 * PI * bass * t)) * fade
            pcm.putShort((value * Short.MAX_VALUE * 0.8).toInt().toShort())
        }

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + pcm.capacity()); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(SAMPLE_RATE); putInt(SAMPLE_RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(pcm.capacity())
        }
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.outputStream().use {
            it.write(header.array())
            it.write(pcm.array())
        }
        tmp.renameTo(file)
    }

    private fun midiToHz(midi: Int) = 440.0 * 2.0.pow((midi - 69) / 12.0)
}
