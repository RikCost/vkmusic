package com.vkmusic.data.export

import com.vkmusic.data.model.Track
import com.vkmusic.data.repository.TrackUrlResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Сохранение трека файлом — порт meridius-core `hls.download()`: прямой .mp3 скачивается как есть
 * (`downloadMP3`), HLS собирается из сегментов с расшифровкой AES-128 (`downloadM3U8`). Вместо FFmpeg
 * аудио вынимается из MPEG-TS без перекодирования ([TsAudioExtractor]), теги — [Id3Writer].
 */
@Singleton
class Mp3Exporter @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val trackUrlResolver: TrackUrlResolver,
    private val storage: ExportStorage,
) {
    /** @return где лежит файл. */
    suspend fun export(track: Track, subfolder: String?, onProgress: suspend (Int) -> Unit): String {
        val url = freshUrl(track) ?: throw IOException("VK не отдал ссылку на трек")
        val (audio, codec) = if (url.substringBefore('?').endsWith(".m3u8")) {
            downloadHls(url, onProgress)
        } else {
            downloadBytes(url, onProgress) to AudioCodec.Mp3
        }
        if (audio.isEmpty()) throw IOException("Пустой аудиопоток")
        val data = if (codec == AudioCodec.Mp3) {
            val cover = track.coverUrl?.let { runCatching { downloadBytes(it) {} }.getOrNull() }
            Id3Writer.tag(track.title, track.artist, track.albumTitle, cover) + audio
        } else {
            audio
        }
        val name = ExportStorage.sanitize("${track.artist} - ${track.title}").ifBlank { track.id } + "." + codec.extension
        return storage.save(subfolder, name, codec.mimeType, data)
    }

    // Ссылки VK протухают, а у веб-треков в списках их нет вовсе — берём свежую перед скачиванием.
    private suspend fun freshUrl(track: Track): String? {
        val candidate = if (track.webHashes != null || track.accessKey != null) track.copy(url = null) else track
        return trackUrlResolver.resolve(listOf(candidate)).first().url ?: track.url
    }

    private suspend fun downloadHls(playlistUrl: String, onProgress: suspend (Int) -> Unit): Pair<ByteArray, AudioCodec> {
        var url = playlistUrl
        var text = downloadText(url)
        // Мастер-плейлист: берём первый вариант потока.
        if (text.contains("#EXT-X-STREAM-INF")) {
            val variant = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
                ?: throw IOException("Пустой мастер-плейлист")
            url = HlsPlaylist.parse("#EXTINF:0,\n$variant", url).first().url
            text = downloadText(url)
        }
        val segments = HlsPlaylist.parse(text, url)
        if (segments.isEmpty()) throw IOException("В плейлисте трека нет сегментов")

        val keys = mutableMapOf<String, ByteArray>()
        segments.mapNotNull { it.key?.uri }.distinct().forEach { keys[it] = downloadBytes(it) {} }

        val extractor = TsAudioExtractor()
        val limit = Semaphore(SEGMENT_CONCURRENCY)
        var done = 0
        // Сегменты качаются параллельно (как concurrency в Meridius), но в TS-разбор идут по порядку.
        coroutineScope {
            segments.chunked(SEGMENT_BATCH).forEach { batch ->
                val bodies = batch.map { segment ->
                    async { limit.withPermit { decrypt(downloadBytes(segment.url) {}, segment, keys) } }
                }.awaitAll()
                bodies.forEach(extractor::feed)
                done += batch.size
                onProgress(done * 100 / segments.size)
            }
        }
        return extractor.result()
    }

    /**
     * IV по спецификации HLS — атрибут IV или номер сегмента. Если результат не похож на MPEG-TS,
     * пробуем приём Meridius: IV = первые 16 байт шифротекста (теряется только первый блок).
     */
    private fun decrypt(data: ByteArray, segment: HlsSegment, keys: Map<String, ByteArray>): ByteArray {
        val key = segment.key ?: return data
        if (key.method != "AES-128") throw IOException("Неизвестное шифрование сегмента: ${key.method}")
        val secret = keys[key.uri] ?: throw IOException("Нет ключа сегмента")
        val specIv = key.iv ?: ByteBuffer.allocate(16).putLong(8, segment.sequence).array()
        val primary = runCatching { aes(secret, specIv, data) }.getOrNull()
        if (primary != null && primary.isNotEmpty() && primary[0] == TS_SYNC) return primary
        return runCatching { aes(secret, data.copyOfRange(0, 16), data) }.getOrNull() ?: primary
            ?: throw IOException("Не удалось расшифровать сегмент")
    }

    private fun aes(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray =
        Cipher.getInstance("AES/CBC/PKCS5Padding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            doFinal(data)
        }

    private suspend fun downloadText(url: String): String = downloadBytes(url) {}.decodeToString()

    private suspend fun downloadBytes(url: String, onProgress: suspend (Int) -> Unit): ByteArray = withContext(Dispatchers.IO) {
        okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body
            val total = body.contentLength()
            val out = ByteArrayOutputStream(if (total > 0) total.toInt() else DEFAULT_BUFFER)
            body.byteStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER)
                var read = 0L
                var lastPercent = -1
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    read += n
                    if (total > 0) {
                        val percent = (read * 100 / total).toInt()
                        if (percent != lastPercent) {
                            lastPercent = percent
                            onProgress(percent)
                        }
                    }
                }
            }
            out.toByteArray()
        }
    }

    private companion object {
        const val TS_SYNC: Byte = 0x47
        const val SEGMENT_CONCURRENCY = 5
        const val SEGMENT_BATCH = 10
        const val DEFAULT_BUFFER = 64 * 1024
    }
}
