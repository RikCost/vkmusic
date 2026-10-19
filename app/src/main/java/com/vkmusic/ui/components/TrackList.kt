@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.vkmusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.ui.compose.state.rememberCurrentMediaItemState
import com.vkmusic.data.model.Playlist
import com.vkmusic.data.model.Track
import com.vkmusic.data.repository.DownloadStatus
import com.vkmusic.ui.LocalSnackbarHostState
import com.vkmusic.ui.formatDuration

@Composable
fun TrackList(
    tracks: List<Track>,
    onSimilar: (Track) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onPlay: ((Int) -> Unit)? = null,
    header: LazyListScope.() -> Unit = {},
    actions: TrackActionsViewModel = hiltViewModel(),
) {
    val player by actions.player.collectAsStateWithLifecycle()
    val currentId = player?.let { currentMediaId(it) }
    var playlistPickerFor by remember { mutableStateOf<List<Track>?>(null) }

    TrackActionsMessages(actions)

    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
        header()
        itemsIndexed(tracks, key = { index, track -> "${index}_${track.id}" }) { index, track ->
            TrackRow(
                track = track,
                isCurrent = track.id == currentId,
                onClick = { onPlay?.invoke(index) ?: actions.play(tracks, index) },
                actions = actions,
                onSimilar = { onSimilar(track) },
                onAddToPlaylist = { playlistPickerFor = listOf(track) },
            )
        }
    }

    playlistPickerFor?.let { selection ->
        PlaylistPickerDialog(
            actions = actions,
            onPick = { actions.addToPlaylist(it, selection) },
            onCreate = { actions.createPlaylist(it, selection) },
            onDismiss = { playlistPickerFor = null },
        )
    }
}

@Composable
fun TrackActionsMessages(actions: TrackActionsViewModel) {
    val snackbar = LocalSnackbarHostState.current
    LaunchedEffect(actions) {
        actions.messages.collect { snackbar.showSnackbar(it) }
    }
}

@Composable
private fun currentMediaId(player: Player): String? =
    rememberCurrentMediaItemState(player).mediaItem?.mediaId

@Composable
private fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    onClick: () -> Unit,
    actions: TrackActionsViewModel,
    onSimilar: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    val library by actions.library.collectAsStateWithLifecycle()
    val downloads by actions.downloads.collectAsStateWithLifecycle()
    val inLibrary = track in library
    val status = downloads[track.id]?.status
    var menuOpen by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = {
            Text(
                text = track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DownloadBadge(status)
                Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        leadingContent = {
            Box(contentAlignment = Alignment.Center) {
                Artwork(url = track.coverUrl, key = track.id, modifier = Modifier.size(48.dp))
                if (isCurrent) {
                    Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)))
                    Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.inversePrimary)
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDuration(track.durationSec * 1000L), style = MaterialTheme.typography.labelMedium)
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Действия")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        fun closing(action: () -> Unit): () -> Unit = {
                            menuOpen = false
                            action()
                        }
                        if (inLibrary) {
                            MenuAction("Удалить из моей музыки", Icons.Rounded.Delete, closing { actions.removeFromLibrary(track) })
                        } else {
                            MenuAction("Добавить к себе", Icons.Rounded.Add, closing { actions.addToLibrary(track) })
                        }
                        MenuAction("Добавить в плейлист", Icons.AutoMirrored.Rounded.PlaylistAdd, closing(onAddToPlaylist))
                        MenuAction("Воспроизвести следующим", Icons.AutoMirrored.Rounded.QueueMusic, closing { actions.playNext(track) })
                        MenuAction("Похожие", Icons.Rounded.Radar, closing(onSimilar))
                        if (status == null || status == DownloadStatus.Failed) {
                            MenuAction("Скачать", Icons.Rounded.Download, closing { actions.download(listOf(track)) })
                        } else {
                            MenuAction("Удалить загрузку", Icons.Rounded.Delete, closing { actions.removeDownload(track) })
                        }
                    }
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun MenuAction(text: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

@Composable
private fun DownloadBadge(status: DownloadStatus?) {
    val modifier = Modifier.size(16.dp)
    when (status) {
        null -> return
        DownloadStatus.Completed -> Icon(Icons.Rounded.DownloadDone, null, modifier, tint = MaterialTheme.colorScheme.primary)
        DownloadStatus.Failed -> Icon(Icons.Rounded.ErrorOutline, null, modifier, tint = MaterialTheme.colorScheme.error)
        DownloadStatus.Queued -> CircularProgressIndicator(modifier, strokeWidth = 2.dp)
        is DownloadStatus.Downloading -> CircularProgressIndicator({ status.percent / 100f }, modifier, strokeWidth = 2.dp)
    }
    Box(Modifier.size(6.dp))
}

@Composable
fun PlaylistPickerDialog(
    actions: TrackActionsViewModel,
    onPick: (Playlist) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val playlists by actions.editablePlaylists.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { actions.loadPlaylists() }

    if (creating) {
        CreatePlaylistDialog(
            onCreate = {
                onCreate(it)
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить в плейлист") },
        text = {
            LazyColumn {
                item {
                    ListItem(
                        headlineContent = { Text("Новый плейлист") },
                        leadingContent = { Icon(Icons.Rounded.Add, contentDescription = null) },
                        modifier = Modifier.clickable { creating = true },
                    )
                }
                itemsIndexed(playlists, key = { _, p -> p.id }) { _, playlist ->
                    ListItem(
                        headlineContent = { Text(playlist.title) },
                        supportingContent = { Text("${playlist.trackCount} треков") },
                        leadingContent = { Artwork(playlist.coverUrl, playlist.id, Modifier.size(40.dp)) },
                        modifier = Modifier.clickable {
                            onPick(playlist)
                            onDismiss()
                        },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
