package com.astracode.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/**
 * Shared drawer palette aligned with the editor theme.
 * Primary actions use the editor's warm champagne accent; the drawer no longer introduces a separate teal/navy brand.
 */
object AstraColors {
    val Primary = Color(0xFFC5A46D)
    val Secondary = Color(0xFF87948A)
    val Tertiary = Color(0xFFE3B567)
    val Warning = Color(0xFFE3B567)
    val Error = Color(0xFFD9857C)

    val Dark = Color(0xFF101311)
    val Drawer = Color(0xFF1C211D)
    val Card = Color(0xFF272D27)
    val Border = Color(0xFF343B34)
    val Selected = Color(0xFF3B3020)
    val Hover = Color(0xFF283229)
    val Text = Color(0xFFE8E3D8)
    val Muted = Color(0xFFA7ADA4)

    val Inset = Color(0xFF0B0D0B)
    val Dock = Color(0xFF181C18)
    val BufferHeader = Color(0xFF181C18)
    val CardGradientStart = Color(0xFF1C211D)
    val IconBadgeStart = Color(0xFF283229)
    val IconBadgeEnd = Color(0xFF1C211D)
    val HighlightedWorkspace = Color(0xFF3B3020)

    // File-type badges remain distinct so different file types are easy to identify.
    val RustBadgeBg = Color(0xFFE43715)
    val RustBadgeFg = Color(0xFFFF8566)
    val TomlBadgeBg = Color(0xFF00B4D8)
    val TomlBadgeFg = Color(0xFF48CAE4)

    val DrawerScrim = Color(0xCC05080C)
}

object AstraFonts {
    val Mono: FontFamily = FontFamily.Monospace
    val Sans: FontFamily = FontFamily.SansSerif
}

private val AstraDarkScheme = darkColorScheme(
    primary = AstraColors.Primary,
    onPrimary = AstraColors.Dark,
    primaryContainer = Color(0xFF3B3020),
    onPrimaryContainer = AstraColors.Text,
    secondary = AstraColors.Secondary,
    onSecondary = AstraColors.Dark,
    secondaryContainer = Color(0xFF283229),
    onSecondaryContainer = AstraColors.Text,
    tertiary = AstraColors.Tertiary,
    onTertiary = AstraColors.Dark,
    error = AstraColors.Error,
    onError = AstraColors.Dark,
    errorContainer = Color(0xFF35201D),
    onErrorContainer = Color(0xFFFFDAD4),
    background = AstraColors.Dark,
    onBackground = AstraColors.Text,
    surface = AstraColors.Dark,
    onSurface = AstraColors.Text,
    surfaceVariant = AstraColors.Drawer,
    onSurfaceVariant = AstraColors.Muted,
    surfaceTint = AstraColors.Primary,
    outline = AstraColors.Border,
    outlineVariant = AstraColors.Border,
    surfaceDim = AstraColors.Dark,
    surfaceBright = Color(0xFF3B423B),
    surfaceContainerLowest = Color(0xFF0B0D0B),
    surfaceContainerLow = Color(0xFF181C18),
    surfaceContainer = AstraColors.Drawer,
    surfaceContainerHigh = AstraColors.Card,
    surfaceContainerHighest = Color(0xFF30372F),
)

@Composable
fun AstraTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AstraDarkScheme, content = content)
}
