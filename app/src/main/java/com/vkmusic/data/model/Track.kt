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
) {
    val id: String get() = "${ownerId}_$audioId"

    val matchKey: String get() = "${artist.trim().lowercase()}|${title.trim().lowercase()}|$durationSec"
}
