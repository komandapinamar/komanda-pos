package com.komanda.business.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = KomandaTokens.AccentTertiary,
    onPrimary = KomandaTokens.AccentPrimary,
    secondary = KomandaTokens.TextSecondary,
    onSecondary = KomandaTokens.TextPrimary,
    background = KomandaTokens.Background,
    onBackground = KomandaTokens.TextPrimary,
    surface = KomandaTokens.Surface,
    onSurface = KomandaTokens.TextPrimary,
    surfaceVariant = KomandaTokens.SurfaceVariant,
    onSurfaceVariant = KomandaTokens.TextSecondary,
    outline = KomandaTokens.Border
)

@Composable
fun KomandaTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
