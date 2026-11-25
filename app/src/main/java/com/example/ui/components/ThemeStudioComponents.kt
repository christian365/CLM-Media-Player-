package com.example.ui.components

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.BackgroundPreset
import com.example.data.local.ThemeMode
import com.example.data.local.ThemePalette
import com.example.data.local.UserPreferences
import com.example.media.BackgroundImageStorage
import com.example.ui.theme.LocalCadenceAppearance
import com.example.ui.theme.backgroundGradientColors
import com.example.ui.theme.previewColors
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeCustomizerContent(
    preferences: UserPreferences,
    onThemeModeChange: (ThemeMode) -> Unit,
    onThemePaletteChange: (ThemePalette) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onBackgroundPresetChange: (BackgroundPreset) -> Unit,
    onBackgroundOverlayOpacityChange: (Float) -> Unit,
    onPickCustomBackgroundUri: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    val appearance = LocalCadenceAppearance.current
    val darkTheme = appearance.darkTheme

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            onPickCustomBackgroundUri(uri)
        }
    }

    val customThumbnailBitmap by produceState<Bitmap?>(
        initialValue = null,
        preferences.customBackgroundPath,
    ) {
        value = BackgroundImageStorage.loadBitmap(preferences.customBackgroundPath, maxDimensionPx = 360)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Live Mini Preview Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(16.dp),
                ),
        ) {
            AppBackgroundLayer(
                preset = preferences.backgroundPreset,
                overlayOpacity = preferences.backgroundOverlayOpacity,
                customBackgroundPath = preferences.customBackgroundPath,
                modifier = Modifier.fillMaxSize(),
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (preferences.dynamicColorEnabled) "Material You" else preferences.themePalette.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${preferences.backgroundPreset.label} • ${if (darkTheme) "Dark" else "Light"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FormatBadgePill(label = "Preview")
            }
        }

        // Brightness Mode
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.theme_mode_label),
                style = MaterialTheme.typography.labelLarge,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    val (label, icon) = when (mode) {
                        ThemeMode.SYSTEM -> "System" to Icons.Rounded.BrightnessAuto
                        ThemeMode.DARK -> "Dark" to Icons.Rounded.DarkMode
                        ThemeMode.LIGHT -> "Light" to Icons.Rounded.LightMode
                    }
                    FilterChip(
                        selected = preferences.themeMode == mode,
                        onClick = { onThemeModeChange(mode) },
                        leadingIcon = {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        modifier = Modifier.testTag("theme_mode_${mode.name.lowercase()}"),
                    )
                }
            }
        }

        // Colorful Theme Swatches
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.theme_palette_label),
                style = MaterialTheme.typography.labelLarge,
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
            ) {
                items(ThemePalette.entries, key = { it.name }) { palette ->
                    val preview = palette.previewColors(darkTheme)
                    val isSelected = !preferences.dynamicColorEnabled && preferences.themePalette == palette
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(74.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onThemePaletteChange(palette) }
                            .padding(vertical = 6.dp, horizontal = 4.dp)
                            .testTag("theme_palette_${palette.name.lowercase()}"),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(preview.primary, preview.secondary, preview.backdrop),
                                    ),
                                )
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = "Selected",
                                            tint = preview.primary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = palette.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Material You Dynamic Color", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Blend device wallpaper colors on Android 12+",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = preferences.dynamicColorEnabled,
                    onCheckedChange = onDynamicColorChange,
                    modifier = Modifier.testTag("dynamic_color_switch"),
                )
            }
        }

        // Background Selection Gallery
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.background_selection_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = preferences.backgroundPreset.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
            ) {
                items(BackgroundPreset.entries, key = { it.name }) { preset ->
                    val isSelected = preferences.backgroundPreset == preset
                    BackgroundOptionCard(
                        preset = preset,
                        isSelected = isSelected,
                        darkTheme = darkTheme,
                        customThumbnail = customThumbnailBitmap,
                        onClick = {
                            if (preset == BackgroundPreset.CUSTOM_PHOTO && preferences.customBackgroundPath.isNullOrBlank()) {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            } else {
                                onBackgroundPresetChange(preset)
                            }
                        },
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pick_custom_background_button"),
            ) {
                Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.pick_custom_background))
            }
        }

        // Scrim Opacity Slider (when decorative background is active)
        if (preferences.backgroundPreset != BackgroundPreset.THEME_SOLID) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.background_dim_label),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = "${(preferences.backgroundOverlayOpacity * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = preferences.backgroundOverlayOpacity,
                    onValueChange = onBackgroundOverlayOpacityChange,
                    valueRange = 0.15f..0.85f,
                    modifier = Modifier.testTag("background_opacity_slider"),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Vivid backdrop", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("High contrast", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun BackgroundOptionCard(
    preset: BackgroundPreset,
    isSelected: Boolean,
    darkTheme: Boolean,
    customThumbnail: Bitmap?,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(82.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("bg_preset_${preset.name.lowercase()}"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(98.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(
                    width = if (isSelected) 2.5.dp else 1.dp,
                    color = if (isSelected) colorScheme.primary else colorScheme.outlineVariant,
                    shape = RoundedCornerShape(14.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            when (preset) {
                BackgroundPreset.WALLPAPER_SKY_CLOUDS -> {
                    Image(
                        painter = painterResource(id = R.drawable.img_bg_sky_clouds),
                        contentDescription = preset.label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                BackgroundPreset.WALLPAPER_AURORA_WAVES -> {
                    Image(
                        painter = painterResource(id = R.drawable.img_bg_aurora_waves),
                        contentDescription = preset.label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                BackgroundPreset.CUSTOM_PHOTO -> {
                    if (customThumbnail != null) {
                        Image(
                            bitmap = customThumbnail.asImageBitmap(),
                            contentDescription = preset.label,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PhotoLibrary,
                                contentDescription = preset.label,
                                tint = colorScheme.primary,
                            )
                        }
                    }
                }
                else -> {
                    val stops = backgroundGradientColors(preset, colorScheme, darkTheme)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(stops)),
                    )
                }
            }

            if (isSelected) {
                Surface(
                    shape = CircleShape,
                    color = colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Active background",
                            tint = colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = preset.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeStudioBottomSheet(
    preferences: UserPreferences,
    onDismiss: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onThemePaletteChange: (ThemePalette) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onBackgroundPresetChange: (BackgroundPreset) -> Unit,
    onBackgroundOverlayOpacityChange: (Float) -> Unit,
    onPickCustomBackgroundUri: (Uri) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Rounded.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = stringResource(R.string.appearance_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            ThemeCustomizerContent(
                preferences = preferences,
                onThemeModeChange = onThemeModeChange,
                onThemePaletteChange = onThemePaletteChange,
                onDynamicColorChange = onDynamicColorChange,
                onBackgroundPresetChange = onBackgroundPresetChange,
                onBackgroundOverlayOpacityChange = onBackgroundOverlayOpacityChange,
                onPickCustomBackgroundUri = onPickCustomBackgroundUri,
            )
        }
    }
}
