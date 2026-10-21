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
    suspend fun resolve(tracks: List<Track>): List<Track> {
        val missing = tracks.filter { it.needsUrlResolution }
        if (missing.isEmpty()) return tracks
        val fresh = runCatching { api.getByIds(missing) }.getOrDefault(emptyList())
            .filter { it.url != null }
            .associateBy { it.id }
        return tracks.map { track ->
            val resolved = fresh[track.id] ?: return@map track
            if (track.url != null) track else track.copy(url = resolved.url, isHls = resolved.isHls, webHashes = resolved.webHashes ?: track.webHashes)
        }
    }
}
