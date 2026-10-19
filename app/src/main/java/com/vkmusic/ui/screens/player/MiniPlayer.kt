@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.vkmusic.ui.screens.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.ui.compose.state.rememberCurrentMediaItemState
import androidx.media3.ui.compose.state.rememberNextButtonState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import com.vkmusic.ui.components.Artwork

@Composable
fun MiniPlayer(
    player: Player,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemState = rememberCurrentMediaItemState(player)
    val item = itemState.mediaItem ?: return
    val metadata = itemState.mediaMetadata
    val progress = rememberProgressStateWithTickInterval(player, 500L)
    val nextState = rememberNextButtonState(player)

    Surface(
        tonalElevation = 3.dp,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column {
            LinearProgressIndicator(
                progress = {
                    if (progress.durationMs > 0) progress.currentPositionMs.toFloat() / progress.durationMs else 0f
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Artwork(url = metadata.artworkUri?.toString(), key = item.mediaId, modifier = Modifier.size(44.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = metadata.title?.toString().orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = metadata.artist?.toString().orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                PlayPauseIconButton(player)
                IconButton(onClick = nextState::onClick, enabled = nextState.isEnabled) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Следующий")
                }
            }
        }
    }
}
