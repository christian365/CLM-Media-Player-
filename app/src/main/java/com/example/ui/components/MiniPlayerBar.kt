package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.media.LocalArtworkLoader
import com.example.media.rememberTrackArtwork
import com.example.playback.PlaybackUiState
import com.example.ui.theme.GlassTier
import com.example.ui.theme.MacGlassContainer

@Composable
fun MiniPlayerBar(
    playbackState: PlaybackUiState,
    artworkLoader: LocalArtworkLoader,
    onOpenNowPlaying: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = playbackState.currentTrack ?: return
    val progress = if (playbackState.durationMs > 0L) {
        (playbackState.positionMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val shape = RoundedCornerShape(16.dp)
    val ambientArtwork = rememberTrackArtwork(loader = artworkLoader, track = track, targetSizePx = 128)

    MacGlassContainer(
        shape = shape,
        tier = GlassTier.PLAYER_DECK,
        ambientBitmap = ambientArtwork,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .shadow(10.dp, shape, clip = false)
            .clickable(onClick = onOpenNowPlaying)
            .testTag("mini_player_bar"),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TrackArtworkThumb(
                    loader = artworkLoader,
                    track = track,
                    modifier = Modifier.size(44.dp),
                    cornerRadius = 10.dp,
                    targetSizePx = 128,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(
                        track.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${track.artist} • ${formatExtensionTag(track)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onTogglePlayPause, modifier = Modifier.testTag("mini_player_play_pause")) {
                    Icon(
                        if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(30.dp),
                    )
                }
                IconButton(onClick = onSkipNext, modifier = Modifier.testTag("mini_player_next")) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "Next track",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            )
        }
    }
}
