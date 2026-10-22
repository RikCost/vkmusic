@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.vkmusic.ui.screens.player

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.width
import androidx.media3.common.MediaMetadata
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.basicMarquee
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.vkmusic.ui.components.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.ui.compose.state.rememberCurrentMediaItemState
import androidx.media3.ui.compose.state.rememberNextButtonState
import androidx.media3.ui.compose.state.rememberPreviousButtonState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import com.vkmusic.data.model.Track
import com.vkmusic.data.repository.DownloadStatus
import com.vkmusic.data.repository.SettingsRepository
import com.vkmusic.player.queue.toTrack
import com.vkmusic.ui.components.Artwork
import com.vkmusic.ui.components.LyricsSheet
import com.vkmusic.ui.components.TrackActionsMessages
import com.vkmusic.ui.components.TrackActionsViewModel
import com.vkmusic.ui.components.shareText
import com.vkmusic.ui.formatDuration
import com.vkmusic.ui.navigation.LocalNavigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val speed: StateFlow<Float> = settingsRepository.settings.map { it.playbackSpeed }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1f)

    // Скорость применяет PlaybackService из настроек — так она переживает перезапуск плеера.
    fun cycleSpeed() {
        val next = SPEEDS.firstOrNull { it > speed.value + 0.01f } ?: SPEEDS.first()
        viewModelScope.launch { settingsRepository.setPlaybackSpeed(next) }
    }

    private companion object {
        val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    }
}

@Composable
fun PlayerScreen(
    player: Player?,
    onBack: () -> Unit,
    actions: TrackActionsViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    TrackActionsMessages(actions)
    var queueOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Свернуть")
            }
            Text(
                "Сейчас играет",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { queueOpen = true }, enabled = player != null) {
                Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = "Очередь")
            }
        }
        if (player == null) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("Плеер не подключён")
            }
        } else {
            PlayerContent(player, actions, playerViewModel)
        }
    }

    if (queueOpen && player != null) QueueSheet(player, onDismiss = { queueOpen = false })
}

@Composable
private fun ColumnScope.PlayerContent(player: Player, actions: TrackActionsViewModel, playerViewModel: PlayerViewModel) {
    val itemState = rememberCurrentMediaItemState(player)
    val metadata = itemState.mediaMetadata
    val key = itemState.mediaItem?.mediaId.orEmpty()
    val track = itemState.mediaItem?.toTrack()

    // На паузе обложка чуть отступает назад и пружинит обратно при воспроизведении.
    val paused = rememberPlayPauseButtonState(player).showPlay
    val coverScale by animateFloatAsState(
        targetValue = if (paused) PAUSED_COVER_SCALE else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "cover_scale",
    )
    val cover = Modifier.graphicsLayer {
        scaleX = coverScale
        scaleY = coverScale
    }
    // В горизонтальной ориентации квадратная обложка во всю ширину выталкивала кнопки за экран:
    // там она слева во всю высоту, а название, перемотка и кнопки — справа.
    if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        Row(Modifier.weight(1f).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(
                url = metadata.artworkUri?.toString(),
                key = key,
                cornerRadius = 8.dp,
                modifier = Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true).then(cover),
            )
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                PlayerDetails(player, metadata, track)
                Spacer(Modifier.height(8.dp))
                track?.let { TrackActionsRow(it, actions, playerViewModel) }
            }
        }
        return
    }
    Spacer(Modifier.height(12.dp))
    Artwork(
        url = metadata.artworkUri?.toString(),
        key = key,
        cornerRadius = 8.dp,
        modifier = Modifier.fillMaxWidth().aspectRatio(1f).then(cover),
    )
    Spacer(Modifier.height(28.dp))
    PlayerDetails(player, metadata, track)
    Spacer(Modifier.weight(1f))
    track?.let { TrackActionsRow(it, actions, playerViewModel) }
    Spacer(Modifier.height(8.dp))
}

/** Название, исполнитель, перемотка и кнопки управления — общие для обеих ориентаций. */
@Composable
private fun PlayerDetails(player: Player, metadata: MediaMetadata, track: Track?) {
    val navigator = LocalNavigator.current
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = metadata.title?.toString() ?: "Ничего не играет",
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            modifier = Modifier.basicMarquee(initialDelayMillis = 2_000),
        )
        val artistId = track?.artistId
        Text(
            text = metadata.artist?.toString().orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (artistId != null) {
                Modifier.clickable { navigator.openArtist(artistId, track.artist) }
            } else {
                Modifier
            },
        )
    }
    Spacer(Modifier.height(16.dp))
    SeekBar(player)
    Spacer(Modifier.height(8.dp))
    TransportControls(player)
}

@Composable
private fun TrackActionsRow(track: Track, actions: TrackActionsViewModel, playerViewModel: PlayerViewModel) {
    val library by actions.library.collectAsStateWithLifecycle()
    val downloads by actions.downloads.collectAsStateWithLifecycle()
    val speed by playerViewModel.speed.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val inLibrary = track in library
    val downloadStatus = downloads[track.id]?.status
    var lyricsOpen by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { if (inLibrary) actions.removeFromLibrary(track) else actions.addToLibrary(track) }) {
            Icon(
                if (inLibrary) Icons.Rounded.Check else Icons.Rounded.Add,
                contentDescription = if (inLibrary) "Удалить из моей музыки" else "Добавить к себе",
                tint = if (inLibrary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = { context.shareText("${track.artist} — ${track.title}\n${track.shareUrl}") }) {
            Icon(Icons.Rounded.Share, contentDescription = "Поделиться")
        }
        DownloadButton(downloadStatus, onDownload = { actions.download(listOf(track)) }, onRemove = { actions.removeDownload(track) })
        IconButton(onClick = { actions.playTrackMix(track) }) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = "Микс по треку")
        }
        IconButton(onClick = { lyricsOpen = true }, enabled = track.hasLyrics) {
            Icon(Icons.Rounded.Lyrics, contentDescription = "Текст песни")
        }
        TextButton(onClick = playerViewModel::cycleSpeed) {
            Text("x${formatSpeed(speed)}", color = if (speed != 1f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
    }
    if (lyricsOpen) LyricsSheet(track, onDismiss = { lyricsOpen = false })
}

/** Скачать / прогресс с отменой / удалить загрузку: повторное нажатие во время загрузки её отменяет, а не ставит заново. */
@Composable
private fun DownloadButton(status: DownloadStatus?, onDownload: () -> Unit, onRemove: () -> Unit) {
    when (status) {
        null, DownloadStatus.Failed -> IconButton(onClick = onDownload) {
            Icon(Icons.Rounded.Download, contentDescription = "Скачать")
        }
        DownloadStatus.Completed -> IconButton(onClick = onRemove) {
            Icon(Icons.Rounded.DownloadDone, contentDescription = "Удалить загрузку", tint = MaterialTheme.colorScheme.primary)
        }
        DownloadStatus.Queued, is DownloadStatus.Downloading -> IconButton(onClick = onRemove) {
            Box(contentAlignment = Alignment.Center) {
                if (status is DownloadStatus.Downloading) {
                    CircularProgressIndicator({ status.percent / 100f }, Modifier.size(28.dp), strokeWidth = 2.dp)
                } else {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                }
                Icon(Icons.Rounded.Close, contentDescription = "Отменить загрузку", modifier = Modifier.size(16.dp))
            }
        }
    }
}

private const val PAUSED_COVER_SCALE = 0.88f

private fun formatSpeed(speed: Float): String = speed.toString().removeSuffix(".0")

@Composable
private fun SeekBar(player: Player) {
    val progress = rememberProgressStateWithTickInterval(player, 250L)
    val duration = progress.durationMs.coerceAtLeast(0L)
    var dragPosition by remember { mutableStateOf<Float?>(null) }
    val position = dragPosition ?: if (duration > 0) progress.currentPositionMs.toFloat() / duration else 0f
    val colors = MaterialTheme.colorScheme

    Slider(
        value = position.coerceIn(0f, 1f),
        onValueChange = { dragPosition = it },
        onValueChangeFinished = {
            dragPosition?.let { player.seekTo((it * duration).toLong()) }
            dragPosition = null
        },
        enabled = duration > 0,
        colors = SliderDefaults.colors(
            thumbColor = colors.primary,
            activeTrackColor = colors.primary,
            inactiveTrackColor = colors.outlineVariant,
        ),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            "${formatDuration((position * duration).toLong())} / ${formatDuration(duration)}",
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun TransportControls(player: Player) {
    val previous = rememberPreviousButtonState(player)
    val next = rememberNextButtonState(player)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
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

private class QueueEntry(val windowIndex: Int, val item: MediaItem)

/** Очередь в порядке воспроизведения (с учётом перемешивания), как список «Сейчас играет» Meridius. */
@Composable
private fun rememberQueue(player: Player): Pair<List<QueueEntry>, Int> {
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(
                        Player.EVENT_TIMELINE_CHANGED,
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    )
                ) {
                    version++
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    return remember(player, version) {
        val timeline = player.currentTimeline
        val shuffle = player.shuffleModeEnabled
        val entries = buildList {
            if (!timeline.isEmpty) {
                var index = timeline.getFirstWindowIndex(shuffle)
                while (index != C.INDEX_UNSET && size < player.mediaItemCount) {
                    add(QueueEntry(index, player.getMediaItemAt(index)))
                    index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffle)
                }
            }
        }
        entries to player.currentMediaItemIndex
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(player: Player, onDismiss: () -> Unit) {
    val (entries, current) = rememberQueue(player)
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        val position = entries.indexOfFirst { it.windowIndex == current }
        if (position > 0) listState.scrollToItem(position)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text(
            "Сейчас играет",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(state = listState, modifier = Modifier.fillMaxHeight(0.85f)) {
            items(entries, key = { "${it.windowIndex}_${it.item.mediaId}" }) { entry ->
                val isCurrent = entry.windowIndex == current
                val metadata = entry.item.mediaMetadata
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isCurrent) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceContainerLow)
                        .clickable { player.seekTo(entry.windowIndex, 0L) }
                        .padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Artwork(metadata.artworkUri?.toString(), entry.item.mediaId, Modifier.size(40.dp), cornerRadius = 4.dp)
                        if (isCurrent) Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(
                            metadata.title?.toString().orEmpty(),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            metadata.artist?.toString().orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!isCurrent) {
                        IconButton(onClick = { player.removeMediaItem(entry.windowIndex) }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Убрать из очереди")
                        }
                    }
                }
            }
        }
    }
}
