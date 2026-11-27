package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.MediaTrackEntity
import com.example.data.local.PlaylistWithCount
import com.example.media.AudioEffectsState
import com.example.media.LocalArtworkLoader
import com.example.media.SleepTimerState
import com.example.ui.theme.GlassTier
import com.example.ui.theme.macGlassSurface
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    queue: List<MediaTrackEntity>,
    currentIndex: Int,
    artworkLoader: LocalArtworkLoader,
    onDismiss: () -> Unit,
    onSelectTrack: (Int) -> Unit,
    onMoveTrack: (Int, Int) -> Unit,
    onRemoveTrack: (Int) -> Unit,
    onSaveQueueAsPlaylist: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.88f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Playback Queue", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${queue.size} item${if (queue.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (queue.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onSaveQueueAsPlaylist,
                        modifier = Modifier.testTag("save_queue_playlist_button"),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save Playlist")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (queue.isEmpty()) {
                Text(
                    "Queue is empty.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(queue, key = { idx, t -> "${t.mediaStoreId}_$idx" }) { index, track ->
                        val isCurrent = index == currentIndex
                        val cardShape = RoundedCornerShape(12.dp)
                        Surface(
                            shape = cardShape,
                            color = Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .macGlassSurface(shape = cardShape, tier = GlassTier.CARD, tintedPrimary = isCurrent)
                                .clickable { onSelectTrack(index) },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TrackArtworkThumb(
                                    loader = artworkLoader,
                                    track = track,
                                    modifier = Modifier.size(40.dp),
                                    cornerRadius = 8.dp,
                                    targetSizePx = 100,
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${track.artist} • ${formatDurationMs(track.durationMs)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(
                                    onClick = { onMoveTrack(index, (index - 1).coerceAtLeast(0)) },
                                    enabled = index > 0,
                                ) {
                                    Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move up")
                                }
                                IconButton(
                                    onClick = { onMoveTrack(index, (index + 1).coerceAtMost(queue.lastIndex)) },
                                    enabled = index < queue.lastIndex,
                                ) {
                                    Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move down")
                                }
                                IconButton(onClick = { onRemoveTrack(index) }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Remove from queue")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EqualizerBottomSheet(
    state: AudioEffectsState,
    onDismiss: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectPreset: (Int) -> Unit,
    onBandLevelChange: (Short, Short) -> Unit,
    onBassBoostChange: (Int) -> Unit,
    onLoudnessChange: (Int) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.88f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.Equalizer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Equalizer & Audio FX", style = MaterialTheme.typography.titleLarge)
                }
                Switch(
                    checked = state.isEnabled,
                    onCheckedChange = onToggleEnabled,
                    enabled = state.isSupported,
                    modifier = Modifier.testTag("eq_enable_switch"),
                )
            }

            if (!state.isSupported) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Text(
                        text = state.errorHint ?: "Start playing audio to attach device Equalizer & Bass Boost effects.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            } else {
                if (state.presets.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        state.presets.forEachIndexed { idx, name ->
                            FilterChip(
                                selected = state.selectedPresetIndex == idx,
                                onClick = { onSelectPreset(idx) },
                                label = { Text(name) },
                            )
                        }
                    }
                }

                HorizontalDivider()

                state.bands.forEach { band ->
                    val freqLabel = if (band.centerFreqHz >= 1000) {
                        "${band.centerFreqHz / 1000} kHz"
                    } else {
                        "${band.centerFreqHz} Hz"
                    }
                    val db = band.levelMb / 100f
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(freqLabel, style = MaterialTheme.typography.labelMedium)
                            Text(
                                String.format(Locale.US, "%+.1f dB", db),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Slider(
                            value = band.levelMb.toFloat(),
                            onValueChange = { onBandLevelChange(band.index, it.toInt().toShort()) },
                            valueRange = band.minLevelMb.toFloat()..band.maxLevelMb.toFloat(),
                            enabled = state.isEnabled,
                        )
                    }
                }

                HorizontalDivider()

                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Bass Boost", style = MaterialTheme.typography.labelLarge)
                        Text("${state.bassBoostStrength / 10}%", style = MaterialTheme.typography.labelMedium)
                    }
                    Slider(
                        value = state.bassBoostStrength.toFloat(),
                        onValueChange = { onBassBoostChange(it.toInt()) },
                        valueRange = 0f..1000f,
                        enabled = state.isEnabled,
                    )
                }

                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Loudness Enhancer", style = MaterialTheme.typography.labelLarge)
                        Text("+${state.loudnessGainMb / 100} dB", style = MaterialTheme.typography.labelMedium)
                    }
                    Slider(
                        value = state.loudnessGainMb.toFloat(),
                        onValueChange = { onLoudnessChange(it.toInt()) },
                        valueRange = 0f..1500f,
                        enabled = state.isEnabled,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SleepTimerBottomSheet(
    state: SleepTimerState,
    onDismiss: () -> Unit,
    onSelectMinutes: (Int) -> Unit,
    onSelectEndOfTrack: () -> Unit,
    onCancel: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.88f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.Nightlight, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Text("Sleep Timer", style = MaterialTheme.typography.titleLarge)
            }

            when (state) {
                SleepTimerState.Off -> Text(
                    "Pause playback automatically after a set duration or when the current track finishes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                is SleepTimerState.Countdown -> Text(
                    "Pausing in ${formatDurationMs(state.remainingSeconds * 1000L)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                SleepTimerState.EndOfTrack -> Text(
                    "Playback will stop at the end of the current track.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(10, 15, 30, 45, 60, 90).forEach { mins ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            onSelectMinutes(mins)
                            onDismiss()
                        },
                        label = { Text("$mins min") },
                    )
                }
                FilterChip(
                    selected = state is SleepTimerState.EndOfTrack,
                    onClick = {
                        onSelectEndOfTrack()
                        onDismiss()
                    },
                    label = { Text("End of track") },
                )
            }

            if (state !is SleepTimerState.Off) {
                OutlinedButton(
                    onClick = {
                        onCancel()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Turn Off Timer")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SpeedPitchBottomSheet(
    speed: Float,
    pitch: Float,
    showPitchControl: Boolean = true,
    onDismiss: () -> Unit,
    onUpdate: (Float, Float) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.88f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(if (showPitchControl) "Speed & Pitch" else "Playback Speed", style = MaterialTheme.typography.titleLarge)
                }
                TextButton(onClick = { onUpdate(1.0f, 1.0f) }) {
                    Text("Reset 1.0x")
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Speed", style = MaterialTheme.typography.labelLarge)
                Text(String.format(Locale.US, "%.2fx", speed), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = speed,
                onValueChange = { onUpdate(it, pitch) },
                valueRange = 0.5f..2.25f,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { preset ->
                    FilterChip(
                        selected = kotlin.math.abs(speed - preset) < 0.03f,
                        onClick = { onUpdate(preset, pitch) },
                        label = { Text("${preset}x") },
                    )
                }
            }

            if (showPitchControl) {
                HorizontalDivider()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Pitch", style = MaterialTheme.typography.labelLarge)
                    Text(String.format(Locale.US, "%.2fx", pitch), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                }
                Slider(
                    value = pitch,
                    onValueChange = { onUpdate(speed, it) },
                    valueRange = 0.6f..1.5f,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistBottomSheet(
    playlists: List<PlaylistWithCount>,
    onDismiss: () -> Unit,
    onSelectPlaylist: (Long) -> Unit,
    onCreatePlaylist: (String) -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.88f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Add to Playlist", style = MaterialTheme.typography.titleLarge)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("New playlist name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onCreatePlaylist(newName.trim())
                            newName = ""
                            onDismiss()
                        }
                    },
                    enabled = newName.isNotBlank(),
                ) {
                    Text("Create")
                }
            }

            HorizontalDivider()

            if (playlists.isEmpty()) {
                Text(
                    "No playlists yet. Create one above.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(playlists, key = { _, p -> p.playlist.playlistId }) { _, item ->
                        val rowShape = RoundedCornerShape(12.dp)
                        Surface(
                            shape = rowShape,
                            color = Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .macGlassSurface(shape = rowShape, tier = GlassTier.CARD)
                                .clickable {
                                    onSelectPlaylist(item.playlist.playlistId)
                                    onDismiss()
                                },
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text(item.playlist.name, style = MaterialTheme.typography.titleSmall)
                                }
                                Text(
                                    "${item.trackCount} tracks",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
