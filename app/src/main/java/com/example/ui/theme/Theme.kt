package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import com.example.data.local.BackgroundPreset
import com.example.data.local.ThemePalette

@Immutable
data class CadenceAppearanceConfig(
    val darkTheme: Boolean = true,
    val palette: ThemePalette = ThemePalette.SKY_BLUE,
    val backgroundPreset: BackgroundPreset = BackgroundPreset.THEME_SOLID,
    val hasDecorativeBackground: Boolean = false,
)

val LocalCadenceAppearance = staticCompositionLocalOf { CadenceAppearanceConfig() }

// macOS radii: ~6pt controls, ~10pt cards, ~12pt windows
val CadenceShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

@Composable
fun CadenceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    palette: ThemePalette = ThemePalette.SKY_BLUE,
    dynamicColor: Boolean = false,
    backgroundPreset: BackgroundPreset = BackgroundPreset.THEME_SOLID,
    content: @Composable () -> Unit,
) {
    val appearance = CadenceAppearanceConfig(
        darkTheme = darkTheme,
        palette = palette,
        backgroundPreset = BackgroundPreset.THEME_SOLID,
        hasDecorativeBackground = false,
    )
    CompositionLocalProvider(LocalCadenceAppearance provides appearance) {
        MaterialTheme(
            colorScheme = macColorScheme(palette, darkTheme),
            typography = CadenceTypography,
            shapes = CadenceShapes,
            content = content,
        )
    }
}
