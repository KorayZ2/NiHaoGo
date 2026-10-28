package com.example.nihaogo.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
    surface = Paper,
    onSurface = Ink,
    onSurfaceVariant = InkSoft,
    primaryContainer = Color(0xFFFDE3DA),
    onPrimaryContainer = RedDeep,
    secondaryContainer = Color(0xFFFDEBC8),
    tertiaryContainer = Color(0xFFC9F0EA),
    surfaceVariant = Color(0xFFFCEEDD),
    outline = Color(0xFFD9BFA0),
    outlineVariant = Hairline,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF9F1),
    surfaceContainer = Color(0xFFFEF4E8),
    surfaceContainerHigh = Color(0xFFFDF0E1),
    surfaceContainerHighest = Color(0xFFFCEBD7),
)

// Soft, rounded corners everywhere, with pill-shaped buttons (see GameButton).
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
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
        shapes = AppShapes,
        content = content
    )
}
