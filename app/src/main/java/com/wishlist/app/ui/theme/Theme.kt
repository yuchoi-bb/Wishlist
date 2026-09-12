package com.wishlist.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Warm neutrals rather than the usual cool greys: paper, not screen. Every value is deliberate, so
 * dynamic color is off by default — Material You would repaint all of this from the wallpaper and
 * the palette below would never be seen.
 */
private val Brand = Color(0xFF245A28)
private val BrandLight = Color(0xFF8FC594)

private val LightColors = lightColorScheme(
    primary = Brand,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4EDE4),
    onPrimaryContainer = Color(0xFF143717),
    secondary = Color(0xFF5C574E),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFAF8F4),
    onBackground = Color(0xFF1E1B16),
    surface = Color(0xFFFAF8F4),
    onSurface = Color(0xFF1E1B16),
    // Cards and bands sit a step above the paper rather than below it.
    surfaceVariant = Color(0xFFF3F0E9),
    onSurfaceVariant = Color(0xFF5C574E),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFF3F0E9),
    outline = Color(0xFFA8A296),
    outlineVariant = Color(0xFFE7E2D8),
    error = Color(0xFFA8443A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF7E7E3),
    onErrorContainer = Color(0xFF6E241D),
)

private val DarkColors = darkColorScheme(
    primary = BrandLight,
    onPrimary = Color(0xFF0C2A0F),
    primaryContainer = Color(0xFF1E3A22),
    onPrimaryContainer = Color(0xFFCDE5CF),
    secondary = Color(0xFFCFC7B8),
    onSecondary = Color(0xFF201D18),
    background = Color(0xFF16150F),
    onBackground = Color(0xFFF0EBE0),
    surface = Color(0xFF16150F),
    onSurface = Color(0xFFF0EBE0),
    surfaceVariant = Color(0xFF262319),
    onSurfaceVariant = Color(0xFFBDB6A6),
    surfaceContainer = Color(0xFF1E1C15),
    surfaceContainerHigh = Color(0xFF23201A),
    surfaceContainerHighest = Color(0xFF2A2620),
    outline = Color(0xFF8A8272),
    outlineVariant = Color(0xFF39352B),
    error = Color(0xFFFF9A8F),
    onError = Color(0xFF48120D),
    errorContainer = Color(0xFF4A1D1F),
    onErrorContainer = Color(0xFFFFD9D3),
)

@Composable
fun ArcTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = ArcTypography,
        content = content,
    )
}
