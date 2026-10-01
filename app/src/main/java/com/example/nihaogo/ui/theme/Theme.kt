package com.example.nihaogo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = RedDark,
    onPrimary = Night,
    secondary = Gold,
    onSecondary = Night,
    tertiary = JadeLight,
    onTertiary = Night,
    primaryContainer = Color(0xFF5C1A22),
    secondaryContainer = Color(0xFF4A3A10),
    tertiaryContainer = Color(0xFF113F3A),
    background = Night,
    surface = Night,
    surfaceVariant = NightSurface,
    surfaceContainerLowest = Night,
    surfaceContainerLow = NightSurface,
    surfaceContainer = NightSurface,
    surfaceContainerHigh = NightSurface,
    surfaceContainerHighest = Color(0xFF362E26),
)

private val LightColorScheme = lightColorScheme(
    primary = Red,
    onPrimary = Color.White,
    secondary = Gold,
    onSecondary = Ink,
    tertiary = Jade,
    onTertiary = Color.White,
    background = Cream,
    onBackground = Ink,
    surface = Cream,
    onSurface = Ink,
    primaryContainer = Color(0xFFFFDADC),
    secondaryContainer = Color(0xFFFFEBC2),
    tertiaryContainer = Color(0xFFC9F0EA),
    surfaceVariant = Color(0xFFFFEFD9),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF3E3),
    surfaceContainer = Color(0xFFFFEFD9),
    surfaceContainerHigh = Color(0xFFFFE7C7),
    surfaceContainerHighest = Color(0xFFFFE1B8),
)

@Composable
fun NiHaoGoTheme(
    // Always light: the painted backgrounds are made for a bright rice-paper look.
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    // Dynamic color is intentionally off so the game keeps its own look.
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
