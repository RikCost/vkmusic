package com.vkmusic.data.repository

import kotlinx.coroutines.CancellationException
import com.vkmusic.data.api.VkAudioRepository
import com.vkmusic.data.model.Playlist
import com.vkmusic.data.model.PlaylistChoice
import com.vkmusic.data.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

    suspend fun choicesFor(track: Track): List<PlaylistChoice> = api.getPlaylistsForTrack(track)

    suspend fun tracks(ownerId: Long, playlistId: Long, accessKey: String?): List<Track> =
        api.getPlaylistTracks(ownerId, playlistId, accessKey)

    // Новый плейлист и изменения состава отражаются в списке сразу: перезагрузка всех плейлистов
    // ради одного счётчика стоила секунды ожидания после каждого нажатия.
    suspend fun create(title: String, tracks: List<Track> = emptyList()): Playlist {
        val playlist = api.createPlaylist(title)
        // Плейлист уже есть в VK — в списке он появляется сразу, даже если добавление треков не удастся.
        _playlists.update { listOf(playlist.copy(trackCount = 0, isEditable = true)) + it.filterNot { p -> p.id == playlist.id } }
        if (tracks.isNotEmpty()) addTracks(playlist, tracks)
        return playlist
    }

    suspend fun addTracks(playlist: Playlist, tracks: List<Track>) {
        api.addToPlaylist(playlist, tracks)
        changeCount(playlist, tracks.size)
    }

    suspend fun removeTracks(playlist: Playlist, tracks: List<Track>) {
        api.removeFromPlaylist(playlist, tracks)
        changeCount(playlist, -tracks.size)
    }

    private fun changeCount(playlist: Playlist, delta: Int) {
        _playlists.update { list ->
            list.map { if (it.id == playlist.id) it.copy(trackCount = (it.trackCount + delta).coerceAtLeast(0)) else it }
        }
    }

    suspend fun edit(playlist: Playlist, title: String, description: String) {
        api.editPlaylist(playlist, title, description)
        _playlists.update { list -> list.map { if (it.id == playlist.id) it.copy(title = title, description = description) else it } }
        refreshQuietly()
    }

    suspend fun follow(playlist: Playlist) {
        api.followPlaylist(playlist)
        refreshQuietly()
        if (playlist.isAlbum) runCatching { refreshAlbums() }
    }

    // Действие в VK уже выполнено: сбой последующей перезагрузки списка — не ошибка для пользователя.
    private suspend fun refreshQuietly() {
        runCatching { refresh() }.exceptionOrNull()?.let { if (it is CancellationException) throw it }
    }

    /** Удаляет свой плейлист или отписывается от чужого — у VK это одно действие над копией в «моих». */
    suspend fun remove(playlist: Playlist) {
        val own = (_playlists.value + _albums.value).firstOrNull { it.id == playlist.id } ?: playlist
        val wasAlbum = playlist.isAlbum || _albums.value.any { it.id == playlist.id }
        api.deletePlaylist(own)
        _playlists.update { list -> list.filterNot { it.id == playlist.id } }
        _albums.update { list -> list.filterNot { it.id == playlist.id } }
        refreshQuietly()
        if (wasAlbum) runCatching { refreshAlbums() }
    }
}
