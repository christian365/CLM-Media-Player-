package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.local.BackgroundPreset
import com.example.data.local.ThemePalette

@Immutable
data class CadenceAppearanceConfig(
    val darkTheme: Boolean = true,
    val palette: ThemePalette = ThemePalette.SKY_BLUE,
    val backgroundPreset: BackgroundPreset = BackgroundPreset.THEME_GLOW,
    val hasDecorativeBackground: Boolean = true,
)

val LocalCadenceAppearance = staticCompositionLocalOf { CadenceAppearanceConfig() }

val CadenceShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun CadenceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    palette: ThemePalette = ThemePalette.SKY_BLUE,
    dynamicColor: Boolean = false,
    backgroundPreset: BackgroundPreset = BackgroundPreset.THEME_GLOW,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        paletteColorScheme(palette, darkTheme)
    }

    val appearance = CadenceAppearanceConfig(
        darkTheme = darkTheme,
        palette = palette,
        backgroundPreset = backgroundPreset,
        hasDecorativeBackground = backgroundPreset != BackgroundPreset.THEME_SOLID,
    )

    CompositionLocalProvider(LocalCadenceAppearance provides appearance) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CadenceTypography,
            shapes = CadenceShapes,
            content = content,
        )
    }
}
