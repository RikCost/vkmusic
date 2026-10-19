package com.vkmusic.ui.screens.playlists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vkmusic.data.model.Track
import com.vkmusic.data.repository.PlaylistRepository
import com.vkmusic.ui.components.TrackSourceViewModel
import com.vkmusic.ui.navigation.PlaylistRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playlistRepository: PlaylistRepository,
) : TrackSourceViewModel() {

    val route = savedStateHandle.toRoute<PlaylistRoute>()

    init {
        refresh()
        playlistRepository.playlists
            .map { list -> list.firstOrNull { it.ownerId == route.ownerId && it.playlistId == route.playlistId }?.trackCount }
            .distinctUntilChanged()
            .drop(1)
            .onEach { refresh() }
            .launchIn(viewModelScope)
    }

    override suspend fun load(): List<Track> = playlistRepository.tracks(route.ownerId, route.playlistId, route.accessKey)
}
