package com.vkmusic.ui.screens.music

import com.vkmusic.data.toUserMessage
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

    // Альбомы живут на своей вкладке: здесь только пользовательские плейлисты.
    val state: StateFlow<LoadState<List<Playlist>>> =
        combine(playlistRepository.playlists, playlistRepository.albums, loading, error) { playlists, albums, isLoading, error ->
            val albumIds = albums.mapTo(HashSet()) { it.id }
            LoadState(playlists.filter { !it.isAlbum && it.id !in albumIds }, isLoading, error)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState(emptyList(), isLoading = true))

    init {
        refresh()
    }

    fun refresh() = launchLoading { playlistRepository.refresh() }

    fun create(title: String) = launchLoading { playlistRepository.create(title) }

    private fun launchLoading(block: suspend () -> Unit) {
        viewModelScope.launch {
            loading.value = true
            error.value = runCatching { block() }.exceptionOrNull()?.toUserMessage()
            loading.value = false
        }
    }
}

@HiltViewModel
class AlbumsViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    private val loading = MutableStateFlow(true)
    private val error = MutableStateFlow<String?>(null)

    // Альбомы, уже найденные среди загруженных плейлистов, видны сразу — полный список догружается.
    val state: StateFlow<LoadState<List<Playlist>>> =
        combine(playlistRepository.albums, playlistRepository.playlists, loading, error) { albums, playlists, isLoading, error ->
            LoadState((albums + playlists.filter { it.isAlbum }).distinctBy { it.id }, isLoading, error)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState(emptyList(), isLoading = true))

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            loading.value = true
            error.value = runCatching { playlistRepository.refreshAlbums() }.exceptionOrNull()?.toUserMessage()
            loading.value = false
        }
    }
}
