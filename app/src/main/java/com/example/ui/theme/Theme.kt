package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val OmniDarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = Color(0xFF04131A),
    primaryContainer = Color(0xFF0A2E3D),
    onPrimaryContainer = Color(0xFFB8F6FF),
    secondary = ElectricViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF241A45),
    onSecondaryContainer = Color(0xFFE2D6FF),
    tertiary = NeonEmerald,
    onTertiary = Color(0xFF031F10),
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = SlateSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = SlateSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    outline = SlateCardBorder,
    error = CrimsonRose
)

private val OmniLightColorScheme = lightColorScheme(
    primary = CyberCyanDark,
    onPrimary = Color.White,
    secondary = ElectricViolet,
    tertiary = NeonEmerald,
    background = Color(0xFFF1F5F9),
    surface = Color.White,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) OmniDarkColorScheme else OmniLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
