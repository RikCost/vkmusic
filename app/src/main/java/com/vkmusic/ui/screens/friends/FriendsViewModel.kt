package com.vkmusic.ui.screens.friends

import com.vkmusic.data.toUserMessage
import com.vkmusic.ui.formatTracks
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vkmusic.data.api.VkAudioRepository
import com.vkmusic.data.model.Playlist
import com.vkmusic.data.model.Track
import com.vkmusic.ui.components.LoadState
import com.vkmusic.ui.components.TrackSourceViewModel
import com.vkmusic.ui.navigation.OwnerRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Строка списка друзей или сообществ: у сообщества ownerId отрицательный. */
data class OwnerItem(val ownerId: Long, val name: String, val photoUrl: String?, val subtitle: String? = null)

abstract class OwnersViewModel : ViewModel() {
    private val _state = MutableStateFlow(LoadState(emptyList<OwnerItem>(), isLoading = true))
    val state: StateFlow<LoadState<List<OwnerItem>>> = _state.asStateFlow()

    protected abstract suspend fun load(): List<OwnerItem>

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { load() }
                .onSuccess { _state.value = LoadState(it) }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.toUserMessage("Ошибка загрузки")) } }
        }
    }
}

@HiltViewModel
class FriendsViewModel @Inject constructor(private val api: VkAudioRepository) : OwnersViewModel() {
    init {
        refresh()
    }

    override suspend fun load() = api.getFriends().map {
        OwnerItem(it.id, it.name, it.photoUrl, it.trackCount.takeIf { count -> count > 0 }?.let(::formatTracks))
    }
}

@HiltViewModel
class CommunitiesViewModel @Inject constructor(private val api: VkAudioRepository) : OwnersViewModel() {
    init {
        refresh()
    }

    override suspend fun load() = api.getCommunities().map { OwnerItem(it.ownerId, it.name, it.photoUrl) }
}

@HiltViewModel
class OwnerTracksViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val api: VkAudioRepository,
) : TrackSourceViewModel() {

    val route = savedStateHandle.toRoute<OwnerRoute>()

    private val _playlists = MutableStateFlow(LoadState(emptyList<Playlist>(), isLoading = true))
    val playlists: StateFlow<LoadState<List<Playlist>>> = _playlists.asStateFlow()

    init {
        refresh()
        refreshPlaylists()
    }

    // Вся музыка владельца, а не первые 100 треков: страницами, как «моя музыка».
    override suspend fun load(): List<Track> = buildList {
        while (size < MAX_TRACKS) {
            val page = api.getUserAudio(route.ownerId, offset = size, count = PAGE_SIZE)
            addAll(page)
            if (page.size < PAGE_SIZE) break
        }
    }.distinctBy { it.id }

    fun refreshPlaylists() {
        viewModelScope.launch {
            _playlists.update { it.copy(isLoading = true, error = null) }
            _playlists.value = runCatching { api.getUserPlaylists(route.ownerId) }
                .fold({ LoadState(it) }, { LoadState(emptyList(), error = it.toUserMessage("Плейлисты недоступны")) })
        }
    }
}

private const val PAGE_SIZE = 500
private const val MAX_TRACKS = 20_000
