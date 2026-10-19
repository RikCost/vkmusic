package com.vkmusic.ui.screens.friends

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vkmusic.data.api.VkAudioRepository
import com.vkmusic.data.model.Friend
import com.vkmusic.data.model.Track
import com.vkmusic.ui.components.LoadState
import com.vkmusic.ui.components.TrackSourceViewModel
import com.vkmusic.ui.navigation.FriendRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val api: VkAudioRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoadState(emptyList<Friend>(), isLoading = true))
    val state: StateFlow<LoadState<List<Friend>>> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { api.getFriends() }
                .onSuccess { _state.value = LoadState(it) }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message ?: "Ошибка загрузки") } }
        }
    }
}

@HiltViewModel
class FriendTracksViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val api: VkAudioRepository,
) : TrackSourceViewModel() {

    val route = savedStateHandle.toRoute<FriendRoute>()

    init {
        refresh()
    }

    override suspend fun load(): List<Track> = api.getUserAudio(route.ownerId)
}
