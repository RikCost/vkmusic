@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.vkmusic.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.ui.compose.state.rememberCurrentMediaItemState
import androidx.media3.ui.compose.state.rememberNextButtonState
import androidx.media3.ui.compose.state.rememberPreviousButtonState
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
    val previous = rememberPreviousButtonState(player)
    val next = rememberNextButtonState(player)
    val colors = MaterialTheme.colorScheme
    val fraction = if (progress.durationMs > 0) (progress.currentPositionMs.toFloat() / progress.durationMs).coerceIn(0f, 1f) else 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(colors.outlineVariant)) {
            Box(Modifier.fillMaxWidth(fraction).height(2.dp).background(colors.primary))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Artwork(url = metadata.artworkUri?.toString(), key = item.mediaId, cornerRadius = 4.dp, modifier = Modifier.size(44.dp))
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    text = metadata.title?.toString().orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = metadata.artist?.toString().orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = previous::onClick, enabled = previous.isEnabled) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "Предыдущий")
            }
            PlayPauseIconButton(player)
            IconButton(onClick = next::onClick, enabled = next.isEnabled) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Следующий")
            }
        }
    }
}
