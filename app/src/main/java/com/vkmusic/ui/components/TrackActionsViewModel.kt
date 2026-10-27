package com.vkmusic.ui.components

import com.vkmusic.ui.formatTracks
import com.vkmusic.data.toUserMessage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vkmusic.data.api.VkAudioRepository
import com.vkmusic.data.model.Playlist
import com.vkmusic.data.model.PlaylistChoice
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Окно «Добавить в плейлист»: [choices] = null, пока VK не ответил; [pending] — плейлисты, ждущие ответа VK. */
data class PlaylistPickerState(
    val track: Track,
    val choices: List<PlaylistChoice>? = null,
    val pending: Set<String> = emptySet(),
    val error: String? = null,
)

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
        _messages.trySend(if (playable.size == 1) "Сохранение в MP3 начато" else "Сохранение в MP3 начато: ${formatTracks(playable.size)}")
    }


    val player = playerConnection.player

    val library: StateFlow<LibraryIndex> = trackRepository.libraryIndex
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryIndex())

    val downloads: StateFlow<Map<String, TrackDownload>> = downloadRepository.downloads

    val myPlaylists: StateFlow<List<Playlist>> = playlistRepository.playlists

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

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

    private val _playlistPicker = MutableStateFlow<PlaylistPickerState?>(null)
    val playlistPicker: StateFlow<PlaylistPickerState?> = _playlistPicker.asStateFlow()

    fun openPlaylistPicker(track: Track) {
        _playlistPicker.value = PlaylistPickerState(track)
        viewModelScope.launch {
            val result = runCatching { playlistRepository.choicesFor(track) }
            _playlistPicker.update { state ->
                if (state?.track?.id != track.id) return@update state
                result.fold(
                    onSuccess = { state.copy(choices = it) },
                    onFailure = { state.copy(choices = emptyList(), error = it.toUserMessage("Не удалось загрузить плейлисты")) },
                )
            }
        }
    }

    fun closePlaylistPicker() {
        _playlistPicker.value = null
    }

    /** Галочка меняется сразу; если VK ответил ошибкой — возвращается обратно. */
    fun togglePlaylist(choice: PlaylistChoice) {
        val state = _playlistPicker.value ?: return
        val playlist = choice.playlist
        if (playlist.id in state.pending) return
        val track = state.track
        val add = !choice.containsTrack
        setChoice(track, playlist.id, add, pending = true)
        viewModelScope.launch {
            runCatching {
                if (add) playlistRepository.addTracks(playlist, listOf(track)) else playlistRepository.removeTracks(playlist, listOf(track))
            }.onSuccess {
                setChoice(track, playlist.id, add, pending = false)
                _messages.trySend(if (add) "Добавлено в «${playlist.title}»" else "Удалено из «${playlist.title}»")
            }.onFailure {
                setChoice(track, playlist.id, !add, pending = false)
                _messages.trySend(it.toUserMessage("Ошибка"))
            }
        }
    }

    private fun setChoice(track: Track, playlistId: String, contains: Boolean, pending: Boolean) {
        _playlistPicker.update { state ->
            if (state?.track?.id != track.id) return@update state
            state.copy(
                choices = state.choices?.map { choice ->
                    if (choice.playlist.id != playlistId || choice.containsTrack == contains) return@map choice
                    val count = (choice.playlist.trackCount + if (contains) 1 else -1).coerceAtLeast(0)
                    PlaylistChoice(choice.playlist.copy(trackCount = count), contains)
                },
                pending = if (pending) state.pending + playlistId else state.pending - playlistId,
            )
        }
    }

    fun createPlaylist(title: String, tracks: List<Track>) =
        launchReporting("Плейлист «$title» создан") { playlistRepository.create(title, tracks) }

    fun download(tracks: List<Track>) {
        downloadRepository.download(tracks)
        _messages.trySend(if (tracks.size == 1) "Загрузка начата" else "Загрузка начата: ${formatTracks(tracks.size)}")
    }

    fun removeDownload(track: Track) = downloadRepository.remove(track)

    /** Треки, которые ещё нужно скачать: уже скачанные и стоящие в очереди пропускаются. */
    fun pendingDownloads(tracks: List<Track>): List<Track> = tracks.filter { track ->
        track.isPlayable && downloads.value[track.id]?.status.let { it == null || it == DownloadStatus.Failed }
    }

    /** Треки, которые не сохраняются в MP3 прямо сейчас; повторное сохранение не блокируется. */
    fun pendingExports(tracks: List<Track>): List<Track> = tracks.filter { track ->
        val status = exports.value[track.id]
        track.isPlayable && status !is ExportStatus.Queued && status !is ExportStatus.Running
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
                .onFailure { _messages.trySend(it.toUserMessage("Ошибка")) }
        }
    }

    private companion object {
        const val TRACK_MIX = "track_mix"
        const val MIX_PAGE = 10
    }
}
