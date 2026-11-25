package com.example

import com.example.data.local.BackgroundPreset
import com.example.data.local.ThemePalette
import com.example.ui.theme.backgroundGradientColors
import com.example.ui.theme.paletteColorScheme
import com.example.ui.theme.previewColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePaletteUnitTest {

    @Test
    fun allThemePalettesProvideDistinctLightAndDarkSchemes() {
        val primariesDark = ThemePalette.entries.map { palette ->
            val dark = paletteColorScheme(palette, darkTheme = true)
            val light = paletteColorScheme(palette, darkTheme = false)
            assertNotEquals(dark.background, light.background)
            assertNotEquals(dark.primary, dark.background)
            assertNotEquals(light.primary, light.background)
            dark.primary
        }

        // At least 7 distinct primary colors across the 8 themes
        assertTrue(primariesDark.toSet().size >= 7)
    }

    @Test
    fun skyBlueThemeIsPresentAndUsesSkyAzureTokens() {
        val skyDark = ThemePalette.SKY_BLUE.previewColors(darkTheme = true)
        val skyLight = ThemePalette.SKY_BLUE.previewColors(darkTheme = false)

        // Blue channel dominates red channel for Sky Blue primary colors
        assertTrue(skyDark.primary.blue > skyDark.primary.red)
        assertTrue(skyLight.primary.blue > skyLight.primary.red)
    }

    @Test
    fun allBackgroundPresetsReturnValidColorStops() {
        val scheme = paletteColorScheme(ThemePalette.SKY_BLUE, darkTheme = true)
        BackgroundPreset.entries.forEach { preset ->
            val stopsDark = backgroundGradientColors(preset, scheme, darkTheme = true)
            val stopsLight = backgroundGradientColors(preset, scheme, darkTheme = false)
            assertTrue("Expected >= 2 stops for $preset dark", stopsDark.size >= 2)
            assertTrue("Expected >= 2 stops for $preset light", stopsLight.size >= 2)
        }
        assertEquals("Sky Blue", ThemePalette.SKY_BLUE.label)
    }
}
