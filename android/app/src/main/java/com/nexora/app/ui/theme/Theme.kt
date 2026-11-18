package com.nexora.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// NEXORA design system: Dark Purple / Deep Violet / Neon Violet (glass premium)
object NexoraColors {
    val Background = Color(0xFF0B0714)
    val SurfaceGlass = Color(0x148B5CF6)
    val GlassBorder = Color(0x38A78BFA)
    val Violet = Color(0xFF8B5CF6)
    val VioletDeep = Color(0xFF6D28D9)
    val Neon = Color(0xFFC084FC)
    val TextPrimary = Color(0xFFECE9F7)
    val TextSub = Color(0xFFA79FC4)
    val Ok = Color(0xFF34D399)
    val Warn = Color(0xFFFBBF24)
    val Err = Color(0xFFF87171)
}

private val Scheme = darkColorScheme(
    primary = NexoraColors.Violet,
    secondary = NexoraColors.Neon,
    background = NexoraColors.Background,
    surface = Color(0xFF120A1F),
    onBackground = NexoraColors.TextPrimary,
    onSurface = NexoraColors.TextPrimary,
    outline = NexoraColors.GlassBorder
)

@Composable
fun NexoraTheme(content: @Composable () -> Unit) {
    // Dark-first premium identity (brand requirement)
    MaterialTheme(colorScheme = Scheme, content = content)
}
