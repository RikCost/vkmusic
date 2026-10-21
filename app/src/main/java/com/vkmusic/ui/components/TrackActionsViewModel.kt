package com.vkmusic.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vkmusic.data.api.VkAudioRepository
import com.vkmusic.data.model.Playlist
import com.vkmusic.data.model.Track
import com.vkmusic.data.export.ExportStatus
import com.vkmusic.data.export.Mp3ExportRepository
import com.vkmusic.data.repository.DownloadRepository
import com.vkmusic.data.repository.DownloadStatus
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
    private val api: VkAudioRepository,
    private val trackRepository: TrackRepository,
    private val playlistRepository: PlaylistRepository,
    private val downloadRepository: DownloadRepository,
    private val playerConnection: PlayerConnection,
    private val mp3ExportRepository: Mp3ExportRepository,
) : ViewModel() {

    val exports: StateFlow<Map<String, ExportStatus>> = mp3ExportRepository.statuses

    fun cancelExport(track: Track) = mp3ExportRepository.cancel(track)

    /** Сохранить треки файлами в папку из настроек; [folder] — подпапка (название плейлиста). */
    fun exportMp3(tracks: List<Track>, folder: String? = null) {
        val playable = tracks.filter { it.isPlayable }
        if (playable.isEmpty()) {
            _messages.trySend("Этот трек нельзя сохранить")
            return
        }
        mp3ExportRepository.enqueue(playable, folder)
        _messages.trySend(if (playable.size == 1) "Сохранение в MP3 начато" else "Сохранение ${playable.size} треков в MP3 начато")
    }


    val player = playerConnection.player

    val library: StateFlow<LibraryIndex> = trackRepository.libraryIndex
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryIndex())

    val downloads: StateFlow<Map<String, TrackDownload>> = downloadRepository.downloads

    val myPlaylists: StateFlow<List<Playlist>> = playlistRepository.playlists

    val editablePlaylists: StateFlow<List<Playlist>> = playlistRepository.playlists
        .map { playlists -> playlists.filter { it.isEditable } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun loadPlaylists() = launchReporting(null) { playlistRepository.refresh() }

    fun play(tracks: List<Track>, index: Int) = playerConnection.playQueue(withDownloads(tracks), index)

    fun shuffle(tracks: List<Track>) = playerConnection.playQueue(withDownloads(tracks), shuffle = true)

    fun playNext(track: Track) {
        val exact = playerConnection.playNext(withDownloads(listOf(track)).first())
        _messages.trySend(if (exact) "Будет воспроизведено следующим" else "Добавлено в очередь (включено перемешивание)")
    }

    // Meridius: «Микс по треку» — mix_id=track_mix, entity_id=<owner_id>_<id>, дальше подгружается порциями.
    fun playTrackMix(track: Track) = launchReporting(null) {
        val started = playerConnection.playEndless { api.getMix(TRACK_MIX, track.id, emptyMap(), MIX_PAGE) }
        if (!started) _messages.trySend("Не удалось загрузить треки микса")
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

    /** Треки, которые ещё нужно скачать: уже скачанные и стоящие в очереди пропускаются. */
    fun pendingDownloads(tracks: List<Track>): List<Track> = tracks.filter { track ->
        track.isPlayable && downloads.value[track.id]?.status.let { it == null || it == DownloadStatus.Failed }
    }

    /** Треки, которые ещё не сохранены в MP3 и не сохраняются сейчас. */
    fun pendingExports(tracks: List<Track>): List<Track> = tracks.filter { track ->
        track.isPlayable && exports.value[track.id].let { it == null || it is ExportStatus.Failed }
    }

    fun notify(message: String) {
        _messages.trySend(message)
    }

    fun cancelDownloads() {
        downloadRepository.cancelActive()
        _messages.trySend("Загрузки отменены")
    }

    // Скачанный трек играет по сохранённой ссылке из кэша загрузок — без сети и без запроса reload_audios.
    private fun withDownloads(tracks: List<Track>): List<Track> {
        val completed = downloads.value
        return tracks.map { track ->
            completed[track.id]?.takeIf { it.status == DownloadStatus.Completed }?.track?.takeIf { it.url != null } ?: track
        }
    }

    private fun launchReporting(success: String?, block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { success?.let(_messages::trySend) }
                .onFailure { _messages.trySend(it.message ?: "Ошибка") }
        }
    }

    private companion object {
        const val TRACK_MIX = "track_mix"
        const val MIX_PAGE = 10
    }
}
