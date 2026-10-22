@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.vkmusic.ui.screens.player

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOn
import androidx.compose.material.icons.rounded.RepeatOneOn
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.ShuffleOn
import com.vkmusic.ui.components.FilledIconButton
import androidx.compose.material3.Icon
import com.vkmusic.ui.components.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberRepeatButtonState
import androidx.media3.ui.compose.state.rememberShuffleButtonState

@Composable
fun PlayPauseIconButton(player: Player, modifier: Modifier = Modifier) {
    val state = rememberPlayPauseButtonState(player)
    IconButton(onClick = state::onClick, enabled = state.isEnabled, modifier = modifier) {
        PlayPauseIcon(showPlay = state.showPlay)
    }
}

@Composable
fun LargePlayPauseButton(player: Player, size: Dp = 72.dp) {
    val state = rememberPlayPauseButtonState(player)
    FilledIconButton(onClick = state::onClick, enabled = state.isEnabled, modifier = Modifier.size(size)) {
        PlayPauseIcon(showPlay = state.showPlay, modifier = Modifier.size(size / 2))
    }
}

@Composable
private fun PlayPauseIcon(showPlay: Boolean, modifier: Modifier = Modifier) {
    Icon(
        imageVector = if (showPlay) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
        contentDescription = if (showPlay) "Воспроизвести" else "Пауза",
        modifier = modifier,
    )
}

@Composable
fun ShuffleIconButton(player: Player) {
    val state = rememberShuffleButtonState(player)
    IconButton(onClick = state::onClick, enabled = state.isEnabled) {
        Icon(
            imageVector = if (state.shuffleOn) Icons.Rounded.ShuffleOn else Icons.Rounded.Shuffle,
            contentDescription = "Перемешать",
            tint = if (state.shuffleOn) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        )
    }
}

@Composable
fun RepeatIconButton(player: Player) {
    val state = rememberRepeatButtonState(player)
    IconButton(onClick = state::onClick, enabled = state.isEnabled) {
        Icon(
            imageVector = when (state.repeatModeState) {
                Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOneOn
                Player.REPEAT_MODE_ALL -> Icons.Rounded.RepeatOn
                else -> Icons.Rounded.Repeat
            },
            contentDescription = "Повтор",
            tint = if (state.repeatModeState != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        )
    }
}
