package com.komanda.business.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Amber400,
    onPrimary = Zinc950,
    secondary = Zinc400,
    onSecondary = Color.White,
    background = Zinc950,
    onBackground = Color.White,
    surface = Zinc900,
    onSurface = Color.White,
    surfaceVariant = Zinc800,
    onSurfaceVariant = Zinc400,
    outline = Zinc700
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
