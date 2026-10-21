package com.vkmusic.ui.screens.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vkmusic.data.model.Playlist
import com.vkmusic.ui.components.AccentTabs
import com.vkmusic.ui.components.Artwork
import com.vkmusic.ui.components.CreatePlaylistDialog
import com.vkmusic.ui.components.FilterField
import com.vkmusic.ui.components.LoadingBox
import com.vkmusic.ui.components.MessageBox
import com.vkmusic.ui.components.PageTopBar
import com.vkmusic.ui.components.SectionTitle
import com.vkmusic.ui.components.TrackSourcePage
import com.vkmusic.ui.navigation.LocalNavigator
import com.vkmusic.ui.screens.bottomOnly

private val TABS = listOf("Музыка", "Плейлисты", "Загрузки")

@Composable
fun MusicScreen(contentPadding: PaddingValues) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var creating by rememberSaveable { mutableStateOf(false) }
    val playlistsViewModel: PlaylistsViewModel = hiltViewModel()

    Column(Modifier.fillMaxSize()) {
        PageTopBar("Музыка") {
            if (tab == 1) {
                IconButton(onClick = { creating = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = "Создать плейлист")
                }
            }
        }
        AccentTabs(TABS, tab) { tab = it }
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> MyTracksTab(contentPadding)
                1 -> PlaylistsTab(contentPadding, playlistsViewModel)
                else -> DownloadsTab(contentPadding)
            }
        }
    }

    if (creating) {
        CreatePlaylistDialog(
            onCreate = {
                playlistsViewModel.create(it)
                creating = false
            },
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun MyTracksTab(contentPadding: PaddingValues, viewModel: TracksViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("") }
    TrackSourcePage(
        state = state,
        onRefresh = viewModel::refresh,
        contentPadding = contentPadding.bottomOnly(),
        emptyText = "В вашей музыке пока нет треков",
        filter = filter,
        header = { item { FilterField(filter, { filter = it }) } },
    )
}

@Composable
private fun DownloadsTab(contentPadding: PaddingValues, viewModel: DownloadsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("") }
    TrackSourcePage(
        state = state,
        onRefresh = {},
        contentPadding = contentPadding.bottomOnly(),
        emptyText = "Скачанных треков нет. Скачать можно из меню трека или целиком плейлист и альбом.",
        filter = filter,
        header = { if (state.data.isNotEmpty()) item { FilterField(filter, { filter = it }) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistsTab(contentPadding: PaddingValues, viewModel: PlaylistsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val (albums, playlists) = state.data.partition { it.isAlbum }

    PullToRefreshBox(
        isRefreshing = state.isLoading && state.data.isNotEmpty(),
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding.bottomOnly()) {
            when {
                state.error != null -> item { MessageBox(state.error.orEmpty()) }
                state.isLoading && state.data.isEmpty() -> item { LoadingBox() }
                state.data.isEmpty() -> item { MessageBox("Плейлистов пока нет") }
            }
            if (playlists.isNotEmpty()) {
                item { SectionTitle("Ваши плейлисты") }
                items(playlists, key = { it.id }) { PlaylistRow(it, navigator::openPlaylist) }
            }
            if (albums.isNotEmpty()) {
                item { SectionTitle("Альбомы") }
                items(albums, key = { it.id }) { PlaylistRow(it, navigator::openPlaylist) }
            }
        }
    }
}

@Composable
fun PlaylistRow(playlist: Playlist, onOpen: (Playlist) -> Unit) {
    ListItem(
        headlineContent = { Text(playlist.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall) },
        supportingContent = {
            Text(
                listOfNotNull(playlist.subtitle.takeIf { it.isNotBlank() }, "${playlist.trackCount} треков").joinToString(" • "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            Box(Modifier.padding(vertical = 4.dp)) {
                Artwork(playlist.coverUrl, playlist.id, Modifier.size(56.dp), cornerRadius = 4.dp)
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.clickable { onOpen(playlist) },
    )
}
