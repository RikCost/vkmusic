package com.vkmusic.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Track(
    val ownerId: Long,
    val audioId: Long,
    val title: String,
    val artist: String,
    val durationSec: Int,
    val url: String?,
    val coverUrl: String? = null,
    val isHls: Boolean = false,
    val albumTitle: String? = null,
    val isExplicit: Boolean = false,
    val hasLyrics: Boolean = false,
    val artistId: String? = null,
    val accessKey: String? = null,
    // Хеши веб-VK «add/edit/action/delete/replace/url» (индекс 13 аудио-массива al_audio.php):
    // по ним ссылка получается лениво через reload_audios, а трек добавляется и удаляется.
    val webHashes: String? = null,
) {
    val id: String get() = "${ownerId}_$audioId"

    val matchKey: String get() = "${artist.trim().lowercase()}|${title.trim().lowercase()}|$durationSec"

    val shareUrl: String get() = "https://vk.com/audio$id"

    /** Можно ставить в очередь: ссылка уже есть или её можно получить по хешам / access_key (reload_audios). */
    val isPlayable: Boolean get() = url != null || webHashes != null || accessKey != null

    val needsUrlResolution: Boolean get() = url == null && (webHashes != null || accessKey != null)

    fun webHash(index: Int): String = webHashes?.split('/')?.getOrNull(index).orEmpty()
}
