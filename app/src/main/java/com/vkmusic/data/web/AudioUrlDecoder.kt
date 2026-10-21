package com.vkmusic.data.web

import kotlin.math.abs

/**
 * Снятие маски со ссылок веб-VK (`…audio_api_unavailable.mp3?extra=<данные>#<операции>`).
 * Порт `Static.ExposeSource` из meridius-core: данные и список операций закодированы base64 с
 * переставленным алфавитом, операции (v/r/s/i/x) применяются с конца списка.
 */
object AudioUrlDecoder {
    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMN0PQRSTUVWXYZO123456789+/="
    private const val UNAVAILABLE = "audio_api_unavailable"

    fun decode(url: String, userId: Long): String {
        if (!url.contains(UNAVAILABLE)) return url
        val extra = url.substringAfter("?extra=", "").takeIf { it.isNotEmpty() } ?: return url
        val parts = extra.split("#")
        var result = decodeBase64(parts[0]) ?: return url
        val opsEncoded = parts.getOrNull(1).orEmpty()
        val ops = if (opsEncoded.isEmpty()) emptyList() else decodeBase64(opsEncoded)?.split('\u0009') ?: return url
        for (op in ops.asReversed()) {
            val args = op.split('\u000B')
            result = when (args[0]) {
                "v" -> result.reversed()
                "r" -> rotate(result, args.getOrNull(1)?.toIntOrNull() ?: return url)
                "s" -> shuffle(result, args.getOrNull(1)?.toLongOrNull() ?: return url)
                "i" -> shuffle(result, (args.getOrNull(1)?.toLongOrNull() ?: return url) xor userId)
                "x" -> xor(result, args.getOrNull(1) ?: return url)
                else -> return url
            }
        }
        return if (result.startsWith("http")) result else url
    }

    private fun decodeBase64(input: String): String? {
        if (input.isEmpty() || input.length % 4 == 1) return null
        val out = StringBuilder()
        var acc = 0
        var count = 0
        for (c in input) {
            val index = ALPHABET.indexOf(c)
            if (index == -1) continue
            acc = if (count % 4 != 0) acc * 64 + index else index
            if (count++ % 4 != 0) out.append(((acc shr (-2 * count and 6)) and 0xFF).toChar())
        }
        return out.toString()
    }

    private fun rotate(s: String, shift: Int): String {
        val doubled = ALPHABET + ALPHABET
        return buildString {
            for (c in s) {
                val index = doubled.indexOf(c)
                if (index == -1) {
                    append(c)
                } else {
                    var offset = index - shift
                    if (offset < 0) offset += doubled.length
                    append(doubled[offset])
                }
            }
        }
    }

    private fun shuffle(s: String, seed: Long): String {
        val length = s.length
        if (length == 0) return s
        val chars = s.toCharArray()
        val permutation = IntArray(length)
        var e = abs(seed)
        for (i in length - 1 downTo 0) {
            e = ((length.toLong() * (i + 1)) xor (e + i)) % length
            permutation[i] = e.toInt()
        }
        for (i in 1 until length) {
            val j = permutation[length - 1 - i]
            val tmp = chars[i]
            chars[i] = chars[j]
            chars[j] = tmp
        }
        return String(chars)
    }

    private fun xor(s: String, key: String): String {
        val k = key[0].code
        return buildString { for (c in s) append((c.code xor k).toChar()) }
    }
}
