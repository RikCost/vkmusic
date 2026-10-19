package com.vkmusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@Composable
fun Artwork(
    url: String?,
    key: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
) {
    Box(modifier.clip(RoundedCornerShape(cornerRadius))) {
        PlaceholderArtwork(key)
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun PlaceholderArtwork(key: String) {
    val hue = (key.hashCode().toUInt() % 360u).toFloat()
    val brush = Brush.linearGradient(
        listOf(Color.hsv(hue, 0.55f, 0.85f), Color.hsv((hue + 40f) % 360f, 0.7f, 0.5f)),
    )
    Box(Modifier.fillMaxSize().background(brush), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.fillMaxWidth(0.45f),
        )
    }
}
