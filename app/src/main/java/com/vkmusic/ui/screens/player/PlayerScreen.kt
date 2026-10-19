@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.vkmusic.ui.screens.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.ui.compose.state.rememberCurrentMediaItemState
import androidx.media3.ui.compose.state.rememberNextButtonState
import androidx.media3.ui.compose.state.rememberPreviousButtonState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import com.vkmusic.data.model.Track
import com.vkmusic.data.repository.DownloadStatus
import com.vkmusic.ui.components.Artwork
import com.vkmusic.ui.components.TrackActionsMessages
import com.vkmusic.ui.components.TrackActionsViewModel
import com.vkmusic.ui.formatDuration

@Composable
fun PlayerScreen(
    player: Player?,
    onBack: () -> Unit,
    onSimilar: (Track) -> Unit,
    actions: TrackActionsViewModel = hiltViewModel(),
) {
    TrackActionsMessages(actions)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Свернуть")
            }
        }
        if (player == null) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("Плеер не подключён")
            }
        } else {
            PlayerContent(player, actions, onSimilar)
        }
    }
}

@Composable
private fun PlayerContent(player: Player, actions: TrackActionsViewModel, onSimilar: (Track) -> Unit) {
    val itemState = rememberCurrentMediaItemState(player)
    val metadata = itemState.mediaMetadata
    val key = itemState.mediaItem?.mediaId.orEmpty()

    Spacer(Modifier.height(16.dp))
    Artwork(
        url = metadata.artworkUri?.toString(),
        key = key,
        cornerRadius = 16.dp,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
    )
    Spacer(Modifier.height(32.dp))
    Text(
        text = metadata.title?.toString() ?: "Ничего не играет",
        style = MaterialTheme.typography.headlineSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Text(
        text = metadata.artist?.toString().orEmpty(),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(24.dp))
    SeekBar(player)
    Spacer(Modifier.height(16.dp))
    TransportControls(player)
    Spacer(Modifier.height(16.dp))
    actions.trackFor(itemState.mediaItem?.mediaId)?.let { TrackActionsRow(it, actions, onSimilar) }
}

@Composable
private fun TrackActionsRow(track: Track, actions: TrackActionsViewModel, onSimilar: (Track) -> Unit) {
    val library by actions.library.collectAsStateWithLifecycle()
    val downloads by actions.downloads.collectAsStateWithLifecycle()
    val inLibrary = track in library
    val downloaded = downloads[track.id]?.status == DownloadStatus.Completed

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        IconButton(onClick = { if (inLibrary) actions.removeFromLibrary(track) else actions.addToLibrary(track) }) {
            Icon(
                if (inLibrary) Icons.Rounded.Check else Icons.Rounded.Add,
                contentDescription = if (inLibrary) "Удалить из моей музыки" else "Добавить к себе",
            )
        }
        IconButton(onClick = { onSimilar(track) }) {
            Icon(Icons.Rounded.Radar, contentDescription = "Похожие")
        }
        IconButton(onClick = { if (downloaded) actions.removeDownload(track) else actions.download(listOf(track)) }) {
            Icon(
                if (downloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                contentDescription = if (downloaded) "Удалить загрузку" else "Скачать",
            )
        }
    }
}

@Composable
private fun SeekBar(player: Player) {
    val progress = rememberProgressStateWithTickInterval(player, 250L)
    val duration = progress.durationMs.coerceAtLeast(0L)
    var dragPosition by remember { mutableStateOf<Float?>(null) }
    val position = dragPosition ?: if (duration > 0) progress.currentPositionMs.toFloat() / duration else 0f

    Slider(
        value = position.coerceIn(0f, 1f),
        onValueChange = { dragPosition = it },
        onValueChangeFinished = {
            dragPosition?.let { player.seekTo((it * duration).toLong()) }
            dragPosition = null
        },
        enabled = duration > 0,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatDuration((position * duration).toLong()), style = MaterialTheme.typography.labelMedium)
        Text(formatDuration(duration), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun TransportControls(player: Player) {
    val previous = rememberPreviousButtonState(player)
    val next = rememberNextButtonState(player)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShuffleIconButton(player)
        IconButton(onClick = previous::onClick, enabled = previous.isEnabled) {
            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Предыдущий", modifier = Modifier.size(36.dp))
        }
        LargePlayPauseButton(player)
        IconButton(onClick = next::onClick, enabled = next.isEnabled) {
            Icon(Icons.Rounded.SkipNext, contentDescription = "Следующий", modifier = Modifier.size(36.dp))
        }
        RepeatIconButton(player)
    }
}
