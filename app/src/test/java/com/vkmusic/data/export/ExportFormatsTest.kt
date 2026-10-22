package com.vkmusic.data.export

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportFormatsTest {

    @Test
    fun `media playlist resolves segments and keys relative to playlist`() {
        val text = """
            #EXTM3U
            #EXT-X-MEDIA-SEQUENCE:7
            #EXT-X-KEY:METHOD=AES-128,URI="key.pub"
            #EXTINF:20.0,
            seg-1-a1.ts?extra=1
            #EXT-X-KEY:METHOD=NONE
            #EXTINF:20.0,
            seg-2-a1.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val segments = HlsPlaylist.parse(text, "https://cs1.vkuseraudio.net/s/v1/ac/abc/index.m3u8?siren=1")

        assertEquals(2, segments.size)
        assertEquals("https://cs1.vkuseraudio.net/s/v1/ac/abc/seg-1-a1.ts?extra=1", segments[0].url)
        assertEquals(7L, segments[0].sequence)
        assertEquals("AES-128", segments[0].key?.method)
        assertEquals("https://cs1.vkuseraudio.net/s/v1/ac/abc/key.pub", segments[0].key?.uri)
        assertNull(segments[1].key)
        assertEquals(8L, segments[1].sequence)
    }

    @Test
    fun `explicit key iv is parsed`() {
        val segments = HlsPlaylist.parse(
            "#EXT-X-KEY:METHOD=AES-128,URI=\"k\",IV=0x000102030405060708090A0B0C0D0E0F\n#EXTINF:1,\ns.ts",
            "https://host/p/index.m3u8",
        )
        assertArrayEquals(ByteArray(16) { it.toByte() }, segments.single().key?.iv)
    }

    @Test
    fun `mpeg audio is pulled out of transport stream across packets`() {
        val extractor = TsAudioExtractor()
        val pesHeader = byteArrayOf(0, 0, 1, 0xC0.toByte(), 0, 0, 0x80.toByte(), 0x80.toByte(), 5, 0x21, 0, 1, 0, 1)
        extractor.feed(pat() + pmt(streamType = 0x03) + packet(AUDIO_PID, start = true, pesHeader + ByteArray(170) { 'A'.code.toByte() }))
        // Следующий сегмент: PES продолжается без нового заголовка.
        extractor.feed(packet(AUDIO_PID, start = false, ByteArray(184) { 'B'.code.toByte() }))

        val (audio, codec) = extractor.result()

        assertEquals(AudioCodec.Mp3, codec)
        assertEquals(170 + 184, audio.size)
        assertTrue(audio.take(170).all { it == 'A'.code.toByte() })
        assertTrue(audio.drop(170).all { it == 'B'.code.toByte() })
    }

    @Test
    fun `duplicate packets and false sync bytes do not leak into audio`() {
        val extractor = TsAudioExtractor()
        val pesHeader = byteArrayOf(0, 0, 1, 0xC0.toByte(), 0, 0, 0x80.toByte(), 0, 0)
        val first = packet(AUDIO_PID, start = true, pesHeader + ByteArray(175) { 'A'.code.toByte() }, continuity = 0)
        val second = packet(AUDIO_PID, start = false, ByteArray(184) { 'B'.code.toByte() }, continuity = 1)
        // Испорченное начало сегмента с ложным 0x47, за которым идёт «заголовок» аудио-PID.
        val garbage = byteArrayOf(1, 2, 0x47, (AUDIO_PID shr 8).toByte(), (AUDIO_PID and 0xFF).toByte(), 0x11, 'X'.code.toByte(), 9, 9)
        extractor.feed(garbage + pat() + pmt(streamType = 0x03) + first + second + second)

        val audio = extractor.result().first

        assertEquals(175 + 184, audio.size)
        assertTrue(audio.none { it == 'X'.code.toByte() })
    }

    @Test
    fun `aac stream type is reported as aac`() {
        val extractor = TsAudioExtractor()
        val pesHeader = byteArrayOf(0, 0, 1, 0xC0.toByte(), 0, 0, 0x80.toByte(), 0, 0)
        extractor.feed(pat() + pmt(streamType = 0x0F) + packet(AUDIO_PID, start = true, pesHeader + ByteArray(175)))
        assertEquals(AudioCodec.Aac, extractor.result().second)
    }

    @Test
    fun `id3 tag has v2_3 header with synchsafe size and frames`() {
        val tag = Id3Writer.tag("Название", "Исполнитель", "Альбом", cover = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2))

        assertEquals("ID3", String(tag, 0, 3, Charsets.ISO_8859_1))
        assertEquals(3, tag[3].toInt())
        val size = (tag[6].toInt() shl 21) or (tag[7].toInt() shl 14) or (tag[8].toInt() shl 7) or tag[9].toInt()
        assertEquals(tag.size - 10, size)
        val ascii = String(tag, Charsets.ISO_8859_1)
        listOf("TIT2", "TPE1", "TALB", "APIC", "image/jpeg").forEach { assertTrue(it, ascii.contains(it)) }
    }

    @Test
    fun `file names lose characters forbidden on storage`() {
        assertEquals("AC_DC - Back In Black_", ExportStorage.sanitize("AC/DC - Back In Black?"))
    }

    private fun packet(pid: Int, start: Boolean, payload: ByteArray, continuity: Int = 0): ByteArray {
        val header = byteArrayOf(0x47, ((if (start) 0x40 else 0) or (pid shr 8)).toByte(), (pid and 0xFF).toByte(), (0x10 or continuity).toByte())
        return header + payload.copyOf(184).also { bytes -> for (i in payload.size until 184) bytes[i] = 0xFF.toByte() }
    }

    private fun pat(): ByteArray = packet(
        0,
        start = true,
        bytes(0x00, 0x00, 0xB0, 0x0D, 0x00, 0x01, 0xC1, 0x00, 0x00, 0x00, 0x01, 0xE0 or (PMT_PID shr 8), PMT_PID and 0xFF, 0, 0, 0, 0),
    )

    private fun pmt(streamType: Int): ByteArray = packet(
        PMT_PID,
        start = true,
        bytes(
            0x00, 0x02, 0xB0, 0x12, 0x00, 0x01, 0xC1, 0x00, 0x00,
            0xE0 or (AUDIO_PID shr 8), AUDIO_PID and 0xFF, 0xF0, 0x00,
            streamType, 0xE0 or (AUDIO_PID shr 8), AUDIO_PID and 0xFF, 0xF0, 0x00,
            0, 0, 0, 0,
        ),
    )

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private companion object {
        const val PMT_PID = 0x100
        const val AUDIO_PID = 0x101
    }
}
