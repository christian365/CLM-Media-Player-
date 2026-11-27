package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.BackgroundPreset
import com.example.data.local.FolderFilterEntity
import com.example.data.local.SubtitleStylePreset
import com.example.data.local.ThemeMode
import com.example.data.local.ThemePalette
import com.example.data.local.UserPreferences
import com.example.ui.components.MacWindowTitleBar
import com.example.ui.components.ThemeCustomizerContent
import com.example.ui.theme.GlassTier
import com.example.ui.theme.macGlassSurface

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    folderFilters: List<FolderFilterEntity>,
    onToggleFolderIncluded: (String, Boolean) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onThemePaletteChange: (ThemePalette) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onBackgroundPresetChange: (BackgroundPreset) -> Unit,
    onBackgroundOverlayOpacityChange: (Float) -> Unit,
    onPickCustomBackgroundUri: (Uri) -> Unit,
    onSubtitlesDefaultChange: (Boolean) -> Unit,
    onSubtitleSizeChange: (Float) -> Unit,
    onSubtitlePresetChange: (SubtitleStylePreset) -> Unit,
    onPreferExtensionDecodersChange: (Boolean) -> Unit,
    onOnlineLyricsChange: (Boolean) -> Unit = {},
    onOnlineSubtitlesChange: (Boolean) -> Unit = {},
    onOnlineCodecLookupChange: (Boolean) -> Unit = {},
    onOpenSubtitlesApiKeyChange: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 148.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            MacWindowTitleBar(
                title = stringResource(R.string.settings_title),
                subtitle = stringResource(R.string.tagline_offline),
            )
        }

        // Theme & Background Studio
        item {
            SettingsCard(
                icon = Icons.Rounded.Palette,
                title = stringResource(R.string.appearance_title),
            ) {
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

        // Codec & FFmpeg Engine
        item {
            SettingsCard(
                icon = Icons.Rounded.GraphicEq,
                title = stringResource(R.string.codec_settings_title),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Prefer FFmpeg Extension Decoders", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "When off, hardware MediaCodec runs first with automatic software and FFmpeg fallback. Takes effect immediately.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = preferences.preferExtensionDecoders,
                        onCheckedChange = onPreferExtensionDecodersChange,
                        modifier = Modifier.testTag("ffmpeg_prefer_switch"),
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                Text("Bundled Media3 + FFmpeg Codec Matrix", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Audio: MP3, AAC/M4A, FLAC, ALAC, WAV, OGG/Vorbis, Opus, WMA, PCM/AIFF\n" +
                        "Video: MP4, MKV, WEBM, AVI, MOV, 3GP (H.264/AVC, H.265/HEVC, VP8, VP9, AV1, AC3/EAC3, DTS)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Online Services (Lyrics, Subtitles & Codec Help)
        item {
            SettingsCard(
                icon = Icons.Rounded.Cloud,
                title = stringResource(R.string.online_services_title),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Online Lyrics (LRCLIB)", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Fetch synced LRC and plain lyrics automatically when embedded tags are missing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = preferences.onlineLyricsEnabled,
                        onCheckedChange = onOnlineLyricsChange,
                        modifier = Modifier.testTag("online_lyrics_switch"),
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Online Subtitles (Podnapisi / OpenSubtitles)", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Search and download .srt/.ass/.vtt subtitles directly inside the video player.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = preferences.onlineSubtitlesEnabled,
                        onCheckedChange = onOnlineSubtitlesChange,
                        modifier = Modifier.testTag("online_subtitles_switch"),
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Online Codec Help & Diagnostics", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Look up codec compatibility details when a file uses an unsupported stream format.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = preferences.onlineCodecLookupEnabled,
                        onCheckedChange = onOnlineCodecLookupChange,
                        modifier = Modifier.testTag("online_codec_switch"),
                    )
                }

                if (preferences.onlineSubtitlesEnabled) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    var apiKeyDraft by remember(preferences.openSubtitlesApiKey) {
                        mutableStateOf(preferences.openSubtitlesApiKey)
                    }
                    Text("OpenSubtitles API Key (Optional)", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Podnapisi works without an account. Add a free OpenSubtitles.com API key for extra subtitle results.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = apiKeyDraft,
                        onValueChange = { apiKeyDraft = it },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        placeholder = { Text("Optional OpenSubtitles API key") },
                        modifier = Modifier.fillMaxWidth().testTag("opensubtitles_api_key_input"),
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onOpenSubtitlesApiKeyChange(apiKeyDraft) },
                            enabled = apiKeyDraft.trim() != preferences.openSubtitlesApiKey,
                        ) {
                            Text("Save Key")
                        }
                        if (preferences.openSubtitlesApiKey.isNotBlank()) {
                            TextButton(onClick = { apiKeyDraft = ""; onOpenSubtitlesApiKeyChange("") }) {
                                Text("Clear")
                            }
                        }
                    }
                }
            }
        }

        // Subtitle Defaults
        item {
            SettingsCard(
                icon = Icons.Rounded.ClosedCaption,
                title = stringResource(R.string.subtitle_defaults_title),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable Text Tracks by Default", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Automatically activate embedded SRT, ASS/SSA, WebVTT, CEA-608/708, or matching sidecar subtitles.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = preferences.subtitlesEnabledByDefault,
                        onCheckedChange = onSubtitlesDefaultChange,
                        modifier = Modifier.testTag("subtitles_default_switch"),
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Default Caption Size", style = MaterialTheme.typography.labelLarge)
                    Text("${preferences.subtitleTextSizeSp.toInt()} sp", style = MaterialTheme.typography.labelMedium)
                }
                Slider(
                    value = preferences.subtitleTextSizeSp,
                    onValueChange = onSubtitleSizeChange,
                    valueRange = 12f..32f,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SubtitleStylePreset.entries.forEach { preset ->
                        FilterChip(
                            selected = preferences.subtitleStylePreset == preset,
                            onClick = { onSubtitlePresetChange(preset) },
                            label = { Text(preset.label) },
                        )
                    }
                }
            }
        }

        // Folder Include / Exclude Filters
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text(stringResource(R.string.folder_filters_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Toggle folders to include or hide them from library views without deleting files.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (folderFilters.isEmpty()) {
            item {
                val emptyShape = RoundedCornerShape(14.dp)
                Surface(
                    shape = emptyShape,
                    color = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .macGlassSurface(shape = emptyShape, tier = GlassTier.CARD),
                ) {
                    Text(
                        "No indexed folders yet. Grant media access and rescan your library.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
        } else {
            items(folderFilters, key = { it.folderPath }) { folder ->
                val folderShape = RoundedCornerShape(14.dp)
                Surface(
                    shape = folderShape,
                    color = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .macGlassSurface(shape = folderShape, tier = GlassTier.CARD),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(folder.folderName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${folder.itemCount} items • ${folder.folderPath}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Switch(
                            checked = folder.isIncluded,
                            onCheckedChange = { onToggleFolderIncluded(folder.folderPath, it) },
                            modifier = Modifier.testTag("folder_toggle_${folder.folderName}"),
                        )
                    }
                }
            }
        }

        // Privacy & Edge Cases
        item {
            SettingsCard(
                icon = Icons.Rounded.Shield,
                title = "Privacy & Playback Resilience",
            ) {
                EdgeCaseBullet(
                    title = "Scoped Storage",
                    detail = "Uses granular READ_MEDIA_AUDIO / READ_MEDIA_VIDEO on Android 13+ and SAF OpenDocument for external .srt/.vtt/.ass sidecars without broad file permissions.",
                )
                Spacer(Modifier.height(8.dp))
                EdgeCaseBullet(
                    title = "Online Lookup Privacy",
                    detail = "Network requests happen only over HTTPS for lyrics (LRCLIB), subtitles (Podnapisi / optional OpenSubtitles), and codec reference lookups, with zero ads or tracking.",
                )
                Spacer(Modifier.height(8.dp))
                EdgeCaseBullet(
                    title = "Corrupt or Unsupported Files",
                    detail = "Hardware-to-FFmpeg fallback activates automatically; unplayable files surface Codec Help diagnostics, external player handoff, and Skip Track recovery.",
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: @Composable () -> Unit,
) {
    val cardShape = RoundedCornerShape(18.dp)
    Surface(
        shape = cardShape,
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .macGlassSurface(shape = cardShape, tier = GlassTier.PANEL),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun EdgeCaseBullet(title: String, detail: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            Icons.Rounded.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp),
        )
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
