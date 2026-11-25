package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.R
import com.example.data.local.BackgroundPreset
import com.example.media.BackgroundImageStorage
import com.example.ui.theme.LocalCadenceAppearance
import com.example.ui.theme.backgroundGradientColors

@Composable
fun AppBackgroundLayer(
    preset: BackgroundPreset,
    overlayOpacity: Float,
    customBackgroundPath: String?,
    modifier: Modifier = Modifier,
) {
    val appearance = LocalCadenceAppearance.current
    val darkTheme = appearance.darkTheme
    val colorScheme = MaterialTheme.colorScheme

    val customBitmap by produceState<Bitmap?>(initialValue = null, customBackgroundPath, preset) {
        value = if (preset == BackgroundPreset.CUSTOM_PHOTO && !customBackgroundPath.isNullOrBlank()) {
            BackgroundImageStorage.loadBitmap(customBackgroundPath, maxDimensionPx = 1600)
        } else {
            null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (preset) {
            BackgroundPreset.THEME_SOLID -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colorScheme.background),
                )
            }

            BackgroundPreset.WALLPAPER_SKY_CLOUDS -> {
                Image(
                    painter = painterResource(id = R.drawable.img_bg_sky_clouds),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            BackgroundPreset.WALLPAPER_AURORA_WAVES -> {
                Image(
                    painter = painterResource(id = R.drawable.img_bg_aurora_waves),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            BackgroundPreset.CUSTOM_PHOTO -> {
                val bmp = customBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    GradientBackdropCanvas(
                        colors = backgroundGradientColors(BackgroundPreset.THEME_GLOW, colorScheme, darkTheme),
                        glowPrimary = colorScheme.primary,
                        glowSecondary = colorScheme.secondary,
                        darkTheme = darkTheme,
                    )
                }
            }

            BackgroundPreset.THEME_GLOW,
            BackgroundPreset.SKY_HORIZON,
            BackgroundPreset.COTTON_CANDY,
            BackgroundPreset.NORTHERN_LIGHTS,
            BackgroundPreset.SUNSET_PRISM -> {
                val stops = backgroundGradientColors(preset, colorScheme, darkTheme)
                GradientBackdropCanvas(
                    colors = stops,
                    glowPrimary = if (preset == BackgroundPreset.THEME_GLOW) colorScheme.primary else stops.first(),
                    glowSecondary = if (preset == BackgroundPreset.THEME_GLOW) colorScheme.secondary else stops.last(),
                    darkTheme = darkTheme,
                )
            }
        }

        // Adjustable contrast scrim so foreground text & cards remain legible on any photo/gradient
        if (preset != BackgroundPreset.THEME_SOLID) {
            val effectiveAlpha = when (preset) {
                BackgroundPreset.WALLPAPER_SKY_CLOUDS,
                BackgroundPreset.WALLPAPER_AURORA_WAVES,
                BackgroundPreset.CUSTOM_PHOTO -> overlayOpacity.coerceIn(0.15f, 0.85f)
                else -> (overlayOpacity * 0.62f).coerceIn(0.08f, 0.72f)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorScheme.background.copy(alpha = effectiveAlpha)),
            )
        }
    }
}

@Composable
private fun GradientBackdropCanvas(
    colors: List<Color>,
    glowPrimary: Color,
    glowSecondary: Color,
    darkTheme: Boolean,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(colors = colors),
        )
        val glowAlpha1 = if (darkTheme) 0.24f else 0.30f
        val glowAlpha2 = if (darkTheme) 0.18f else 0.22f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glowPrimary.copy(alpha = glowAlpha1), Color.Transparent),
                center = Offset(size.width * 0.18f, size.height * 0.14f),
                radius = size.maxDimension * 0.62f,
            ),
            radius = size.maxDimension * 0.62f,
            center = Offset(size.width * 0.18f, size.height * 0.14f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glowSecondary.copy(alpha = glowAlpha2), Color.Transparent),
                center = Offset(size.width * 0.86f, size.height * 0.78f),
                radius = size.maxDimension * 0.58f,
            ),
            radius = size.maxDimension * 0.58f,
            center = Offset(size.width * 0.86f, size.height * 0.78f),
        )
    }
}
