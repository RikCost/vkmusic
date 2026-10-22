package com.vkmusic.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Заготовки строк списка, пока он грузится: форма будущего списка вместо одинокого спиннера. */
@Composable
fun ListSkeleton(rows: Int = SKELETON_ROWS, artworkSize: Dp = 44.dp) {
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(PULSE_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "skeleton_alpha",
    )
    val shade = MaterialTheme.colorScheme.surfaceVariant
    Column(Modifier.fillMaxWidth().alpha(pulse)) {
        repeat(rows) { index ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 40.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            ) {
                Box(Modifier.size(artworkSize).background(shade, RoundedCornerShape(4.dp)))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Разная длина строк — чтобы заготовка не выглядела таблицей одинаковых полос.
                    Box(Modifier.fillMaxWidth(0.55f + (index % 3) * 0.12f).height(12.dp).background(shade, RoundedCornerShape(6.dp)))
                    Box(Modifier.fillMaxWidth(0.3f + (index % 2) * 0.1f).height(10.dp).background(shade, RoundedCornerShape(5.dp)))
                }
            }
        }
    }
}

/** «Эквалайзер» текущего трека: столбики прыгают, пока трек играет, и замирают на паузе. */
@Composable
fun PlayingBars(isPlaying: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "bars")
    val color = MaterialTheme.colorScheme.primary
    Row(modifier.size(16.dp).semantics { contentDescription = "Играет" }, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        BAR_PERIODS.forEachIndexed { index, period ->
            val bounce by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(period, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "bar_$index",
            )
            val height by animateFloatAsState(if (isPlaying) bounce else PAUSED_HEIGHTS[index], label = "bar_height_$index")
            Box(Modifier.width(4.dp).fillMaxHeight(height).background(color, RoundedCornerShape(1.dp)))
        }
    }
}

private const val SKELETON_ROWS = 8
private const val PULSE_MS = 800
private val BAR_PERIODS = listOf(420, 560, 360)
private val PAUSED_HEIGHTS = listOf(0.45f, 0.8f, 0.6f)
