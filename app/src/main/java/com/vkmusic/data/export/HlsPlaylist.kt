package com.vkmusic.data.export

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Ключ шифрования сегмента (`#EXT-X-KEY`). */
data class HlsKey(val method: String, val uri: String?, val iv: ByteArray?)

data class HlsSegment(val url: String, val sequence: Long, val key: HlsKey?)

/**
 * Разбор медиаплейлиста HLS — то, что meridius-core делает m3u8-parser'ом в `hls.parse`:
 * список сегментов с абсолютными адресами и действующим для каждого ключом.
 */
object HlsPlaylist {

    fun parse(text: String, playlistUrl: String): List<HlsSegment> {
        val base = playlistUrl.toHttpUrl()
        val segments = mutableListOf<HlsSegment>()
        var sequence = 0L
        var key: HlsKey? = null
        var expectUri = false
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> sequence = line.substringAfter(':').trim().toLongOrNull() ?: 0L
                line.startsWith("#EXT-X-KEY:") -> key = parseKey(line.substringAfter(':'), base)
                line.startsWith("#EXTINF") -> expectUri = true
                line.startsWith("#") -> Unit
                expectUri || !line.startsWith("#") -> {
                    segments += HlsSegment(resolve(base, line), sequence++, key?.takeIf { it.method != "NONE" })
                    expectUri = false
                }
            }
        }
        return segments
    }

    private fun parseKey(attributes: String, base: HttpUrl): HlsKey {
        val values = ATTRIBUTE_REGEX.findAll(attributes).associate { it.groupValues[1] to it.groupValues[2].trim('"') }
        val iv = values["IV"]?.removePrefix("0x")?.removePrefix("0X")?.let(::hexToBytes)
        return HlsKey(values["METHOD"] ?: "NONE", values["URI"]?.let { resolve(base, it) }, iv)
    }

    private fun resolve(base: HttpUrl, reference: String): String = base.resolve(reference)?.toString() ?: reference

    private fun hexToBytes(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        return ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toIntOrNull(16)?.toByte() ?: return null }
    }

    private val ATTRIBUTE_REGEX = Regex("([A-Z0-9-]+)=(\"[^\"]*\"|[^,]*)")
}
