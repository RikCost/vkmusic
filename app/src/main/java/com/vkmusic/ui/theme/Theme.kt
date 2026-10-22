package com.vkmusic.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight

/**
 * Цветовые схемы Meridius (`dist/renderer/themes.json`): у каждой темы два цвета — основной фон
 * и акцент. Остальная палитра выводится из них так же, как в CSS-переменных Meridius
 * (`secondary-hover`, `item-hover`, `small-text` — оттенки этих двух цветов).
 */
data class MeridiusTheme(
    val id: String,
    val name: String,
    val description: String,
    val background: Color,
    val accent: Color,
) {
    val isDark: Boolean get() = background.luminance() < 0.5f
}

object MeridiusThemes {
    const val SYSTEM = "system"

    val all = listOf(
        MeridiusTheme("classic", "Классическая", "Тёмная с красным акцентом", Color(0xFF141414), Color(0xFFE9003F)),
        MeridiusTheme("modern", "Современная", "Тёмно-фиолетовая цветовая схема", Color(0xFF242529), Color(0xFF8C78F3)),
        MeridiusTheme("vk-black", "VK Black", "Чёрная цветовая схема в духе VK", Color(0xFF121212), Color(0xFF71AAEB)),
        MeridiusTheme("vk-white", "VK White", "Светлая цветовая схема в духе VK", Color(0xFFFFFFFF), Color(0xFF5181B8)),
        MeridiusTheme("spotify", "Spotify", "Палитра Spotify", Color(0xFF181818), Color(0xFF1DB954)),
        MeridiusTheme("higurashi", "Higurashi", "Бело-красная цветовая схема", Color(0xFFFFFFFF), Color(0xFFFF0000)),
    )

    fun byId(id: String): MeridiusTheme? = all.firstOrNull { it.id == id }
}

private fun MeridiusTheme.colorScheme(): ColorScheme {
    val content = if (isDark) Color.White else Color(0xFF141414)
    val onAccent = if (accent.luminance() > 0.6f) Color.Black else Color.White
    fun layer(alpha: Float) = content.copy(alpha = alpha).compositeOver(background)
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accent.copy(alpha = 0.22f).compositeOver(background),
        onPrimaryContainer = content,
        inversePrimary = accent,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = layer(0.12f),
        onSecondaryContainer = content,
        tertiary = accent,
        onTertiary = onAccent,
        tertiaryContainer = accent.copy(alpha = 0.22f).compositeOver(background),
        onTertiaryContainer = content,
        background = background,
        onBackground = content,
        surface = background,
        onSurface = content,
        surfaceVariant = layer(0.08f),
        onSurfaceVariant = content.copy(alpha = 0.62f).compositeOver(background),
        surfaceTint = accent,
        surfaceDim = background,
        surfaceBright = layer(0.10f),
        surfaceContainerLowest = background,
        surfaceContainerLow = layer(0.03f),
        surfaceContainer = layer(0.05f),
        surfaceContainerHigh = layer(0.08f),
        surfaceContainerHighest = layer(0.11f),
        outline = layer(0.28f),
        outlineVariant = layer(0.12f),
        scrim = Color.Black,
    )
}

// Meridius набирает заголовки и названия треков жирным — переносим веса, а не шрифт.
private val MeridiusTypography = Typography().let { base ->
    base.copy(
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun MuVkTheme(
    themeId: String = MeridiusThemes.all.first().id,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        themeId == MeridiusThemes.SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> (MeridiusThemes.byId(themeId) ?: MeridiusThemes.all.first()).colorScheme()
    }
    MaterialTheme(colorScheme = colorScheme, typography = MeridiusTypography, content = content)
}
