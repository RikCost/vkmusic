package com.vkmusic.ui.screens.artist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vkmusic.data.api.VkAudioRepository
import com.vkmusic.data.model.Playlist
import com.vkmusic.data.model.Track
import com.vkmusic.ui.components.AccentTabs
import com.vkmusic.ui.components.BackTopBar
import com.vkmusic.ui.components.LoadState
import com.vkmusic.ui.components.LoadingBox
import com.vkmusic.ui.components.MessageBox
import com.vkmusic.ui.components.TrackSourcePage
import com.vkmusic.ui.components.TrackSourceViewModel
import com.vkmusic.ui.navigation.ArtistRoute
import com.vkmusic.ui.navigation.LocalNavigator
import com.vkmusic.ui.screens.bottomOnly
import com.vkmusic.ui.screens.music.PlaylistRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArtistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val api: VkAudioRepository,
) : TrackSourceViewModel() {

    val route = savedStateHandle.toRoute<ArtistRoute>()

    private val _albums = MutableStateFlow(LoadState(emptyList<Playlist>(), isLoading = true))
    val albums: StateFlow<LoadState<List<Playlist>>> = _albums.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            _albums.value = runCatching { api.getArtistAlbums(route.artistId) }
                .fold({ LoadState(it) }, { LoadState(emptyList(), error = it.message ?: "Альбомы недоступны") })
        }
    }

    override suspend fun load(): List<Track> = api.getArtistTracks(route.artistId)
}

/** Страница исполнителя Meridius (ARTIST_PAGE): «Популярное» и плейлисты/альбомы. */
@Composable
fun ArtistScreen(contentPadding: PaddingValues, viewModel: ArtistViewModel = hiltViewModel()) {
    val navigator = LocalNavigator.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        BackTopBar(title = viewModel.route.name, subtitle = "Исполнитель", onBack = navigator::back)
        AccentTabs(listOf("Популярное", "Альбомы"), tab) { tab = it }
        Box(Modifier.weight(1f)) {
            if (tab == 0) {
                TrackSourcePage(
                    state = state,
                    onRefresh = viewModel::refresh,
                    contentPadding = contentPadding.bottomOnly(),
                    emptyText = "У исполнителя пока нет треков",
                )
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding.bottomOnly()) {
                    when {
                        albums.error != null -> item { MessageBox(albums.error.orEmpty()) }
                        albums.isLoading -> item { LoadingBox() }
                        albums.data.isEmpty() -> item { MessageBox("Альбомов нет") }
                    }
                    items(albums.data, key = { it.id }) { PlaylistRow(it, navigator::openPlaylist) }
                }
            }
        }
    }
}
