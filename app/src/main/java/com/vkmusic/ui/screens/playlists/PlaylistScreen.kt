package com.vkmusic.ui.screens.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import com.vkmusic.ui.components.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vkmusic.data.model.Playlist
import com.vkmusic.data.model.Track
import com.vkmusic.ui.LocalSnackbarHostState
import com.vkmusic.ui.components.Artwork
import com.vkmusic.ui.components.BackTopBar
import com.vkmusic.ui.components.rememberBulkDownloadGuard
import com.vkmusic.ui.components.ConfirmDialog
import com.vkmusic.ui.components.EditPlaylistDialog
import com.vkmusic.ui.components.FilterField
import com.vkmusic.ui.components.MenuAction
import com.vkmusic.ui.components.RoundPlayButton
import com.vkmusic.ui.components.TrackActionsViewModel
import com.vkmusic.ui.components.TrackSourcePage
import com.vkmusic.ui.components.shareText
import com.vkmusic.ui.formatCount
import com.vkmusic.ui.formatDate
import com.vkmusic.ui.formatTotalDuration
import com.vkmusic.ui.navigation.LocalNavigator
import com.vkmusic.ui.screens.bottomOnly

@Composable
fun PlaylistScreen(
    contentPadding: PaddingValues,
    viewModel: PlaylistViewModel = hiltViewModel(),
    actions: TrackActionsViewModel = hiltViewModel(),
) {
    val navigator = LocalNavigator.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val details by viewModel.details.collectAsStateWithLifecycle()
    val libraryCopy by viewModel.libraryCopy.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val snackbar = LocalSnackbarHostState.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PlaylistEvent.Message -> snackbar.showSnackbar(event.text)
                PlaylistEvent.Closed -> navigator.back()
            }
        }
    }

    val isEditable = (libraryCopy ?: details).isEditable
    Column(Modifier.fillMaxSize()) {
        BackTopBar(title = "", onBack = navigator::back)
        TrackSourcePage(
            state = state,
            onRefresh = viewModel::refresh,
            contentPadding = contentPadding.bottomOnly(),
            emptyText = "В плейлисте пока нет треков",
            showPlayAll = false,
            filter = filter,
            actions = actions,
            header = {
                item {
                    PlaylistHeader(
                        playlist = details,
                        tracks = state.data,
                        isEditable = isEditable,
                        isInLibrary = libraryCopy != null,
                        actions = actions,
                        onFollow = viewModel::follow,
                        onUnfollow = viewModel::unfollow,
                        onEdit = { editing = true },
                        onDelete = { confirmDelete = true },
                    )
                }
                if (state.data.size > 1) item { FilterField(filter, { filter = it }) }
            },
        )
    }

    if (editing) {
        EditPlaylistDialog(
            playlist = details,
            onSave = { title, description ->
                viewModel.edit(title, description)
                editing = false
            },
            onDismiss = { editing = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Удалить плейлист?",
            text = "«${details.title}» будет удалён из ВКонтакте.",
            confirm = "Удалить",
            onConfirm = viewModel::delete,
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Шапка плейлиста Meridius: обложка, описание, «N прослушиваний • N треков • длительность», «Обновлено …». */
@Composable
private fun PlaylistHeader(
    playlist: Playlist,
    tracks: List<Track>,
    isEditable: Boolean,
    isInLibrary: Boolean,
    actions: TrackActionsViewModel,
    onFollow: () -> Unit,
    onUnfollow: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    val bulk = rememberBulkDownloadGuard()

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Artwork(playlist.coverUrl ?: tracks.firstOrNull()?.coverUrl, playlist.id, Modifier.size(132.dp), cornerRadius = 6.dp)
            Column(Modifier.padding(start = 16.dp)) {
                Text(playlist.title, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (playlist.subtitle.isNotBlank() && playlist.subtitle != playlist.description) {
                    Text(playlist.subtitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                val stats = listOfNotNull(
                    playlist.plays.takeIf { it > 0 }?.let { "${formatCount(it)} прослушиваний" },
                    "${tracks.size.takeIf { it > 0 } ?: playlist.trackCount} треков",
                    tracks.sumOf { it.durationSec.toLong() }.takeIf { it > 0 }?.let(::formatTotalDuration),
                ).joinToString(" • ")
                Text(stats, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                if (playlist.updatedAt > 0) {
                    Text("Обновлено ${formatDate(playlist.updatedAt)}", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
        }
        if (playlist.description.isNotBlank()) {
            Text(
                playlist.description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(vertical = 12.dp),
        ) {
            RoundPlayButton(onClick = { actions.play(tracks, 0) }, enabled = tracks.isNotEmpty())
            IconButton(onClick = { actions.shuffle(tracks) }, enabled = tracks.isNotEmpty()) {
                Icon(Icons.Rounded.Shuffle, contentDescription = "Перемешать")
            }
            if (!isEditable) {
                IconButton(onClick = if (isInLibrary) onUnfollow else onFollow) {
                    Icon(
                        if (isInLibrary) Icons.Rounded.Check else Icons.Rounded.Add,
                        contentDescription = if (isInLibrary) "Отписаться от плейлиста" else "Подписаться на плейлист",
                        tint = if (isInLibrary) colors.primary else colors.onSurface,
                    )
                }
            }
            IconButton(onClick = { bulk.download(actions, tracks) }, enabled = tracks.isNotEmpty()) {
                Icon(Icons.Rounded.Download, contentDescription = "Скачать плейлист")
            }
            IconButton(
                onClick = { bulk.export(actions, tracks, folder = playlist.title) },
                enabled = tracks.isNotEmpty(),
            ) {
                Icon(Icons.Rounded.SaveAlt, contentDescription = "Сохранить плейлист в MP3")
            }
            IconButton(onClick = { context.shareText("${playlist.title}\n${playlist.shareUrl}") }) {
                Icon(Icons.Rounded.Share, contentDescription = "Поделиться плейлистом")
            }
            if (isEditable || isInLibrary) {
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Ещё") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (isEditable) {
                            MenuAction("Редактировать плейлист", Icons.Rounded.Edit) {
                                menuOpen = false
                                onEdit()
                            }
                            MenuAction("Удалить плейлист", Icons.Rounded.Delete) {
                                menuOpen = false
                                onDelete()
                            }
                        } else {
                            MenuAction("Отписаться от плейлиста", Icons.Rounded.RemoveCircleOutline) {
                                menuOpen = false
                                onUnfollow()
                            }
                        }
                    }
                }
            }
        }
    }
}
