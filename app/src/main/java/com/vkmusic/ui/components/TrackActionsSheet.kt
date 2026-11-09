package com.vkmusic.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vkmusic.data.export.ExportStatus
import com.vkmusic.data.model.Track
import com.vkmusic.data.repository.DownloadStatus
import com.vkmusic.ui.formatDuration
import com.vkmusic.ui.navigation.LocalNavigator
import kotlinx.coroutines.launch

/**
 * Действия с треком: шторка снизу (открывается кнопкой «⋮» и долгим нажатием на трек) и окна,
 * которые из неё вызываются. Окна живут здесь, а не в шторке: она закрывается раньше, чем они откроются.
 */
@Composable
fun TrackActions(track: Track, open: Boolean, onClose: () -> Unit, actions: TrackActionsViewModel) {
    var playlistPicker by remember { mutableStateOf(false) }
    var lyricsOpen by remember { mutableStateOf(false) }

    if (open) {
        TrackActionsSheet(
            track = track,
            actions = actions,
            onClose = onClose,
            onPickPlaylist = { playlistPicker = true },
            onOpenLyrics = { lyricsOpen = true },
        )
    }
    if (playlistPicker) PlaylistPickerDialog(track, actions, onDismiss = { playlistPicker = false })
    if (lyricsOpen) LyricsSheet(track, onDismiss = { lyricsOpen = false })
}

/**
 * Шторка вместо выпадающего меню: одиннадцать пунктов в меню у правого края не дотянуться большим
 * пальцем, а в шапке шторки видно, к какому треку относятся действия.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackActionsSheet(
    track: Track,
    actions: TrackActionsViewModel,
    onClose: () -> Unit,
    onPickPlaylist: () -> Unit,
    onOpenLyrics: () -> Unit,
) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Подписки живут, только пока шторка открыта, а не на каждую строку списка.
    val library by actions.library.collectAsStateWithLifecycle()
    val downloads by actions.downloads.collectAsStateWithLifecycle()
    val exports by actions.exports.collectAsStateWithLifecycle()
    val inLibrary = track in library
    val download = downloads[track.id]?.status
    val export = exports[track.id]

    // Сначала шторка уезжает, потом действие: иначе окно или новый экран появлялись бы под ней.
    fun closing(action: () -> Unit): () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onClose()
            action()
        }
    }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
        ) {
            Artwork(url = track.coverUrl, key = track.id, cornerRadius = 6.dp, modifier = Modifier.size(52.dp))
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                formatDuration(track.durationSec * 1000L),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            if (inLibrary) {
                SheetAction("Удалить из моей музыки", Icons.Rounded.Delete, closing { actions.removeFromLibrary(track) })
            } else {
                SheetAction("Добавить к себе", Icons.Rounded.Add, closing { actions.addToLibrary(track) })
            }
            SheetAction("Добавить в плейлист", Icons.AutoMirrored.Rounded.PlaylistAdd, closing(onPickPlaylist))
            SheetAction("Воспроизвести следующим", Icons.AutoMirrored.Rounded.QueueMusic, closing { actions.playNext(track) })
            SheetAction("Микс по треку", Icons.Rounded.AutoAwesome, closing { actions.playTrackMix(track) })
            SheetAction("Похожие", Icons.Rounded.Radar, closing { navigator.openSimilar(track) })
            if (track.hasLyrics) SheetAction("Открыть текст песни", Icons.Rounded.Lyrics, closing(onOpenLyrics))
            track.artistId?.let { artistId ->
                SheetAction("Перейти к исполнителю", Icons.Rounded.Person, closing { navigator.openArtist(artistId, track.artist) })
            }
            SheetAction("Поделиться", Icons.Rounded.Share, closing { context.shareText("${track.artist} — ${track.title}\n${track.shareUrl}") })
            if (export is ExportStatus.Queued || export is ExportStatus.Running) {
                SheetAction("Отменить сохранение MP3", Icons.Rounded.Close, closing { actions.cancelExport(track) })
            } else {
                SheetAction("Сохранить в MP3", Icons.Rounded.AudioFile, closing { actions.exportMp3(listOf(track)) })
            }
            if (download == null || download == DownloadStatus.Failed) {
                SheetAction("Скачать аудиозапись", Icons.Rounded.Download, closing { actions.download(listOf(track)) })
            } else {
                SheetAction("Удалить загрузку", Icons.Rounded.Delete, closing { actions.removeDownload(track) })
            }
        }
    }
}

@Composable
private fun SheetAction(text: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 20.dp))
    }
}
