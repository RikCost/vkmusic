package com.vkmusic.data.repository

import com.vkmusic.data.api.VkAudioRepository
import com.vkmusic.data.model.Playlist
import com.vkmusic.data.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    private val api: VkAudioRepository,
) {
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _albums = MutableStateFlow<List<Playlist>>(emptyList())
    val albums: StateFlow<List<Playlist>> = _albums.asStateFlow()

    suspend fun refresh() {
        _playlists.value = api.getMyPlaylists()
    }

    suspend fun refreshAlbums() {
        _albums.value = api.getMyAlbums()
    }

    fun clear() {
        _playlists.value = emptyList()
        _albums.value = emptyList()
    }

    suspend fun details(ownerId: Long, playlistId: Long, accessKey: String?): Playlist =
        api.getPlaylist(ownerId, playlistId, accessKey)

    suspend fun tracks(ownerId: Long, playlistId: Long, accessKey: String?): List<Track> =
        api.getPlaylistTracks(ownerId, playlistId, accessKey)

    suspend fun create(title: String, tracks: List<Track> = emptyList()): Playlist {
        val playlist = api.createPlaylist(title)
        if (tracks.isNotEmpty()) api.addToPlaylist(playlist, tracks)
        refresh()
        return playlist
    }

    suspend fun addTracks(playlist: Playlist, tracks: List<Track>) {
        api.addToPlaylist(playlist, tracks)
        refresh()
    }

    suspend fun edit(playlist: Playlist, title: String, description: String) {
        api.editPlaylist(playlist, title, description)
        refresh()
    }

    suspend fun follow(playlist: Playlist) {
        api.followPlaylist(playlist)
        refresh()
        if (playlist.isAlbum) runCatching { refreshAlbums() }
    }

    /** Удаляет свой плейлист или отписывается от чужого — у VK это одно действие над копией в «моих». */
    suspend fun remove(playlist: Playlist) {
        val own = (_playlists.value + _albums.value).firstOrNull { it.id == playlist.id } ?: playlist
        api.deletePlaylist(own)
        refresh()
        if (playlist.isAlbum || _albums.value.any { it.id == playlist.id }) runCatching { refreshAlbums() }
    }
}
