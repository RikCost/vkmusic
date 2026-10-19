package com.vkmusic.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vkmusic.data.model.Playlist
import com.vkmusic.data.model.Track
import com.vkmusic.data.repository.DownloadRepository
import com.vkmusic.data.repository.LibraryIndex
import com.vkmusic.data.repository.PlaylistRepository
import com.vkmusic.data.repository.TrackDownload
import com.vkmusic.data.repository.TrackRepository
import com.vkmusic.player.queue.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrackActionsViewModel @Inject constructor(
    private val trackRepository: TrackRepository,
    private val playlistRepository: PlaylistRepository,
    private val downloadRepository: DownloadRepository,
    private val playerConnection: PlayerConnection,
) : ViewModel() {

    val player = playerConnection.player

    fun trackFor(mediaId: String?): Track? = playerConnection.trackFor(mediaId)

    val library: StateFlow<LibraryIndex> = trackRepository.libraryIndex
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryIndex())

    val downloads: StateFlow<Map<String, TrackDownload>> = downloadRepository.downloads

    val editablePlaylists: StateFlow<List<Playlist>> = playlistRepository.playlists
        .map { playlists -> playlists.filter { it.isEditable } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun loadPlaylists() = launchReporting(null) { playlistRepository.refresh() }

    fun play(tracks: List<Track>, index: Int) = playerConnection.playQueue(tracks, index)

    fun shuffle(tracks: List<Track>) = playerConnection.playQueue(tracks, shuffle = true)

    fun playNext(track: Track) {
        val exact = playerConnection.playNext(track)
        _messages.trySend(if (exact) "Будет воспроизведено следующим" else "Добавлено в очередь (включено перемешивание)")
    }

    fun addToLibrary(track: Track) = launchReporting("Добавлено в мою музыку") { trackRepository.addToLibrary(track) }

    fun removeFromLibrary(track: Track) = launchReporting("Удалено из моей музыки") { trackRepository.removeFromLibrary(track) }

    fun addToPlaylist(playlist: Playlist, tracks: List<Track>) =
        launchReporting("Добавлено в «${playlist.title}»") { playlistRepository.addTracks(playlist, tracks) }

    fun createPlaylist(title: String, tracks: List<Track>) =
        launchReporting("Плейлист «$title» создан") { playlistRepository.create(title, tracks) }

    fun download(tracks: List<Track>) {
        downloadRepository.download(tracks)
        _messages.trySend(if (tracks.size == 1) "Загрузка начата" else "Загрузка ${tracks.size} треков начата")
    }

    fun removeDownload(track: Track) = downloadRepository.remove(track)

    private fun launchReporting(success: String?, block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { success?.let(_messages::trySend) }
                .onFailure { _messages.trySend(it.message ?: "Ошибка") }
        }
    }
}
