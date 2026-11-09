package com.vkmusic.data.repository

import com.vkmusic.data.api.VkAudioRepository
import com.vkmusic.data.model.Track
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Веб-VK отдаёт списки без ссылок: ссылку выдаёт reload_audios по хешам трека. Получать их на весь
 * список заранее дорого, поэтому они запрашиваются перед воспроизведением и скачиванием.
 */
@Singleton
class TrackUrlResolver @Inject constructor(
    private val api: VkAudioRepository,
) {
    /** Те же треки в том же порядке; у кого получилось — со ссылкой. */
    suspend fun resolve(tracks: List<Track>): List<Track> = resolveChecked(tracks).tracks

    /**
     * Как [resolve], но отличает сбой запроса (нет сети, VK не ответил — стоит повторить) от ответа VK без
     * ссылки: такой трек недоступен, например закрыт правообладателем, и повтор не поможет.
     */
    suspend fun resolveChecked(tracks: List<Track>): UrlResolution {
        val missing = tracks.filter { it.needsUrlResolution }
        if (missing.isEmpty()) return UrlResolution(tracks, requestFailed = false)
        val fresh = runCatching { api.getByIds(missing) }
            .getOrElse { return UrlResolution(tracks, requestFailed = true) }
            .filter { it.url != null }
            .associateBy { it.id }
        val resolved = tracks.map { track ->
            val resolved = fresh[track.id] ?: return@map track
            if (track.url != null) track else track.copy(url = resolved.url, isHls = resolved.isHls, webHashes = resolved.webHashes ?: track.webHashes)
        }
        return UrlResolution(resolved, requestFailed = false)
    }
}

/** Итог [TrackUrlResolver.resolveChecked]: [requestFailed] — ссылки не получены из-за сбоя, а не потому что их нет. */
data class UrlResolution(val tracks: List<Track>, val requestFailed: Boolean) {
    /** Треки, у которых VK ссылку не дал, хотя ответил. */
    val unavailableIds: Set<String>
        get() = if (requestFailed) emptySet() else tracks.filter { it.url == null }.mapTo(HashSet()) { it.id }
}
