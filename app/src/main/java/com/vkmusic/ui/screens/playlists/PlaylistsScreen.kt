package com.vkmusic.ui.screens.playlists

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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vkmusic.data.model.Playlist
import com.vkmusic.ui.components.Artwork
import com.vkmusic.ui.components.CreatePlaylistDialog
import com.vkmusic.ui.components.LoadingBox
import com.vkmusic.ui.components.MessageBox
import com.vkmusic.ui.screens.bottomOnly
import com.vkmusic.ui.screens.search.SectionTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    contentPadding: PaddingValues,
    onOpenPlaylist: (Playlist) -> Unit,
    viewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    val (albums, playlists) = state.data.partition { it.isAlbum }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Плейлисты") },
            actions = {
                IconButton(onClick = { creating = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = "Создать плейлист")
                }
            },
        )
        PullToRefreshBox(
            isRefreshing = state.isLoading && state.data.isNotEmpty(),
            onRefresh = viewModel::refresh,
            modifier = Modifier.weight(1f),
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding.bottomOnly()) {
                when {
                    state.error != null -> item { MessageBox(state.error.orEmpty()) }
                    state.isLoading && state.data.isEmpty() -> item { LoadingBox() }
                }
                if (playlists.isNotEmpty()) {
                    item { SectionTitle("Мои плейлисты") }
                    items(playlists, key = { it.id }) { PlaylistRow(it, onOpenPlaylist) }
                }
                if (albums.isNotEmpty()) {
                    item { SectionTitle("Альбомы") }
                    items(albums, key = { it.id }) { PlaylistRow(it, onOpenPlaylist) }
                }
            }
        }
    }

    if (creating) {
        CreatePlaylistDialog(
            onCreate = {
                viewModel.create(it)
                creating = false
            },
            onDismiss = { creating = false },
        )
    }
}

@Composable
fun PlaylistRow(playlist: Playlist, onOpen: (Playlist) -> Unit) {
    ListItem(
        headlineContent = { Text(playlist.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                listOfNotNull(playlist.subtitle.takeIf { it.isNotBlank() }, "${playlist.trackCount} треков").joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            Box(Modifier.padding(vertical = 4.dp)) {
                Artwork(playlist.coverUrl, playlist.id, Modifier.size(56.dp))
            }
        },
        modifier = Modifier.clickable { onOpen(playlist) },
    )
}
