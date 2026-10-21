@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.vkmusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.ui.compose.state.rememberCurrentMediaItemState
import com.vkmusic.data.model.Playlist
import com.vkmusic.data.model.Track
import com.vkmusic.data.export.ExportStatus
import com.vkmusic.data.repository.DownloadStatus
import com.vkmusic.ui.LocalSnackbarHostState
import com.vkmusic.ui.formatDuration
import com.vkmusic.ui.navigation.LocalNavigator

@Composable
fun TrackList(
    tracks: List<Track>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onPlay: ((Int) -> Unit)? = null,
    header: LazyListScope.() -> Unit = {},
    actions: TrackActionsViewModel = hiltViewModel(),
) {
    TrackActionsMessages(actions)
    val currentId = currentTrackId(actions)

    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
        header()
        trackItems(tracks, currentId, actions, onPlay)
    }
}

/** Строки треков для встраивания в чужой LazyColumn (главная, страница исполнителя). */
fun LazyListScope.trackItems(
    tracks: List<Track>,
    currentId: String?,
    actions: TrackActionsViewModel,
    onPlay: ((Int) -> Unit)? = null,
    keyPrefix: String = "",
) {
    itemsIndexed(tracks, key = { index, track -> "$keyPrefix${index}_${track.id}" }) { index, track ->
        TrackRow(
            track = track,
            index = index,
            isCurrent = track.id == currentId,
            onClick = { onPlay?.invoke(index) ?: actions.play(tracks, index) },
            actions = actions,
        )
    }
}

@Composable
fun currentTrackId(actions: TrackActionsViewModel): String? {
    val player by actions.player.collectAsStateWithLifecycle()
    return player?.let { currentMediaId(it) }
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
fun TrackRow(
    track: Track,
    index: Int,
    isCurrent: Boolean,
    onClick: () -> Unit,
    actions: TrackActionsViewModel,
    extraMenu: (@Composable (close: () -> Unit) -> Unit)? = null,
) {
    val downloads by actions.downloads.collectAsStateWithLifecycle()
    val exports by actions.exports.collectAsStateWithLifecycle()
    val status = downloads[track.id]?.status
    val export = exports[track.id]
    // Колонка «Альбом» как в таблице Meridius — только там, где хватает ширины.
    val wide = LocalConfiguration.current.screenWidthDp >= WIDE_LAYOUT_DP
    val colors = MaterialTheme.colorScheme

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isCurrent) colors.surfaceVariant else colors.background)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
            if (isCurrent) {
                Icon(Icons.Rounded.GraphicEq, contentDescription = "Играет", tint = colors.primary, modifier = Modifier.size(18.dp))
            } else {
                Text("${index + 1}", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
        }
        Artwork(url = track.coverUrl, key = track.id, cornerRadius = 4.dp, modifier = Modifier.size(44.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                text = track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                color = if (isCurrent) colors.primary else colors.onSurface,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (track.isExplicit) ExplicitBadge()
                DownloadBadge(status)
                ExportBadge(export)
                Text(
                    track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (wide) {
            Text(
                track.albumTitle.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(0.7f).padding(end = 12.dp),
            )
        }
        Text(formatDuration(track.durationSec * 1000L), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        TrackMenuButton(track, status, export, actions, extraMenu)
    }
}

@Composable
private fun TrackMenuButton(
    track: Track,
    status: DownloadStatus?,
    export: ExportStatus?,
    actions: TrackActionsViewModel,
    extraMenu: (@Composable (close: () -> Unit) -> Unit)?,
) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val library by actions.library.collectAsStateWithLifecycle()
    val inLibrary = track in library
    var menuOpen by remember { mutableStateOf(false) }
    var playlistPicker by remember { mutableStateOf(false) }
    var lyricsOpen by remember { mutableStateOf(false) }

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
            MenuAction("Добавить в плейлист", Icons.AutoMirrored.Rounded.PlaylistAdd, closing { playlistPicker = true })
            MenuAction("Воспроизвести следующим", Icons.AutoMirrored.Rounded.QueueMusic, closing { actions.playNext(track) })
            MenuAction("Микс по треку", Icons.Rounded.AutoAwesome, closing { actions.playTrackMix(track) })
            MenuAction("Похожие", Icons.Rounded.Radar, closing { navigator.openSimilar(track) })
            if (track.hasLyrics) MenuAction("Открыть текст песни", Icons.Rounded.Lyrics, closing { lyricsOpen = true })
            track.artistId?.let { artistId ->
                MenuAction("Перейти к исполнителю", Icons.Rounded.Person, closing { navigator.openArtist(artistId, track.artist) })
            }
            MenuAction("Поделиться", Icons.Rounded.Share, closing { context.shareText("${track.artist} — ${track.title}\n${track.shareUrl}") })
            if (export is ExportStatus.Queued || export is ExportStatus.Running) {
                MenuAction("Отменить сохранение MP3", Icons.Rounded.Close, closing { actions.cancelExport(track) })
            } else {
                MenuAction("Сохранить в MP3", Icons.Rounded.SaveAlt, closing { actions.exportMp3(listOf(track)) })
            }
            if (status == null || status == DownloadStatus.Failed) {
                MenuAction("Скачать аудиозапись", Icons.Rounded.Download, closing { actions.download(listOf(track)) })
            } else {
                MenuAction("Удалить загрузку", Icons.Rounded.Delete, closing { actions.removeDownload(track) })
            }
            extraMenu?.invoke { menuOpen = false }
        }
    }

    if (playlistPicker) {
        PlaylistPickerDialog(
            actions = actions,
            onPick = { actions.addToPlaylist(it, listOf(track)) },
            onCreate = { actions.createPlaylist(it, listOf(track)) },
            onDismiss = { playlistPicker = false },
        )
    }
    if (lyricsOpen) LyricsSheet(track, onDismiss = { lyricsOpen = false })
}

@Composable
fun MenuAction(text: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

@Composable
private fun ExplicitBadge() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(end = 4.dp)
            .size(14.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(2.dp)),
    ) {
        Text("E", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.background)
    }
}

// Сохранение файлом: идёт — кольцо прогресса, готово — значок файла.
@Composable
private fun ExportBadge(status: ExportStatus?) {
    val modifier = Modifier.size(14.dp)
    when (status) {
        null, is ExportStatus.Failed -> return
        ExportStatus.Queued -> CircularProgressIndicator(modifier, strokeWidth = 2.dp, color = MaterialTheme.colorScheme.tertiary)
        is ExportStatus.Running -> CircularProgressIndicator({ status.percent / 100f }, modifier, strokeWidth = 2.dp, color = MaterialTheme.colorScheme.tertiary)
        is ExportStatus.Saved -> Icon(Icons.Rounded.AudioFile, contentDescription = "Сохранено в MP3", modifier = modifier, tint = MaterialTheme.colorScheme.primary)
    }
    Box(Modifier.size(4.dp))
}

@Composable
private fun DownloadBadge(status: DownloadStatus?) {
    val modifier = Modifier.size(14.dp)
    when (status) {
        null -> return
        DownloadStatus.Completed -> Icon(Icons.Rounded.DownloadDone, null, modifier, tint = MaterialTheme.colorScheme.primary)
        DownloadStatus.Failed -> Icon(Icons.Rounded.ErrorOutline, null, modifier, tint = MaterialTheme.colorScheme.error)
        DownloadStatus.Queued -> CircularProgressIndicator(modifier, strokeWidth = 2.dp)
        is DownloadStatus.Downloading -> CircularProgressIndicator({ status.percent / 100f }, modifier, strokeWidth = 2.dp)
    }
    Box(Modifier.size(4.dp))
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
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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

private const val WIDE_LAYOUT_DP = 600
