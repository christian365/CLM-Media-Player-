package com.example.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ClosedCaptionDisabled
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.data.local.SubtitleStylePreset
import com.example.data.local.UserPreferences
import com.example.media.SleepTimerState
import com.example.playback.PlaybackUiState
import com.example.playback.SubtitleOverlayHelper
import com.example.playback.SubtitleTrackOption
import com.example.playback.VideoResizeMode
import com.example.ui.components.FormatBadgePill
import com.example.ui.components.formatDurationMs
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    player: Player?,
    playbackState: PlaybackUiState,
    preferences: UserPreferences,
    sleepTimerState: SleepTimerState,
    onNavigateBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onCycleResizeMode: () -> Unit,
    onSelectSubtitleTrack: (SubtitleTrackOption?) -> Unit,
    onPickSidecarSubtitleUri: (Uri) -> Unit,
    onUpdateSubtitleSize: (Float) -> Unit,
    onUpdateSubtitlePreset: (SubtitleStylePreset) -> Unit,
    onOpenSpeedSheet: () -> Unit,
    onOpenSleepTimerSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var controlsVisible by remember { mutableStateOf(true) }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var isLandscapeForced by remember { mutableStateOf(false) }

    val subtitlePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            onPickSidecarSubtitleUri(uri)
        }
    }

    // Immersive fullscreen system bars while viewing video
    DisposableEffect(activity) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Auto-hide controls after 4 seconds when playing
    LaunchedEffect(controlsVisible, playbackState.isPlaying, showSubtitleSheet) {
        if (controlsVisible && playbackState.isPlaying && !showSubtitleSheet) {
            delay(4000L)
            controlsVisible = false
        }
    }

    BackHandler {
        onNavigateBack()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { controlsVisible = !controlsVisible },
                    onDoubleTap = { offset ->
                        if (offset.x < size.width / 2f) {
                            onSeekBy(-10_000L)
                        } else {
                            onSeekBy(10_000L)
                        }
                    },
                )
            }
            .testTag("video_player_surface"),
    ) {
        // Media3 PlayerView with built-in SurfaceView + SubtitleView (CaptionStyleCompat)
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    keepScreenOn = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
            update = { view ->
                if (view.player !== player) {
                    view.player = player
                }
                view.resizeMode = when (playbackState.videoResizeMode) {
                    VideoResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    VideoResizeMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    VideoResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    VideoResizeMode.RATIO_16_9 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                    VideoResizeMode.RATIO_4_3 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
                }
                SubtitleOverlayHelper.applyStyle(
                    subtitleView = view.subtitleView,
                    textSizeSp = preferences.subtitleTextSizeSp,
                    preset = preferences.subtitleStylePreset,
                )
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Controls Overlay
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.72f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.82f),
                            )
                        )
                    ),
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("video_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to library",
                            tint = Color.White,
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playbackState.currentTrack?.title ?: "Video Player",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (playbackState.codecSummary.isNotBlank()) {
                            Text(
                                text = playbackState.codecSummary,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    FormatBadgePill(
                        label = playbackState.videoResizeMode.label,
                        containerColor = Color.White.copy(alpha = 0.16f),
                        contentColor = Color.White,
                        modifier = Modifier.clickable(onClick = onCycleResizeMode),
                    )

                    IconButton(
                        onClick = {
                            isLandscapeForced = !isLandscapeForced
                            activity?.requestedOrientation = if (isLandscapeForced) {
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            }
                        },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ScreenRotation,
                            contentDescription = "Toggle orientation",
                            tint = Color.White,
                        )
                    }
                }

                // Center Play/Seek Controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { onSeekBy(-10_000L) },
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("video_rewind_10"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Replay10,
                            contentDescription = "Rewind 10 seconds",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp),
                        )
                    }

                    FilledIconButton(
                        onClick = onTogglePlayPause,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier
                            .size(68.dp)
                            .testTag("video_play_pause"),
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(38.dp),
                        )
                    }

                    IconButton(
                        onClick = { onSeekBy(10_000L) },
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("video_forward_10"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Forward10,
                            contentDescription = "Forward 10 seconds",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }

                // Bottom Bar (Scrubber + Utility Actions)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .align(Alignment.BottomCenter),
                ) {
                    val duration = playbackState.durationMs.coerceAtLeast(1L)
                    var isDragging by remember { mutableStateOf(false) }
                    var dragFraction by remember { mutableFloatStateOf(0f) }
                    val sliderVal = if (isDragging) {
                        dragFraction
                    } else {
                        (playbackState.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    }

                    Slider(
                        value = sliderVal,
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
                            inactiveTrackColor = Color.White.copy(alpha = 0.32f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val posText = formatDurationMs(
                            if (isDragging) (dragFraction * duration).toLong() else playbackState.positionMs
                        )
                        Text(
                            text = "$posText / ${formatDurationMs(playbackState.durationMs)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = onCycleResizeMode,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("video_aspect_button"),
                            ) {
                                Icon(Icons.Rounded.AspectRatio, contentDescription = "Aspect ratio", tint = Color.White)
                            }
                            IconButton(
                                onClick = onOpenSpeedSheet,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("video_speed_button"),
                            ) {
                                Icon(Icons.Rounded.Speed, contentDescription = "Playback speed", tint = Color.White)
                            }
                            IconButton(
                                onClick = onOpenSleepTimerSheet,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("video_sleep_button"),
                            ) {
                                Icon(
                                    Icons.Rounded.Nightlight,
                                    contentDescription = "Sleep timer",
                                    tint = if (sleepTimerState !is SleepTimerState.Off) {
                                        MaterialTheme.colorScheme.secondary
                                    } else {
                                        Color.White
                                    },
                                )
                            }
                            val hasSelectedSub = playbackState.subtitleTracks.any { it.isSelected }
                            IconButton(
                                onClick = { showSubtitleSheet = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("video_subtitles_button"),
                            ) {
                                Icon(
                                    imageVector = if (hasSelectedSub) {
                                        Icons.Rounded.ClosedCaption
                                    } else {
                                        Icons.Rounded.ClosedCaptionDisabled
                                    },
                                    contentDescription = stringResource(R.string.subtitles_title),
                                    tint = if (hasSelectedSub) MaterialTheme.colorScheme.primary else Color.White,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSubtitleSheet) {
        VideoSubtitlesBottomSheet(
            subtitleTracks = playbackState.subtitleTracks,
            sidecarCount = playbackState.sidecarSubtitles.size,
            textSizeSp = preferences.subtitleTextSizeSp,
            stylePreset = preferences.subtitleStylePreset,
            onDismiss = { showSubtitleSheet = false },
            onSelectTrack = onSelectSubtitleTrack,
            onPickSidecarFile = {
                subtitlePickerLauncher.launch(
                    arrayOf(
                        "application/x-subrip",
                        "text/vtt",
                        "text/x-ssa",
                        "text/plain",
                        "application/octet-stream",
                        "*/*",
                    )
                )
            },
            onUpdateTextSize = onUpdateSubtitleSize,
            onUpdateStylePreset = onUpdateSubtitlePreset,
        )
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun VideoSubtitlesBottomSheet(
    subtitleTracks: List<SubtitleTrackOption>,
    sidecarCount: Int,
    textSizeSp: Float,
    stylePreset: SubtitleStylePreset,
    onDismiss: () -> Unit,
    onSelectTrack: (SubtitleTrackOption?) -> Unit,
    onPickSidecarFile: () -> Unit,
    onUpdateTextSize: (Float) -> Unit,
    onUpdateStylePreset: (SubtitleStylePreset) -> Unit,
) {
    val anySelected = subtitleTracks.any { it.isSelected }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
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
                Text(stringResource(R.string.subtitles_title), style = MaterialTheme.typography.titleLarge)
                if (sidecarCount > 0) {
                    FormatBadgePill(label = "$sidecarCount sidecar loaded")
                }
            }

            OutlinedButton(
                onClick = onPickSidecarFile,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pick_sidecar_subtitle_button"),
            ) {
                Icon(Icons.Rounded.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.load_sidecar_subtitle))
            }

            LazyColumn(
                modifier = Modifier.heightIn(max = 200.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (!anySelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectTrack(null)
                                onDismiss()
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.subtitles_off), style = MaterialTheme.typography.titleSmall)
                            if (!anySelected) {
                                Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                items(subtitleTracks) { trackOpt ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (trackOpt.isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectTrack(trackOpt)
                                onDismiss()
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(trackOpt.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            if (trackOpt.isSelected) {
                                Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Subtitle Size", style = MaterialTheme.typography.labelLarge)
                Text("${textSizeSp.toInt()} sp", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = textSizeSp,
                onValueChange = onUpdateTextSize,
                valueRange = 12f..32f,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SubtitleStylePreset.entries.forEach { preset ->
                    FilterChip(
                        selected = preset == stylePreset,
                        onClick = { onUpdateStylePreset(preset) },
                        label = { Text(preset.label) },
                    )
                }
            }

            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Done")
            }
        }
    }
}
