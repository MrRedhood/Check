package com.astracode.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Obsidian Atelier: warm, restrained colour for a premium developer workspace.
val AtelierBackground = Color(0xFF101311)
val AtelierSurface = Color(0xFF1C211D)
val AtelierSurfaceRaised = Color(0xFF272D27)
val AtelierChampagne = Color(0xFFC5A46D)
val AtelierText = Color(0xFFE8E3D8)
val AtelierMutedText = Color(0xFFA7ADA4)
val AtelierSage = Color(0xFF87948A)
val AtelierBorder = Color(0xFF343B34)
val AtelierWarning = Color(0xFFE3B567)
val AtelierError = Color(0xFFD9857C)
val AtelierErrorSurface = Color(0xFF35201D)

private val AtelierColorScheme = darkColorScheme(
    primary = AtelierChampagne,
    onPrimary = AtelierBackground,
    primaryContainer = Color(0xFF3B3020),
    onPrimaryContainer = AtelierText,
    secondary = AtelierSage,
    onSecondary = AtelierBackground,
    secondaryContainer = Color(0xFF283229),
    onSecondaryContainer = AtelierText,
    tertiary = AtelierWarning,
    onTertiary = AtelierBackground,
    error = AtelierError,
    onError = AtelierBackground,
    errorContainer = AtelierErrorSurface,
    onErrorContainer = Color(0xFFFFDAD4),
    background = AtelierBackground,
    onBackground = AtelierText,
    surface = AtelierBackground,
    onSurface = AtelierText,
    surfaceVariant = AtelierSurface,
    onSurfaceVariant = AtelierMutedText,
    surfaceTint = AtelierChampagne,
    outline = AtelierBorder,
    outlineVariant = AtelierBorder,
    surfaceDim = AtelierBackground,
    surfaceBright = Color(0xFF3B423B),
    surfaceContainerLowest = Color(0xFF0B0D0B),
    surfaceContainerLow = Color(0xFF181C18),
    surfaceContainer = AtelierSurface,
    surfaceContainerHigh = AtelierSurfaceRaised,
    surfaceContainerHighest = Color(0xFF30372F),
)

private val AtelierShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

@Composable
fun AstraCodeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AtelierColorScheme,
        typography = Typography(),
        shapes = AtelierShapes,
        content = content,
    )
}
