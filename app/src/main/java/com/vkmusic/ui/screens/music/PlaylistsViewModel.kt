package com.vkmusic.ui.screens.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vkmusic.data.model.Playlist
import com.vkmusic.data.repository.PlaylistRepository
import com.vkmusic.ui.components.LoadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    private val loading = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val state: StateFlow<LoadState<List<Playlist>>> =
        combine(playlistRepository.playlists, loading, error) { playlists, isLoading, error ->
            LoadState(playlists, isLoading, error)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState(emptyList(), isLoading = true))

    init {
        refresh()
    }

    fun refresh() = launchLoading { playlistRepository.refresh() }

    fun create(title: String) = launchLoading { playlistRepository.create(title) }

    private fun launchLoading(block: suspend () -> Unit) {
        viewModelScope.launch {
            loading.value = true
            error.value = runCatching { block() }.exceptionOrNull()?.message
            loading.value = false
        }
    }
}
