package com.vkmusic.data.export

import java.io.ByteArrayOutputStream

/**
 * Тег ID3v2.3 для сохранённого MP3 — те же поля, что Meridius пишет FFmpeg'ом (`hls.metadata()`):
 * исполнитель, название, альбом, кодировщик, плюс обложка (APIC, `-id3v2_version 3`).
 */
object Id3Writer {

    fun tag(title: String, artist: String, album: String?, cover: ByteArray?): ByteArray {
        val frames = ByteArrayOutputStream()
        frames.textFrame("TIT2", title)
        frames.textFrame("TPE1", artist)
        album?.takeIf { it.isNotBlank() }?.let { frames.textFrame("TALB", it) }
        frames.textFrame("TENC", ENCODED_BY)
        cover?.takeIf { it.isNotEmpty() }?.let { frames.coverFrame(it) }

        val body = frames.toByteArray()
        return ByteArrayOutputStream().apply {
            write("ID3".toByteArray(Charsets.ISO_8859_1))
            write(byteArrayOf(3, 0, 0))
            write(syncSafe(body.size))
            write(body)
        }.toByteArray()
    }

    // Текст в UTF-16 с BOM (кодировка 1) — кириллица в названиях обычна.
    private fun ByteArrayOutputStream.textFrame(id: String, value: String) {
        if (value.isBlank()) return
        val text = byteArrayOf(1) + value.toByteArray(Charsets.UTF_16)
        frame(id, text)
    }

    private fun ByteArrayOutputStream.coverFrame(image: ByteArray) {
        val mime = if (image.size > 3 && image[0] == 0x89.toByte() && image[1] == 'P'.code.toByte()) "image/png" else "image/jpeg"
        val data = ByteArrayOutputStream().apply {
            write(0) // кодировка описания
            write(mime.toByteArray(Charsets.ISO_8859_1))
            write(0)
            write(COVER_FRONT)
            write(0) // пустое описание
            write(image)
        }.toByteArray()
        frame("APIC", data)
    }

    private fun ByteArrayOutputStream.frame(id: String, data: ByteArray) {
        write(id.toByteArray(Charsets.ISO_8859_1))
        write(intBytes(data.size))
        write(byteArrayOf(0, 0))
        write(data)
    }

    // В v2.3 размер кадра — обычное 32-битное число, размер всего тега — «synchsafe» (по 7 бит).
    private fun intBytes(value: Int) = byteArrayOf((value shr 24).toByte(), (value shr 16).toByte(), (value shr 8).toByte(), value.toByte())

    private fun syncSafe(value: Int) = byteArrayOf(
        ((value shr 21) and 0x7F).toByte(),
        ((value shr 14) and 0x7F).toByte(),
        ((value shr 7) and 0x7F).toByte(),
        (value and 0x7F).toByte(),
    )

    private const val COVER_FRONT = 3
    private const val ENCODED_BY = "MuVK"
}
