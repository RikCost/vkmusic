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
    val description: String = "",
    val plays: Long = 0,
    val updatedAt: Long = 0,
    // Подписка на чужой плейлист хранится у VK копией в «моих» плейлистах: удалять нужно именно её.
    val followCopyId: Long? = null,
) {
    val id: String get() = "${ownerId}_$playlistId"

    val shareUrl: String
        get() = "https://vk.com/music/playlist/${id}" + (accessKey?.let { "_$it" } ?: "")
}
