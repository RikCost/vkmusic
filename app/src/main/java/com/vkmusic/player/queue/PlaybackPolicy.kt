@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.vkmusic.player.queue

import androidx.media3.datasource.HttpDataSource
import com.vkmusic.data.model.Track
import kotlin.random.Random

// Столько раз подряд запрашивается ссылка трека, прежде чем он считается неиграбельным.
internal const val MAX_RESOLVE_ATTEMPTS = 3

// Столько неиграбельных треков подряд пропускается само. Дальше, скорее всего, нет сети: без предела
// сервис пролистал бы всю очередь; ошибка остаётся, пока что-нибудь не заиграет.
internal const val MAX_CONSECUTIVE_SKIPS = 5

/** Что делать, когда плеер встал на заглушке текущего трека (ссылку так и не получили). */
internal enum class UnresolvedAction { RETRY, SKIP, GIVE_UP }

internal fun unresolvedAction(attempts: Int, consecutiveSkips: Int, hasNext: Boolean): UnresolvedAction = when {
    attempts < MAX_RESOLVE_ATTEMPTS -> UnresolvedAction.RETRY
    hasNext && consecutiveSkips < MAX_CONSECUTIVE_SKIPS -> UnresolvedAction.SKIP
    else -> UnresolvedAction.GIVE_UP
}

/** Так CDN VK отвечает на истёкшую ссылку. */
internal fun isExpiredLinkResponse(code: Int): Boolean = code == 403 || code == 404 || code == 410

/** HTTP-код ответа, из-за которого упало воспроизведение; null — ошибка не HTTP (формат, сеть, декодер). */
internal fun Throwable.httpResponseCode(): Int? =
    generateSequence(this) { it.cause }.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()?.responseCode

/** Ссылку можно получить заново — как [Track.needsUrlResolution], но для трека, у которого она уже есть. */
internal val Track.canRefreshUrl: Boolean get() = url != null && (webHashes != null || accessKey != null)

/**
 * Треки сохранённой очереди перед восстановлением. Ссылки VK истекают: у веб-треков ссылка сбрасывается
 * (получат свежую по хешам), остальные обновит getByIds. Скачанные ([downloaded], по id) играют из кэша —
 * им ставится ссылка загрузки: по ней HLS находит свои сегменты, и трек играет без сети.
 */
internal fun restoredTracks(saved: List<Track>, downloaded: Map<String, Track>): List<Track> = saved.map { track ->
    val download = downloaded[track.id]
    when {
        download?.url != null -> track.copy(url = download.url, isHls = download.isHls)
        track.webHashes != null -> track.copy(url = null)
        else -> track
    }
}

/**
 * Запасные стартовые треки, если ссылку выбранного получить не удалось: следующие по порядку,
 * при перемешивании — случайные.
 */
internal fun fallbackStarts(size: Int, start: Int, shuffle: Boolean, count: Int, random: Random = Random.Default): List<Int> =
    if (shuffle) (0 until size).filter { it != start }.shuffled(random).take(count) else ((start + 1) until size).take(count)
