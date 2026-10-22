@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.vkmusic.ui.components

import com.vkmusic.data.toUserMessage
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.ui.compose.state.rememberCurrentMediaItemState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import com.vkmusic.data.api.VkAudioRepository
import com.vkmusic.data.model.Lyrics
import com.vkmusic.data.model.Track
import com.vkmusic.player.queue.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LyricsViewModel @Inject constructor(
    private val api: VkAudioRepository,
    playerConnection: PlayerConnection,
) : ViewModel() {

    val player = playerConnection.player

    private val _state = MutableStateFlow(LoadState<Lyrics?>(null, isLoading = true))
    val state: StateFlow<LoadState<Lyrics?>> = _state.asStateFlow()

    private var loadedFor: String? = null

    fun load(track: Track) {
        if (loadedFor == track.id) return
        loadedFor = track.id
        _state.value = LoadState(null, isLoading = true)
        viewModelScope.launch {
            _state.value = runCatching { api.getLyrics(track) }
                .fold(
                    onSuccess = { LoadState(it, error = if (it == null) "Текст песни не найден" else null) },
                    onFailure = { LoadState(null, error = it.toUserMessage("Не удалось загрузить текст")) },
                )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSheet(track: Track, onDismiss: () -> Unit, viewModel: LyricsViewModel = hiltViewModel(key = "lyrics_${track.id}")) {
    LaunchedEffect(track.id) { viewModel.load(track) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = 24.dp)) {
            Text(track.title, style = MaterialTheme.typography.titleLarge)
            Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val lyrics = state.data
            when {
                state.isLoading -> LoadingBox()
                lyrics == null -> MessageBox(state.error ?: "Текст песни не найден")
                else -> {
                    val currentPlayer = player
                    if (lyrics.isSynced && currentPlayer != null) {
                        SyncedLyrics(lyrics, track, currentPlayer)
                    } else {
                        LyricsLines(lyrics, activeIndex = -1)
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncedLyrics(lyrics: Lyrics, track: Track, player: Player) {
    val isCurrent = rememberCurrentMediaItemState(player).mediaItem?.mediaId == track.id
    val progress = rememberProgressStateWithTickInterval(player, 250L)
    val position = progress.currentPositionMs
    val active = if (isCurrent) lyrics.lines.indexOfLast { (it.startMs ?: Long.MAX_VALUE) <= position } else -1
    LyricsLines(lyrics, active)
}

@Composable
private fun LyricsLines(lyrics: Lyrics, activeIndex: Int) {
    val listState = rememberLazyListState()
    LaunchedEffect(activeIndex) {
        if (activeIndex > 2) listState.animateScrollToItem(activeIndex - 2)
    }
    LazyColumn(state = listState, modifier = Modifier.padding(top = 16.dp)) {
        itemsIndexed(lyrics.lines) { index, line ->
            val active = index == activeIndex
            Text(
                text = line.text.ifBlank { "♪" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                color = when {
                    active -> MaterialTheme.colorScheme.primary
                    activeIndex >= 0 && index < activeIndex -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
        item { Text("", Modifier.padding(bottom = 32.dp)) }
    }
}
