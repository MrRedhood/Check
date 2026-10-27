package com.astracode.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** Palette lifted 1:1 from the Stitch drawer export (tailwind.config colors + inline hex values). */
object AstraColors {
    val Primary = Color(0xFF65D9AD)
    val Secondary = Color(0xFF79A8FF)
    val Tertiary = Color(0xFFC6A0F6)
    val Warning = Color(0xFFF4BE68)
    val Error = Color(0xFFFF7B86)

    val Dark = Color(0xFF0B0F14)
    val Drawer = Color(0xFF101722)
    val Card = Color(0xFF151D29)
    val Border = Color(0xFF293647)
    val Selected = Color(0xFF1B2A3D)
    val Hover = Color(0xFF1E293B)
    val Text = Color(0xFFE6EDF7)
    val Muted = Color(0xFF8A99AD)

    // Inline hex values used by the design
    val Inset = Color(0xFF0F1622)
    val Dock = Color(0xFF0E1520)
    val BufferHeader = Color(0xFF121924)
    val CardGradientStart = Color(0xFF182333)
    val IconBadgeStart = Color(0xFF1A2638)
    val IconBadgeEnd = Color(0xFF121B27)
    val HighlightedWorkspace = Color(0xFF172334)

    // File-type badges
    val RustBadgeBg = Color(0xFFE43715)
    val RustBadgeFg = Color(0xFFFF8566)
    val TomlBadgeBg = Color(0xFF00B4D8)
    val TomlBadgeFg = Color(0xFF48CAE4)

    // bg-[#05080C]/80 drawer scrim (blur-[2px] intentionally dropped: full-screen blur is a frame-budget risk on mid-range ARM64)
    val DrawerScrim = Color(0xCC05080C)
}

object AstraFonts {
    // Swap for Font(R.font.jetbrains_mono_*) / Font(R.font.inter_*) once the TTFs are bundled in res/font.
    val Mono: FontFamily = FontFamily.Monospace
    val Sans: FontFamily = FontFamily.SansSerif
}

private val AstraDarkScheme = darkColorScheme(
    primary = AstraColors.Primary,
    onPrimary = AstraColors.Dark,
    secondary = AstraColors.Secondary,
    tertiary = AstraColors.Tertiary,
    error = AstraColors.Error,
    background = AstraColors.Dark,
    onBackground = AstraColors.Text,
    surface = AstraColors.Drawer,
    onSurface = AstraColors.Text,
    surfaceVariant = AstraColors.Card,
    onSurfaceVariant = AstraColors.Muted,
    outline = AstraColors.Border,
    outlineVariant = AstraColors.Border,
)

@Composable
fun AstraTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AstraDarkScheme, content = content)
}