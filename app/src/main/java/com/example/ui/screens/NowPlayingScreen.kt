package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.example.R
import com.example.lyrics.LrcParser
import com.example.lyrics.LyricsResult
import com.example.media.LocalArtworkLoader
import com.example.media.SleepTimerState
import com.example.media.rememberTrackArtwork
import com.example.playback.PlaybackUiState
import com.example.ui.components.FormatBadgePill
import com.example.ui.components.TrackArtworkThumb
import com.example.ui.components.formatDurationMs
import com.example.ui.components.macGlassBorderColor
import com.example.ui.theme.GlassTier
import com.example.ui.theme.LocalCadenceAppearance
import com.example.ui.theme.MacBlurredArtworkLayer
import com.example.ui.theme.MacGlassContainer
import com.example.ui.theme.macGlassSurface

@Composable
fun NowPlayingScreen(
    playbackState: PlaybackUiState,
    sleepTimerState: SleepTimerState,
    artworkLoader: LocalArtworkLoader,
    onNavigateBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenSpeedPitch: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenAddToPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = playbackState.currentTrack
    var showLyrics by remember { mutableStateOf(false) }

    val hasBg = LocalCadenceAppearance.current.hasDecorativeBackground
    val ambientArtwork = track?.let {
        rememberTrackArtwork(loader = artworkLoader, track = it, targetSizePx = 256)
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Full-bleed dynamic blurred artwork glass backdrop
        MacBlurredArtworkLayer(bitmap = ambientArtwork)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        if (hasBg || ambientArtwork != null) {
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.16f),
                                Color.Transparent,
                                MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.34f),
                            )
                        } else {
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                                MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.72f),
                            )
                        }
                    )
                ),
        )
        if (track == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Rounded.Album, contentDescription = null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text("Nothing playing yet", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close")
                }
            }
            return@Box
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .align(Alignment.Center)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val glassBorder = macGlassBorderColor()

            // Top Bar (macOS frosted sheet header)
            val headerShape = RoundedCornerShape(18.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .macGlassSurface(shape = headerShape, tier = GlassTier.PANEL)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("now_playing_back"),
                ) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Collapse player")
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.now_playing),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (playbackState.codecSummary.isNotBlank()) {
                        Text(
                            text = playbackState.codecSummary,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Cover / Lyrics toggle pill
                val pillShape = RoundedCornerShape(20.dp)
                Surface(
                    shape = pillShape,
                    color = Color.Transparent,
                    modifier = Modifier
                        .macGlassSurface(shape = pillShape, tier = GlassTier.CARD, tintedPrimary = showLyrics)
                        .clickable { showLyrics = !showLyrics }
                        .testTag("toggle_lyrics_button"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lyrics,
                            contentDescription = "Toggle lyrics",
                            tint = if (showLyrics) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = if (showLyrics) stringResource(R.string.artwork_tab) else stringResource(R.string.lyrics_tab),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Center Stage: Artwork OR Embedded Lyrics
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(targetState = showLyrics, label = "cover_lyrics_switch") { lyricsVisible ->
                    if (!lyricsVisible) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.84f)
                                    .aspectRatio(1f)
                                    .shadow(20.dp, RoundedCornerShape(28.dp), clip = false)
                                    .macGlassSurface(shape = RoundedCornerShape(28.dp), tier = GlassTier.CARD)
                                    .padding(6.dp),
                            ) {
                                TrackArtworkThumb(
                                    loader = artworkLoader,
                                    track = track,
                                    modifier = Modifier.fillMaxSize(),
                                    cornerRadius = 22.dp,
                                    targetSizePx = 640,
                                    showFallbackBadge = true,
                                )
                            }
                        }
                    } else {
                        LyricsPanel(
                            lyrics = playbackState.lyrics,
                            isLoading = playbackState.isLoadingLyrics,
                            positionMs = playbackState.positionMs,
                            onSeekToTimestamp = onSeekTo,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Frosted Glass Player Transport & Seek Deck
            val deckShape = RoundedCornerShape(26.dp)
            MacGlassContainer(
                shape = deckShape,
                tier = GlassTier.PLAYER_DECK,
                ambientBitmap = ambientArtwork,
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(12.dp, deckShape, clip = false),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    // Track Info
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${track.artist} — ${track.album}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(Modifier.height(6.dp))

                    // Seek Bar
                    val duration = playbackState.durationMs.coerceAtLeast(1L)
                    var isDragging by remember { mutableStateOf(false) }
                    var dragFraction by remember { mutableFloatStateOf(0f) }
                    val currentFraction = if (isDragging) {
                        dragFraction
                    } else {
                        (playbackState.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    }

                    Slider(
                        value = currentFraction,
                        onValueChange = {
                            isDragging = true
                            dragFraction = it
                        },
                        onValueChangeFinished = {
                            onSeekTo((dragFraction * duration).toLong())
                            isDragging = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("now_playing_seek_slider"),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        val displayedPos = if (isDragging) (dragFraction * duration).toLong() else playbackState.positionMs
                        Text(
                            text = formatDurationMs(displayedPos),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = formatDurationMs(playbackState.durationMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Primary Transport Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = onToggleShuffle,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("now_playing_shuffle"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (playbackState.shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        IconButton(
                            onClick = onSkipPrevious,
                            modifier = Modifier
                                .size(54.dp)
                                .testTag("now_playing_prev"),
                        ) {
                            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(34.dp))
                        }

                        FilledIconButton(
                            onClick = onTogglePlayPause,
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            modifier = Modifier
                                .size(66.dp)
                                .testTag("now_playing_play_pause"),
                        ) {
                            Icon(
                                imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(36.dp),
                            )
                        }

                        IconButton(
                            onClick = onSkipNext,
                            modifier = Modifier
                                .size(54.dp)
                                .testTag("now_playing_next"),
                        ) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = "Next", modifier = Modifier.size(34.dp))
                        }

                        IconButton(
                            onClick = onCycleRepeat,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("now_playing_repeat"),
                        ) {
                            val (icon, tint) = when (playbackState.repeatMode) {
                                Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne to MaterialTheme.colorScheme.primary
                                Player.REPEAT_MODE_ALL -> Icons.Rounded.Repeat to MaterialTheme.colorScheme.primary
                                else -> Icons.Rounded.Repeat to MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Icon(icon, contentDescription = "Repeat mode", tint = tint)
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Secondary Audio Tools Row (Frosted Glass Dock)
            val toolsShape = RoundedCornerShape(18.dp)
            MacGlassContainer(
                shape = toolsShape,
                tier = GlassTier.PANEL,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onOpenSpeedPitch, modifier = Modifier.testTag("audio_speed_button")) {
                        Icon(
                            Icons.Rounded.Speed,
                            contentDescription = stringResource(R.string.speed_pitch_title),
                            tint = if (playbackState.playbackSpeed != 1.0f || playbackState.playbackPitch != 1.0f) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    IconButton(onClick = onOpenEqualizer, modifier = Modifier.testTag("audio_eq_button")) {
                        Icon(Icons.Rounded.Equalizer, contentDescription = stringResource(R.string.equalizer_title))
                    }
                    IconButton(onClick = onOpenSleepTimer, modifier = Modifier.testTag("audio_sleep_button")) {
                        Icon(
                            Icons.Rounded.Nightlight,
                            contentDescription = stringResource(R.string.sleep_timer_title),
                            tint = if (sleepTimerState !is SleepTimerState.Off) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    IconButton(onClick = onOpenQueue, modifier = Modifier.testTag("audio_queue_button")) {
                        Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = stringResource(R.string.queue_title))
                    }
                    IconButton(onClick = onOpenAddToPlaylist, modifier = Modifier.testTag("audio_playlist_button")) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = "Add to playlist")
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsPanel(
    lyrics: LyricsResult,
    isLoading: Boolean,
    positionMs: Long,
    onSeekToTimestamp: (Long) -> Unit,
) {
    val panelShape = RoundedCornerShape(22.dp)
    MacGlassContainer(
        shape = panelShape,
        tier = GlassTier.PLAYER_DECK,
        modifier = Modifier
            .fillMaxSize()
            .testTag("lyrics_panel"),
    ) {
        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            lyrics is LyricsResult.Synchronized -> {
                val activeIndex = remember(lyrics.lines, positionMs) {
                    LrcParser.activeLineIndex(lyrics.lines, positionMs)
                }
                val listState = rememberLazyListState()

                LaunchedEffect(activeIndex) {
                    if (activeIndex >= 0 && activeIndex < lyrics.lines.size) {
                        val target = (activeIndex - 2).coerceAtLeast(0)
                        listState.animateScrollToItem(target)
                    }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FormatBadgePill(label = stringResource(R.string.synced_lyrics_badge))
                        Text(
                            text = lyrics.sourceTag,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(lyrics.lines, key = { idx, line -> "${idx}_${line.timestampMs}" }) { index, line ->
                            val isActive = index == activeIndex
                            val isPast = activeIndex >= 0 && index < activeIndex
                            Text(
                                text = line.text,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = if (isActive) 21.sp else 17.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                    lineHeight = if (isActive) 28.sp else 24.sp,
                                ),
                                color = when {
                                    isActive -> MaterialTheme.colorScheme.primary
                                    isPast -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f)
                                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = line.timestampMs != null) {
                                        line.timestampMs?.let(onSeekToTimestamp)
                                    }
                                    .padding(vertical = 4.dp),
                            )
                        }
                    }
                }
            }

            lyrics is LyricsResult.Unsynchronized -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FormatBadgePill(
                            label = stringResource(R.string.plain_lyrics_badge),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            text = lyrics.sourceTag,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(lyrics.lines) { _, line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                            )
                        }
                    }
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lyrics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(44.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.no_lyrics_found),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.no_lyrics_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
