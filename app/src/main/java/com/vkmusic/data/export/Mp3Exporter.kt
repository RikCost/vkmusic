package com.vkmusic.data.export

import com.vkmusic.data.HttpStatusException
import kotlinx.coroutines.ensureActive
import com.vkmusic.data.model.Track
import com.vkmusic.data.repository.TrackUrlResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Сохранение трека файлом — порт meridius-core `hls.download()`: прямой .mp3 скачивается как есть,
 * HLS собирается из сегментов с расшифровкой AES-128. Вместо FFmpeg аудио вынимается из MPEG-TS без
 * перекодирования ([TsAudioExtractor]), теги — [Id3Writer]. Байты идут прямо в файл: трек не должен
 * жить в памяти целиком, иначе длинная запись стоит десятков мегабайт на пике.
 */
@Singleton
class Mp3Exporter @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val trackUrlResolver: TrackUrlResolver,
    private val storage: ExportStorage,
) {
    /** @return где лежит файл: адрес для воспроизведения и понятное человеку место. */
    suspend fun export(track: Track, subfolder: String?, onProgress: suspend (Int) -> Unit): SavedFile {
        val url = freshUrl(track) ?: throw IOException("VK не отдал ссылку на трек")
        return if (url.substringBefore('?').endsWith(".m3u8")) exportHls(track, url, subfolder, onProgress) else exportDirect(track, url, subfolder, onProgress)
    }

    private suspend fun exportDirect(track: Track, url: String, subfolder: String?, onProgress: suspend (Int) -> Unit): SavedFile {
        val tag = id3Tag(track, AudioCodec.Mp3)
        return storage.save(subfolder, fileName(track, AudioCodec.Mp3), AudioCodec.Mp3.mimeType) { out ->
            out.write(tag)
            if (downloadTo(url, out, onProgress) == 0L) throw IOException("Пустой аудиопоток")
        }
    }

    private suspend fun exportHls(track: Track, playlistUrl: String, subfolder: String?, onProgress: suspend (Int) -> Unit): SavedFile {
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
        // Первый сегмент разбирается в буфер: по нему становится ясен кодек, а имя и mime файла нужны
        // до его создания. Дальше кадры пишутся прямо в файл.
        extractor.feed(decrypt(downloadBytes(segments.first().url) {}, segments.first(), keys))
        val codec = extractor.codec()
        val tag = id3Tag(track, codec)
        return storage.save(subfolder, fileName(track, codec), codec.mimeType) { out ->
            out.write(tag)
            extractor.drainTo(out)
            streamSegments(segments.drop(1), keys, extractor, segments.size, onProgress)
            if (extractor.emittedBytes == 0L) throw IOException("Пустой аудиопоток")
        }
    }

    /** Сегменты качаются параллельно (как concurrency в Meridius), но в TS-разбор идут по порядку. */
    private suspend fun streamSegments(
        remaining: List<HlsSegment>,
        keys: Map<String, ByteArray>,
        extractor: TsAudioExtractor,
        total: Int,
        onProgress: suspend (Int) -> Unit,
    ) {
        var done = total - remaining.size
        val limit = Semaphore(SEGMENT_CONCURRENCY)
        coroutineScope {
            remaining.chunked(SEGMENT_BATCH).forEach { batch ->
                val bodies = batch.map { segment ->
                    async { limit.withPermit { decrypt(downloadBytes(segment.url) {}, segment, keys) } }
                }.awaitAll()
                bodies.forEach(extractor::feed)
                done += batch.size
                onProgress(done * 100 / total)
            }
        }
        onProgress(100)
    }

    // Ссылки VK протухают, а у веб-треков в списках их нет вовсе — берём свежую перед скачиванием.
    // reload_audios на частые запросы отвечает пусто (Meridius повторяет его на no_audios) — повторяем с паузой.
    private suspend fun freshUrl(track: Track): String? {
        val candidate = if (track.webHashes != null || track.accessKey != null) track.copy(url = null) else track
        RESOLVE_DELAYS_MS.forEach { pause ->
            delay(pause)
            trackUrlResolver.resolve(listOf(candidate)).first().url?.let { return it }
        }
        return track.url
    }

    // ID3v2 читается только плеерами MP3; ADTS-поток AAC с приклеенным тегом перестал бы декодироваться.
    private suspend fun id3Tag(track: Track, codec: AudioCodec): ByteArray =
        if (codec == AudioCodec.Mp3) {
            Id3Writer.tag(track.title, track.artist, track.albumTitle, track.coverUrl?.let { runCatching { downloadBytes(it) {} }.getOrNull() })
        } else {
            ByteArray(0)
        }

    private fun fileName(track: Track, codec: AudioCodec): String =
        ExportStorage.sanitize("${track.artist} - ${track.title}").ifBlank { track.id } + "." + codec.extension

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

    /** Небольшие тела (плейлист, ключ, обложка) — целиком; звук идёт в [downloadTo]. */
    private suspend fun downloadBytes(url: String, onProgress: suspend (Int) -> Unit): ByteArray =
        ByteArrayOutputStream().use { out ->
            downloadTo(url, out, onProgress)
            out.toByteArray()
        }

    /** @return сколько байт скопировано. */
    private suspend fun downloadTo(url: String, out: OutputStream, onProgress: suspend (Int) -> Unit): Long = withContext(Dispatchers.IO) {
        okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code)
            val body = response.body
            val total = body.contentLength()
            var read = 0L
            var lastPercent = -1
            body.byteStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER)
                while (true) {
                    // Отмена задачи (WorkManager, кнопка «Отменить») не ждёт конца скачивания.
                    ensureActive()
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
            read
        }
    }

    private companion object {
        const val TS_SYNC: Byte = 0x47
        const val SEGMENT_CONCURRENCY = 5
        const val SEGMENT_BATCH = 10
        const val DEFAULT_BUFFER = 64 * 1024
        val RESOLVE_DELAYS_MS = listOf(0L, 3_000L, 8_000L)
    }
}
