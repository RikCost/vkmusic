package com.vkmusic.ui.screens.music

import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CloudOff
import com.vkmusic.ui.components.ListSkeleton
import com.vkmusic.ui.formatTracks
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.vkmusic.ui.components.IconButton
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
import com.vkmusic.ui.components.TrackSourcePage
import com.vkmusic.ui.navigation.LocalNavigator
import com.vkmusic.ui.screens.bottomOnly

private val TABS = listOf("Музыка", "Плейлисты", "Альбомы", "Загрузки")

@Composable
fun MusicScreen(contentPadding: PaddingValues) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var creating by rememberSaveable { mutableStateOf(false) }
    val playlistsViewModel: PlaylistsViewModel = hiltViewModel()
    // Создаётся вместе с экраном, чтобы альбомы грузились заранее, а не после открытия их вкладки.
    val albumsViewModel: AlbumsViewModel = hiltViewModel()

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
                2 -> AlbumsTab(contentPadding, albumsViewModel)
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
        downloadsMode = true,
        emptyIcon = Icons.Rounded.DownloadForOffline,
        filter = filter,
        header = { if (state.data.isNotEmpty()) item { FilterField(filter, { filter = it }) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistsTab(contentPadding: PaddingValues, viewModel: PlaylistsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current

    PullToRefreshBox(
        isRefreshing = state.isLoading && state.data.isNotEmpty(),
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding.bottomOnly()) {
            when {
                state.error != null -> item { MessageBox(state.error.orEmpty(), icon = Icons.Rounded.CloudOff) }
                state.isLoading && state.data.isEmpty() -> item { ListSkeleton(artworkSize = 56.dp) }
                state.data.isEmpty() -> item { MessageBox("Плейлистов пока нет", icon = Icons.AutoMirrored.Rounded.QueueMusic) }
            }
            items(state.data, key = { it.id }) { PlaylistRow(it, navigator::openPlaylist) }
        }
    }
}

/** Альбомы исполнителей, добавленные к себе, — сеткой обложек, как блоки каталога Meridius. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumsTab(contentPadding: PaddingValues, viewModel: AlbumsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current

    PullToRefreshBox(
        isRefreshing = state.isLoading && state.data.isNotEmpty(),
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(ALBUM_CELL),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = contentPadding.calculateBottomPadding() + 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            val fullWidth: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
            when {
                state.error != null -> item(span = fullWidth) { MessageBox(state.error.orEmpty(), icon = Icons.Rounded.CloudOff) }
                state.isLoading && state.data.isEmpty() -> item(span = fullWidth) { LoadingBox() }
                state.data.isEmpty() -> item(span = fullWidth) {
                    MessageBox("Альбомов пока нет. Добавить альбом к себе можно кнопкой «+» на его странице.", icon = Icons.Rounded.Album)
                }
            }
            gridItems(state.data, key = { it.id }) { album -> AlbumCard(album, navigator::openPlaylist) }
        }
    }
}

@Composable
private fun AlbumCard(album: Playlist, onOpen: (Playlist) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onOpen(album) }.padding(4.dp)) {
        Artwork(album.coverUrl, album.id, Modifier.fillMaxWidth().aspectRatio(1f), cornerRadius = 6.dp)
        Text(
            album.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            album.subtitle.ifBlank { formatTracks(album.trackCount) },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val ALBUM_CELL = 150.dp

@Composable
fun PlaylistRow(playlist: Playlist, onOpen: (Playlist) -> Unit) {
    ListItem(
        headlineContent = { Text(playlist.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall) },
        supportingContent = {
            Text(
                listOfNotNull(playlist.subtitle.takeIf { it.isNotBlank() }, formatTracks(playlist.trackCount)).joinToString(" • "),
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
