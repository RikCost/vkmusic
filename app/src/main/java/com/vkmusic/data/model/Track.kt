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
) {
    val id: String get() = "${ownerId}_$audioId"

    val matchKey: String get() = "${artist.trim().lowercase()}|${title.trim().lowercase()}|$durationSec"

    val shareUrl: String get() = "https://vk.com/audio$id"
}
