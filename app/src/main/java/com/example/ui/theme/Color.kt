package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.example.data.local.BackgroundPreset
import com.example.data.local.ThemePalette

// Shared semantic accents
val CoralAccent = Color(0xFFFB7185)
val EmeraldSuccess = Color(0xFF34D399)

// 1. Sky Blue (Featured vibrant azure & cloud theme)
private val SkyBlueDark = darkColorScheme(
    primary = Color(0xFF56CCF2),
    onPrimary = Color(0xFF00273B),
    primaryContainer = Color(0xFF0A486B),
    onPrimaryContainer = Color(0xFFC8ECFF),
    secondary = Color(0xFF7DD3FC),
    onSecondary = Color(0xFF032D42),
    secondaryContainer = Color(0xFF153B56),
    onSecondaryContainer = Color(0xFFDDF4FF),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF2E1E00),
    error = Color(0xFFFB7185),
    background = Color(0xFF071322),
    onBackground = Color(0xFFF0F8FF),
    surface = Color(0xFF0D1D31),
    onSurface = Color(0xFFF0F8FF),
    surfaceVariant = Color(0xFF152942),
    onSurfaceVariant = Color(0xFFA5C4DE),
    surfaceContainerLowest = Color(0xFF050D18),
    surfaceContainerLow = Color(0xFF0C1A2C),
    surfaceContainer = Color(0xFF12243B),
    surfaceContainerHigh = Color(0xFF1A314E),
    surfaceContainerHighest = Color(0xFF233E61),
    outline = Color(0xFF34567A),
    outlineVariant = Color(0xFF1D344F),
)

private val SkyBlueLight = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEEFF),
    onPrimaryContainer = Color(0xFF04324D),
    secondary = Color(0xFF0EA5E9),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F4FF),
    onSecondaryContainer = Color(0xFF073A57),
    tertiary = Color(0xFFF59E0B),
    onTertiary = Color.White,
    error = Color(0xFFDC2626),
    background = Color(0xFFEEF7FF),
    onBackground = Color(0xFF082032),
    surface = Color(0xFFF7FBFF),
    onSurface = Color(0xFF082032),
    surfaceVariant = Color(0xFFDCEEFD),
    onSurfaceVariant = Color(0xFF3D5E78),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFE9F4FE),
    surfaceContainer = Color(0xFFDFEFFD),
    surfaceContainerHigh = Color(0xFFD0E7FA),
    surfaceContainerHighest = Color(0xFFC1DDF6),
    outline = Color(0xFF9AC2E2),
    outlineVariant = Color(0xFFCBE2F5),
)

// 2. Sunset Coral
private val SunsetCoralDark = darkColorScheme(
    primary = Color(0xFFFF758F),
    onPrimary = Color(0xFF3A0413),
    primaryContainer = Color(0xFF5D1329),
    onPrimaryContainer = Color(0xFFFFD9E0),
    secondary = Color(0xFFFFB74D),
    onSecondary = Color(0xFF331D00),
    secondaryContainer = Color(0xFF4E2D06),
    onSecondaryContainer = Color(0xFFFFE3B8),
    tertiary = Color(0xFFC084FC),
    onTertiary = Color(0xFF280B47),
    error = Color(0xFFFF6B6B),
    background = Color(0xFF180B12),
    onBackground = Color(0xFFFFF1F4),
    surface = Color(0xFF23111B),
    onSurface = Color(0xFFFFF1F4),
    surfaceVariant = Color(0xFF311926),
    onSurfaceVariant = Color(0xFFD7A9B8),
    surfaceContainerLowest = Color(0xFF11070C),
    surfaceContainerLow = Color(0xFF1F0F18),
    surfaceContainer = Color(0xFF2B1622),
    surfaceContainerHigh = Color(0xFF381E2D),
    surfaceContainerHighest = Color(0xFF452739),
    outline = Color(0xFF643B51),
    outlineVariant = Color(0xFF3E2231),
)

private val SunsetCoralLight = lightColorScheme(
    primary = Color(0xFFE11D48),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCE3),
    onPrimaryContainer = Color(0xFF4E0518),
    secondary = Color(0xFFEA580C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE6D5),
    onSecondaryContainer = Color(0xFF4D1A00),
    tertiary = Color(0xFF9333EA),
    onTertiary = Color.White,
    error = Color(0xFFB91C1C),
    background = Color(0xFFFFF5F7),
    onBackground = Color(0xFF2B0E17),
    surface = Color(0xFFFFFAFB),
    onSurface = Color(0xFF2B0E17),
    surfaceVariant = Color(0xFFFDE4EA),
    onSurfaceVariant = Color(0xFF734452),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFEFF2),
    surfaceContainer = Color(0xFFFCE6EB),
    surfaceContainerHigh = Color(0xFFF7D8E0),
    surfaceContainerHighest = Color(0xFFF0C8D2),
    outline = Color(0xFFE1A9B8),
    outlineVariant = Color(0xFFF6D6DF),
)

// 3. Aurora Mint
private val AuroraMintDark = darkColorScheme(
    primary = Color(0xFF34D399),
    onPrimary = Color(0xFF01291B),
    primaryContainer = Color(0xFF0A4933),
    onPrimaryContainer = Color(0xFFBDF7DF),
    secondary = Color(0xFF2DD4BF),
    onSecondary = Color(0xFF022823),
    secondaryContainer = Color(0xFF0E443E),
    onSecondaryContainer = Color(0xFFC2FBF2),
    tertiary = Color(0xFF38BDF8),
    onTertiary = Color(0xFF032333),
    error = CoralAccent,
    background = Color(0xFF061612),
    onBackground = Color(0xFFECFFF7),
    surface = Color(0xFF0C211C),
    onSurface = Color(0xFFECFFF7),
    surfaceVariant = Color(0xFF132E27),
    onSurfaceVariant = Color(0xFF97C9B9),
    surfaceContainerLowest = Color(0xFF040F0C),
    surfaceContainerLow = Color(0xFF0A1D18),
    surfaceContainer = Color(0xFF102822),
    surfaceContainerHigh = Color(0xFF17362E),
    surfaceContainerHighest = Color(0xFF20453B),
    outline = Color(0xFF2E5E51),
    outlineVariant = Color(0xFF193830),
)

private val AuroraMintLight = lightColorScheme(
    primary = Color(0xFF059669),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFFAEA),
    onPrimaryContainer = Color(0xFF023825),
    secondary = Color(0xFF0D9488),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF043732),
    tertiary = Color(0xFF0284C7),
    onTertiary = Color.White,
    error = Color(0xFFDC2626),
    background = Color(0xFFF0FDF8),
    onBackground = Color(0xFF07261C),
    surface = Color(0xFFF8FFFC),
    onSurface = Color(0xFF07261C),
    surfaceVariant = Color(0xFFDDF7EC),
    onSurfaceVariant = Color(0xFF3A6657),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEAFBF3),
    surfaceContainer = Color(0xFFDEF7EC),
    surfaceContainerHigh = Color(0xFFCDEEE0),
    surfaceContainerHighest = Color(0xFFBCE3D2),
    outline = Color(0xFF8FD0B7),
    outlineVariant = Color(0xFFC8ECDD),
)

// 4. Electric Violet
private val ElectricVioletDark = darkColorScheme(
    primary = Color(0xFFA78BFA),
    onPrimary = Color(0xFF1F0A4D),
    primaryContainer = Color(0xFF3B1E7A),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFFF472B6),
    onSecondary = Color(0xFF3E0625),
    secondaryContainer = Color(0xFF5D153D),
    onSecondaryContainer = Color(0xFFFFD8EC),
    tertiary = Color(0xFF38BDF8),
    onTertiary = Color(0xFF032333),
    error = CoralAccent,
    background = Color(0xFF0F0A1E),
    onBackground = Color(0xFFF5F0FF),
    surface = Color(0xFF17102B),
    onSurface = Color(0xFFF5F0FF),
    surfaceVariant = Color(0xFF22193D),
    onSurfaceVariant = Color(0xFFB9AEDB),
    surfaceContainerLowest = Color(0xFF0A0615),
    surfaceContainerLow = Color(0xFF150E27),
    surfaceContainer = Color(0xFF1E1636),
    surfaceContainerHigh = Color(0xFF281F47),
    surfaceContainerHighest = Color(0xFF332859),
    outline = Color(0xFF4C3C7A),
    outlineVariant = Color(0xFF2B214A),
)

private val ElectricVioletLight = lightColorScheme(
    primary = Color(0xFF7C3AED),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE2FF),
    onPrimaryContainer = Color(0xFF2A0864),
    secondary = Color(0xFFDB2777),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFCE0EE),
    onSecondaryContainer = Color(0xFF4D0526),
    tertiary = Color(0xFF0284C7),
    onTertiary = Color.White,
    error = Color(0xFFDC2626),
    background = Color(0xFFF7F4FF),
    onBackground = Color(0xFF1B1038),
    surface = Color(0xFFFCFAFF),
    onSurface = Color(0xFF1B1038),
    surfaceVariant = Color(0xFFECE5FC),
    onSurfaceVariant = Color(0xFF584B7A),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF3EEFE),
    surfaceContainer = Color(0xFFEBE3FD),
    surfaceContainerHigh = Color(0xFFE0D4FA),
    surfaceContainerHighest = Color(0xFFD3C3F5),
    outline = Color(0xFFB9A6E6),
    outlineVariant = Color(0xFFE0D7F7),
)

// 5. Golden Amber
private val GoldenAmberDark = darkColorScheme(
    primary = Color(0xFFFBBF24),
    onPrimary = Color(0xFF2E1E00),
    primaryContainer = Color(0xFF4F3404),
    onPrimaryContainer = Color(0xFFFFEBAA),
    secondary = Color(0xFFFB7185),
    onSecondary = Color(0xFF3A0612),
    secondaryContainer = Color(0xFF591426),
    onSecondaryContainer = Color(0xFFFFD8DF),
    tertiary = Color(0xFF38BDF8),
    onTertiary = Color(0xFF042233),
    error = CoralAccent,
    background = Color(0xFF161006),
    onBackground = Color(0xFFFFF8EA),
    surface = Color(0xFF20180A),
    onSurface = Color(0xFFFFF8EA),
    surfaceVariant = Color(0xFF2E2210),
    onSurfaceVariant = Color(0xFFD5BF95),
    surfaceContainerLowest = Color(0xFF0F0B04),
    surfaceContainerLow = Color(0xFF1D1508),
    surfaceContainer = Color(0xFF281E0E),
    surfaceContainerHigh = Color(0xFF362914),
    surfaceContainerHighest = Color(0xFF44341A),
    outline = Color(0xFF614C29),
    outlineVariant = Color(0xFF3B2D17),
)

private val GoldenAmberLight = lightColorScheme(
    primary = Color(0xFFD97706),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFEF0C7),
    onPrimaryContainer = Color(0xFF472300),
    secondary = Color(0xFFE11D48),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDCE3),
    onSecondaryContainer = Color(0xFF4D0518),
    tertiary = Color(0xFF0284C7),
    onTertiary = Color.White,
    error = Color(0xFFDC2626),
    background = Color(0xFFFFFBF2),
    onBackground = Color(0xFF261A06),
    surface = Color(0xFFFFFDF8),
    onSurface = Color(0xFF261A06),
    surfaceVariant = Color(0xFFFAEFD8),
    onSurfaceVariant = Color(0xFF6D5836),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFCF5E6),
    surfaceContainer = Color(0xFFF9EED7),
    surfaceContainerHigh = Color(0xFFF3E2C1),
    surfaceContainerHighest = Color(0xFFEBD5AA),
    outline = Color(0xFFD9BA84),
    outlineVariant = Color(0xFFF2E2C4),
)

// 6. Crimson Berry
private val CrimsonBerryDark = darkColorScheme(
    primary = Color(0xFFF43F5E),
    onPrimary = Color(0xFF3A030E),
    primaryContainer = Color(0xFF640C20),
    onPrimaryContainer = Color(0xFFFFD9DF),
    secondary = Color(0xFF818CF8),
    onSecondary = Color(0xFF11164D),
    secondaryContainer = Color(0xFF23296E),
    onSecondaryContainer = Color(0xFFE0E4FF),
    tertiary = Color(0xFF34D399),
    onTertiary = Color(0xFF02291B),
    error = Color(0xFFFF8080),
    background = Color(0xFF150910),
    onBackground = Color(0xFFFFEFF3),
    surface = Color(0xFF1F0E18),
    onSurface = Color(0xFFFFEFF3),
    surfaceVariant = Color(0xFF2D1624),
    onSurfaceVariant = Color(0xFFD1A4B4),
    surfaceContainerLowest = Color(0xFF0E050A),
    surfaceContainerLow = Color(0xFF1C0C15),
    surfaceContainer = Color(0xFF27121F),
    surfaceContainerHigh = Color(0xFF351A2A),
    surfaceContainerHighest = Color(0xFF422235),
    outline = Color(0xFF5F354C),
    outlineVariant = Color(0xFF3B1E2E),
)

private val CrimsonBerryLight = lightColorScheme(
    primary = Color(0xFFBE123C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E2),
    onPrimaryContainer = Color(0xFF450313),
    secondary = Color(0xFF4F46E5),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E5FF),
    onSecondaryContainer = Color(0xFF151254),
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    error = Color(0xFFB91C1C),
    background = Color(0xFFFFF4F7),
    onBackground = Color(0xFF290B15),
    surface = Color(0xFFFFFAFB),
    onSurface = Color(0xFF290B15),
    surfaceVariant = Color(0xFFFCE3EA),
    onSurfaceVariant = Color(0xFF6E404F),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFEEF3),
    surfaceContainer = Color(0xFFFBE4EB),
    surfaceContainerHigh = Color(0xFFF5D5DF),
    surfaceContainerHighest = Color(0xFFEDC4D1),
    outline = Color(0xFFDDA1B4),
    outlineVariant = Color(0xFFF4D3DD),
)

// 7. Ocean Teal
private val OceanTealDark = darkColorScheme(
    primary = Color(0xFF22D3EE),
    onPrimary = Color(0xFF00272E),
    primaryContainer = Color(0xFF064A57),
    onPrimaryContainer = Color(0xFFB8F7FF),
    secondary = Color(0xFF34D399),
    onSecondary = Color(0xFF01281A),
    secondaryContainer = Color(0xFF0A4834),
    onSecondaryContainer = Color(0xFFC2FBE4),
    tertiary = Color(0xFFA78BFA),
    onTertiary = Color(0xFF1F0A4D),
    error = CoralAccent,
    background = Color(0xFF05151A),
    onBackground = Color(0xFFEBFCFF),
    surface = Color(0xFF0A2027),
    onSurface = Color(0xFFEBFCFF),
    surfaceVariant = Color(0xFF112D37),
    onSurfaceVariant = Color(0xFF93C3CF),
    surfaceContainerLowest = Color(0xFF030D11),
    surfaceContainerLow = Color(0xFF081B22),
    surfaceContainer = Color(0xFF0E2730),
    surfaceContainerHigh = Color(0xFF153440),
    surfaceContainerHighest = Color(0xFF1D4352),
    outline = Color(0xFF2D5B6B),
    outlineVariant = Color(0xFF183641),
)

private val OceanTealLight = lightColorScheme(
    primary = Color(0xFF0891B2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8F6FF),
    onPrimaryContainer = Color(0xFF023642),
    secondary = Color(0xFF059669),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFFAEA),
    onSecondaryContainer = Color(0xFF023825),
    tertiary = Color(0xFF7C3AED),
    onTertiary = Color.White,
    error = Color(0xFFDC2626),
    background = Color(0xFFF0FBFE),
    onBackground = Color(0xFF07232B),
    surface = Color(0xFFF7FDFF),
    onSurface = Color(0xFF07232B),
    surfaceVariant = Color(0xFFD9F3F9),
    onSurfaceVariant = Color(0xFF39616C),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFE8F8FC),
    surfaceContainer = Color(0xFFDCF3F9),
    surfaceContainerHigh = Color(0xFFCCEBF4),
    surfaceContainerHighest = Color(0xFFBCE1EC),
    outline = Color(0xFF8CC9D8),
    outlineVariant = Color(0xFFC7EAF3),
)

// 8. Obsidian Slate (Original studio dark/porcelain option)
private val ObsidianSlateDark = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF042233),
    primaryContainer = Color(0xFF102B3F),
    onPrimaryContainer = Color(0xFFBAE6FD),
    secondary = Color(0xFFF59E0B),
    onSecondary = Color(0xFF281802),
    secondaryContainer = Color(0xFF3B2706),
    onSecondaryContainer = Color(0xFFFDE68A),
    tertiary = EmeraldSuccess,
    onTertiary = Color(0xFF03261B),
    error = CoralAccent,
    background = Color(0xFF0B0E14),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF121721),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF1A2230),
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainerLowest = Color(0xFF080A0F),
    surfaceContainerLow = Color(0xFF121721),
    surfaceContainer = Color(0xFF1A2230),
    surfaceContainerHigh = Color(0xFF232D3F),
    surfaceContainerHighest = Color(0xFF2A364B),
    outline = Color(0xFF2E3B4E),
    outlineVariant = Color(0xFF1E2838),
)

private val ObsidianSlateLight = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8F0FE),
    onPrimaryContainer = Color(0xFF073B5C),
    secondary = Color(0xFFD97706),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF5A2E04),
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    error = Color(0xFFDC2626),
    background = Color(0xFFF6F8FB),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE9EFF6),
    onSurfaceVariant = Color(0xFF475569),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F5F9),
    surfaceContainer = Color(0xFFE9EFF6),
    surfaceContainerHigh = Color(0xFFE2E8F0),
    surfaceContainerHighest = Color(0xFFD5DFEC),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
)

fun paletteColorScheme(palette: ThemePalette, darkTheme: Boolean): ColorScheme = when (palette) {
    ThemePalette.SKY_BLUE -> if (darkTheme) SkyBlueDark else SkyBlueLight
    ThemePalette.SUNSET_CORAL -> if (darkTheme) SunsetCoralDark else SunsetCoralLight
    ThemePalette.AURORA_MINT -> if (darkTheme) AuroraMintDark else AuroraMintLight
    ThemePalette.ELECTRIC_VIOLET -> if (darkTheme) ElectricVioletDark else ElectricVioletLight
    ThemePalette.GOLDEN_AMBER -> if (darkTheme) GoldenAmberDark else GoldenAmberLight
    ThemePalette.CRIMSON_BERRY -> if (darkTheme) CrimsonBerryDark else CrimsonBerryLight
    ThemePalette.OCEAN_TEAL -> if (darkTheme) OceanTealDark else OceanTealLight
    ThemePalette.OBSIDIAN_SLATE -> if (darkTheme) ObsidianSlateDark else ObsidianSlateLight
}

data class PalettePreviewColors(
    val primary: Color,
    val secondary: Color,
    val backdrop: Color,
)

fun ThemePalette.previewColors(darkTheme: Boolean): PalettePreviewColors {
    val scheme = paletteColorScheme(this, darkTheme)
    return PalettePreviewColors(
        primary = scheme.primary,
        secondary = scheme.secondary,
        backdrop = scheme.background,
    )
}

fun backgroundGradientColors(
    preset: BackgroundPreset,
    colorScheme: ColorScheme,
    darkTheme: Boolean,
): List<Color> = when (preset) {
    BackgroundPreset.THEME_SOLID -> listOf(colorScheme.background, colorScheme.background)
    BackgroundPreset.THEME_GLOW -> if (darkTheme) {
        listOf(
            colorScheme.primaryContainer.copy(alpha = 0.85f),
            colorScheme.background,
            colorScheme.secondaryContainer.copy(alpha = 0.55f),
        )
    } else {
        listOf(
            colorScheme.primaryContainer,
            colorScheme.background,
            colorScheme.secondaryContainer.copy(alpha = 0.9f),
        )
    }
    BackgroundPreset.SKY_HORIZON -> if (darkTheme) {
        listOf(Color(0xFF0A4D7A), Color(0xFF0A2540), Color(0xFF071322))
    } else {
        listOf(Color(0xFF7DD3FC), Color(0xFFBAE6FD), Color(0xFFF0F9FF))
    }
    BackgroundPreset.COTTON_CANDY -> if (darkTheme) {
        listOf(Color(0xFF1E3A6E), Color(0xFF3E2058), Color(0xFF4A1837))
    } else {
        listOf(Color(0xFFBAE6FD), Color(0xFFE9D5FF), Color(0xFFFBCFE8))
    }
    BackgroundPreset.NORTHERN_LIGHTS -> if (darkTheme) {
        listOf(Color(0xFF064E3B), Color(0xFF0C4A6E), Color(0xFF2E1065))
    } else {
        listOf(Color(0xFFA7F3D0), Color(0xFFBAE6FD), Color(0xFFDDD6FE))
    }
    BackgroundPreset.SUNSET_PRISM -> if (darkTheme) {
        listOf(Color(0xFF6B1D3B), Color(0xFF4C1D4B), Color(0xFF1D1B44))
    } else {
        listOf(Color(0xFFFECDD3), Color(0xFFFDE68A), Color(0xFFDDD6FE))
    }
    BackgroundPreset.WALLPAPER_SKY_CLOUDS -> listOf(Color(0xFF38BDF8), Color(0xFF0284C7))
    BackgroundPreset.WALLPAPER_AURORA_WAVES -> listOf(Color(0xFF0EA5E9), Color(0xFF7C3AED), Color(0xFFFB7185))
    BackgroundPreset.CUSTOM_PHOTO -> listOf(colorScheme.primaryContainer, colorScheme.surfaceContainer)
}
