package com.vkmusic.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val VkBlue = Color(0xFF0077FF)

private val LightColors = lightColorScheme(
    primary = VkBlue,
    secondary = Color(0xFF4A5B75),
    tertiary = Color(0xFFFF3347),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF71AAEB),
    secondary = Color(0xFFB0C2DB),
    tertiary = Color(0xFFFF6B7A),
    background = Color(0xFF141414),
    surface = Color(0xFF19191A),
)

@Composable
fun VkMusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
