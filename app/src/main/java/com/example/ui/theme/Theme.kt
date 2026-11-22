package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val SystemDarkColorScheme = darkColorScheme(
    primary = SystemCyan,
    onPrimary = ObsidianBg,
    primaryContainer = Color(0xFF0A2A3B),
    onPrimaryContainer = SystemCyan,
    secondary = ShadowViolet,
    onSecondary = TextPrimary,
    secondaryContainer = Color(0xFF231747),
    onSecondaryContainer = Color(0xFFDDD6FE),
    tertiary = QuestEmerald,
    onTertiary = ObsidianBg,
    tertiaryContainer = Color(0xFF0A2E24),
    onTertiaryContainer = QuestEmerald,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = AbyssalSurface,
    onSurface = TextPrimary,
    surfaceVariant = HudCardSurface,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = AbyssalSurface,
    surfaceContainer = HudCardSurface,
    surfaceContainerHigh = HudElevatedSurface,
    outline = HudBorder,
    outlineVariant = Color(0xFF1F3A52),
    error = FailureCrimson,
    onError = TextPrimary,
    errorContainer = Color(0xFF3B1219),
    onErrorContainer = Color(0xFFFECACA)
)

private val SystemShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(18.dp)
)

@Composable
fun AriseSystemTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SystemDarkColorScheme,
        typography = Typography,
        shapes = SystemShapes,
        content = content
    )
}
