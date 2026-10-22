package com.vkmusic.ui.screens.playlists

import com.vkmusic.ui.AppMessages
import com.vkmusic.data.toUserMessage
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vkmusic.data.model.Playlist
import com.vkmusic.data.model.Track
import com.vkmusic.data.repository.PlaylistRepository
import com.vkmusic.ui.components.TrackSourceViewModel
import com.vkmusic.ui.navigation.PlaylistRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PlaylistEvent {
    data class Message(val text: String) : PlaylistEvent
    data object Closed : PlaylistEvent
}

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playlistRepository: PlaylistRepository,
    private val appMessages: AppMessages,
) : TrackSourceViewModel() {

    val route = savedStateHandle.toRoute<PlaylistRoute>()

    private val _details = MutableStateFlow(Playlist(route.ownerId, route.playlistId, route.title, accessKey = route.accessKey))
    val details: StateFlow<Playlist> = _details.asStateFlow()

    /** Плейлист в «моих»: свой или с подпиской. */
    val libraryCopy: StateFlow<Playlist?> = playlistRepository.playlists
        .map { list -> list.firstOrNull { it.ownerId == route.ownerId && it.playlistId == route.playlistId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _events = Channel<PlaylistEvent>(Channel.BUFFERED)
    val events: Flow<PlaylistEvent> = _events.receiveAsFlow()

    init {
        refresh()
        loadDetails()
        if (playlistRepository.playlists.value.isEmpty()) launchReporting(null) { playlistRepository.refresh() }
        playlistRepository.playlists
            .map { list -> list.firstOrNull { it.ownerId == route.ownerId && it.playlistId == route.playlistId }?.trackCount }
            .distinctUntilChanged()
            .drop(1)
            .onEach { refresh() }
            .launchIn(viewModelScope)
    }

    override suspend fun load(): List<Track> = playlistRepository.tracks(route.ownerId, route.playlistId, route.accessKey)

    // Пока описание не загружено, редактировать нельзя: в диалоге оказалось бы пустое описание,
    // и «Сохранить» стёрло бы настоящее в VK.
    private var detailsLoaded = false

    private fun loadDetails() {
        viewModelScope.launch {
            runCatching { playlistRepository.details(route.ownerId, route.playlistId, route.accessKey) }
                .onSuccess {
                    _details.value = it
                    detailsLoaded = true
                }
        }
    }

    /** @return можно ли открыть редактирование; если нет — догружает описание и объясняет почему. */
    fun requestEdit(): Boolean {
        if (detailsLoaded) return true
        loadDetails()
        viewModelScope.launch { _events.send(PlaylistEvent.Message("Плейлист ещё загружается — попробуйте через секунду")) }
        return false
    }

    fun follow() = launchReporting("Вы подписались на плейлист") { playlistRepository.follow(_details.value) }

    fun unfollow() = launchReporting("Вы отписались от плейлиста") { playlistRepository.remove(_details.value) }

    fun delete() = launchReporting(null) {
        playlistRepository.remove(_details.value)
        // Экран закрывается сразу — сообщение покажет корневой экран, иначе оно пропадало вместе с этим.
        appMessages.show("Плейлист удалён")
        _events.send(PlaylistEvent.Closed)
    }

    fun edit(title: String, description: String) = launchReporting("Плейлист обновлён") {
        playlistRepository.edit(_details.value, title, description)
        _details.value = _details.value.copy(title = title, description = description)
    }

    private fun launchReporting(success: String?, block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { success?.let { _events.send(PlaylistEvent.Message(it)) } }
                .onFailure { _events.send(PlaylistEvent.Message(it.toUserMessage("Ошибка"))) }
        }
    }
}
