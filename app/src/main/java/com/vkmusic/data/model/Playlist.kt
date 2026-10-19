package com.vkmusic.data.model

data class Playlist(
    val ownerId: Long,
    val playlistId: Long,
    val title: String,
    val subtitle: String = "",
    val coverUrl: String? = null,
    val trackCount: Int = 0,
    val isAlbum: Boolean = false,
    val isEditable: Boolean = false,
    val accessKey: String? = null,
) {
    val id: String get() = "${ownerId}_$playlistId"
}
