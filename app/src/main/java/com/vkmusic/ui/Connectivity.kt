package com.vkmusic.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Есть ли сеть — из [com.vkmusic.data.network.NetworkMonitor], раздаётся корневым экраном. */
val LocalIsOnline = compositionLocalOf { true }

/**
 * Ошибка загрузки, показанная без сети, повторяется сама, когда сеть возвращается: вышел из лифта —
 * список догрузился, «Повторить» нажимать не нужно. Ошибки при живой сети так не повторяются.
 */
@Composable
fun RetryWhenBackOnline(onRetry: () -> Unit) {
    val online = LocalIsOnline.current
    val retry by rememberUpdatedState(onRetry)
    var wasOffline by remember { mutableStateOf(!online) }
    LaunchedEffect(online) {
        if (!online) {
            wasOffline = true
        } else if (wasOffline) {
            wasOffline = false
            retry()
        }
    }
}

/** Тонкая плашка над мини-плеером, пока нет сети: играет то, что успело загрузиться, и загрузки. */
@Composable
fun OfflineBar(modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = !LocalIsOnline.current, enter = expandVertically(), exit = shrinkVertically(), modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(vertical = 4.dp),
        ) {
            Icon(
                Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 6.dp).size(14.dp),
            )
            Text(
                "Нет подключения — продолжим, когда сеть вернётся",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
