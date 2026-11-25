package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.example.data.local.ThemePalette

// Apple system accent colors (light / dark), matching macOS System Settings ▸ Appearance
private fun ThemePalette.appleAccent(dark: Boolean): Color = when (this) {
    ThemePalette.SKY_BLUE        -> if (dark) Color(0xFF0A84FF) else Color(0xFF007AFF)
    ThemePalette.SUNSET_CORAL    -> if (dark) Color(0xFFFF375F) else Color(0xFFFF2D55)
    ThemePalette.AURORA_MINT     -> if (dark) Color(0xFF30D158) else Color(0xFF34C759)
    ThemePalette.ELECTRIC_VIOLET -> if (dark) Color(0xFFBF5AF2) else Color(0xFFAF52DE)
    ThemePalette.GOLDEN_AMBER    -> if (dark) Color(0xFFFF9F0A) else Color(0xFFFF9500)
    ThemePalette.CRIMSON_BERRY   -> if (dark) Color(0xFFFF453A) else Color(0xFFFF3B30)
    ThemePalette.OCEAN_TEAL      -> if (dark) Color(0xFF40C8E0) else Color(0xFF30B0C7)
    ThemePalette.OBSIDIAN_SLATE  -> if (dark) Color(0xFF98989D) else Color(0xFF8E8E93)
}

fun macColorScheme(palette: ThemePalette, dark: Boolean): ColorScheme {
    val accent = palette.appleAccent(dark)
    return if (dark) darkColorScheme(
        primary = accent,
        onPrimary = Color.White,
        primaryContainer = accent.copy(alpha = 0.25f),
        onPrimaryContainer = accent,
        secondary = accent,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFF3A3A3C),
        onSecondaryContainer = Color(0xFFF5F5F7),
        tertiary = Color(0xFF48484A),
        onTertiary = Color(0xFFF5F5F7),
        error = Color(0xFFFF453A),
        onError = Color.White,
        errorContainer = Color(0xFF4A1F1C),
        onErrorContainer = Color(0xFFFFB4AB),
        background = Color(0xFF1C1C1E),
        onBackground = Color(0xFFF5F5F7),
        surface = Color(0xFF232325),
        onSurface = Color(0xFFF5F5F7),
        surfaceVariant = Color(0xFF2C2C2E),
        onSurfaceVariant = Color(0xFF98989D),
        surfaceContainerLowest = Color(0xFF161618),
        surfaceContainerLow = Color(0xFF202022),
        surfaceContainer = Color(0xFF2A2A2C),
        surfaceContainerHigh = Color(0xFF323234),
        surfaceContainerHighest = Color(0xFF3A3A3C),
        outline = Color(0xFF8E8E93),
        outlineVariant = Color(0xFF48484A),
        inverseSurface = Color(0xFF2C2C2E),
        inverseOnSurface = Color(0xFFF5F5F7),
        scrim = Color.Black,
    ) else lightColorScheme(
        primary = accent,
        onPrimary = Color.White,
        primaryContainer = accent.copy(alpha = 0.14f),
        onPrimaryContainer = accent,
        secondary = accent,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE9E9EB),
        onSecondaryContainer = Color(0xFF1D1D1F),
        tertiary = Color(0xFFE9E9EB),
        onTertiary = Color(0xFF1D1D1F),
        error = Color(0xFFFF3B30),
        onError = Color.White,
        errorContainer = Color(0xFFFFE5E5),
        onErrorContainer = Color(0xFF8C1D18),
        background = Color(0xFFF5F5F7),
        onBackground = Color(0xFF1D1D1F),
        surface = Color.White,
        onSurface = Color(0xFF1D1D1F),
        surfaceVariant = Color(0xFFEFEFF4),
        onSurfaceVariant = Color(0xFF6E6E73),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFEFEFF4),
        surfaceContainer = Color(0xFFE9E9EB),
        surfaceContainerHigh = Color(0xFFF6F6F8),
        surfaceContainerHighest = Color(0xFFE2E2E6),
        outline = Color(0xFF8E8E93),
        outlineVariant = Color(0xFFD2D2D7),
        inverseSurface = Color.White,
        inverseOnSurface = Color(0xFF1D1D1F),
        scrim = Color.Black,
    )
}

// macOS traffic lights
val MacCloseRed = Color(0xFFFF5F57)
val MacMinimizeYellow = Color(0xFFFEBC2E)
val MacZoomGreen = Color(0xFF28C840)
