package com.vkmusic.data.export

import java.io.ByteArrayOutputStream

enum class AudioCodec(val extension: String, val mimeType: String) {
    Mp3("mp3", "audio/mpeg"),
    Aac("aac", "audio/aac"),
}

/**
 * Извлекает аудиопоток из MPEG-TS сегментов HLS без перекодирования. Meridius делает это FFmpeg'ом
 * (с перекодированием в 320 kbps); здесь аудиокадры просто вынимаются из PES-пакетов: MP3 в TS
 * склеивается в обычный .mp3, AAC — в ADTS-поток .aac.
 *
 * Состояние (PID-ы из PAT/PMT, незавершённый PES) переживает границы сегментов.
 */
class TsAudioExtractor {
    private val output = ByteArrayOutputStream()
    private var pmtPid = -1
    private var audioPid = -1
    private var streamType = -1

    // PES-заголовок может прийти не целиком в первом TS-пакете — копим до разбора.
    private var pesHeader: ByteArrayOutputStream? = null

    val bytesWritten: Int get() = output.size()

    fun feed(data: ByteArray) {
        var i = 0
        while (i + PACKET_SIZE <= data.size) {
            if (data[i] != SYNC) {
                i++
                continue
            }
            packet(data, i)
            i += PACKET_SIZE
        }
    }

    fun result(): Pair<ByteArray, AudioCodec> {
        val bytes = output.toByteArray()
        val codec = when (streamType) {
            STREAM_MPEG1_AUDIO, STREAM_MPEG2_AUDIO -> AudioCodec.Mp3
            STREAM_AAC_ADTS -> AudioCodec.Aac
            else -> detectCodec(bytes)
        }
        return bytes to codec
    }

    private fun packet(data: ByteArray, start: Int) {
        val payloadUnitStart = data[start + 1].toInt() and 0x40 != 0
        val pid = ((data[start + 1].toInt() and 0x1F) shl 8) or (data[start + 2].toInt() and 0xFF)
        val adaptation = (data[start + 3].toInt() shr 4) and 0x03
        if (adaptation == 0 || adaptation == 2) return
        var payload = start + 4
        if (adaptation == 3) payload += 1 + (data[start + 4].toInt() and 0xFF)
        val end = start + PACKET_SIZE
        if (payload >= end) return

        when {
            pid == PAT_PID -> if (payloadUnitStart) parsePat(data, payload, end)
            pid == pmtPid -> if (payloadUnitStart) parsePmt(data, payload, end)
            audioPid == -1 && payloadUnitStart && looksLikeAudioPes(data, payload, end) -> {
                // PMT не встретился (или повреждён) — берём первый поток с аудио-PES.
                audioPid = pid
                startPes(data, payload, end)
            }
            pid == audioPid -> if (payloadUnitStart) startPes(data, payload, end) else continuePes(data, payload, end)
        }
    }

    private fun parsePat(data: ByteArray, payload: Int, end: Int) {
        val section = payload + 1 + (data[payload].toInt() and 0xFF)
        // section header 8 байт, затем пары program_number(2) + PID(2), CRC в конце.
        var entry = section + 8
        val sectionLength = ((data[section + 1].toInt() and 0x0F) shl 8) or (data[section + 2].toInt() and 0xFF)
        val sectionEnd = minOf(section + 3 + sectionLength - 4, end)
        while (entry + 4 <= sectionEnd) {
            val program = ((data[entry].toInt() and 0xFF) shl 8) or (data[entry + 1].toInt() and 0xFF)
            if (program != 0) {
                pmtPid = ((data[entry + 2].toInt() and 0x1F) shl 8) or (data[entry + 3].toInt() and 0xFF)
                return
            }
            entry += 4
        }
    }

    private fun parsePmt(data: ByteArray, payload: Int, end: Int) {
        val section = payload + 1 + (data[payload].toInt() and 0xFF)
        if (section + 12 > end) return
        val sectionLength = ((data[section + 1].toInt() and 0x0F) shl 8) or (data[section + 2].toInt() and 0xFF)
        val programInfoLength = ((data[section + 10].toInt() and 0x0F) shl 8) or (data[section + 11].toInt() and 0xFF)
        var entry = section + 12 + programInfoLength
        val sectionEnd = minOf(section + 3 + sectionLength - 4, end)
        while (entry + 5 <= sectionEnd) {
            val type = data[entry].toInt() and 0xFF
            val pid = ((data[entry + 1].toInt() and 0x1F) shl 8) or (data[entry + 2].toInt() and 0xFF)
            val infoLength = ((data[entry + 3].toInt() and 0x0F) shl 8) or (data[entry + 4].toInt() and 0xFF)
            if (type in AUDIO_STREAM_TYPES) {
                audioPid = pid
                streamType = type
                return
            }
            entry += 5 + infoLength
        }
    }

    private fun startPes(data: ByteArray, payload: Int, end: Int) {
        pesHeader = ByteArrayOutputStream().apply { write(data, payload, end - payload) }
        flushPesHeader()
    }

    private fun continuePes(data: ByteArray, payload: Int, end: Int) {
        val pending = pesHeader
        if (pending != null) {
            pending.write(data, payload, end - payload)
            flushPesHeader()
        } else {
            output.write(data, payload, end - payload)
        }
    }

    // PES: 00 00 01 | stream_id | length(2) | flags(2) | header_data_length | заголовок | данные.
    private fun flushPesHeader() {
        val bytes = pesHeader?.toByteArray() ?: return
        if (bytes.size < 9) return
        if (bytes[0].toInt() != 0 || bytes[1].toInt() != 0 || bytes[2].toInt() != 1) {
            pesHeader = null
            return
        }
        val dataStart = 9 + (bytes[8].toInt() and 0xFF)
        if (bytes.size < dataStart) return
        output.write(bytes, dataStart, bytes.size - dataStart)
        pesHeader = null
    }

    private fun looksLikeAudioPes(data: ByteArray, payload: Int, end: Int): Boolean {
        if (payload + 4 > end) return false
        val streamId = data[payload + 3].toInt() and 0xFF
        return data[payload].toInt() == 0 && data[payload + 1].toInt() == 0 && data[payload + 2].toInt() == 1 && streamId in 0xC0..0xDF
    }

    // ADTS: слой = 00 (биты 1-2 второго байта), MP3 (Layer III): слой = 01.
    private fun detectCodec(bytes: ByteArray): AudioCodec {
        for (i in 0 until minOf(bytes.size - 1, SCAN_LIMIT)) {
            if (bytes[i].toInt() and 0xFF == 0xFF && bytes[i + 1].toInt() and 0xE0 == 0xE0) {
                val layer = (bytes[i + 1].toInt() shr 1) and 0x03
                return if (layer == 0) AudioCodec.Aac else AudioCodec.Mp3
            }
        }
        return AudioCodec.Mp3
    }

    private companion object {
        const val PACKET_SIZE = 188
        const val SYNC: Byte = 0x47
        const val PAT_PID = 0
        const val STREAM_MPEG1_AUDIO = 0x03
        const val STREAM_MPEG2_AUDIO = 0x04
        const val STREAM_AAC_ADTS = 0x0F
        const val SCAN_LIMIT = 4096
        val AUDIO_STREAM_TYPES = setOf(STREAM_MPEG1_AUDIO, STREAM_MPEG2_AUDIO, STREAM_AAC_ADTS)
    }
}
