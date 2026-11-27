@file:OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import com.example.ui.components.VideoGestureOverlay
import com.example.ui.components.formatDurationMs
import com.example.ui.theme.GlassTier
import com.example.ui.theme.MacGlassContainer
import kotlinx.coroutines.delay

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
    if (player == null) { onNavigateBack(); return }

    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    var controlsVisible by remember { mutableStateOf(true) }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var isLandscapeForced by remember { mutableStateOf(false) }
    var selectedTrack by remember { mutableStateOf<SubtitleTrackOption?>(null) }
    val subtitlePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> if (uri != null) onPickSidecarSubtitleUri(uri) }

    // Stop video when app goes to background
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (playbackState.currentTrack?.mediaType != com.example.data.local.MediaType.AUDIO) {
                    player.pause()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(activity) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (window != null) WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(controlsVisible, playbackState.isPlaying, showSubtitleSheet) {
        if (controlsVisible && playbackState.isPlaying && !showSubtitleSheet) { delay(4000L); controlsVisible = false }
    }

    BackHandler { onNavigateBack() }

    VideoGestureOverlay(player = player, modifier = modifier.fillMaxSize().background(Color.Black).testTag("video_player_surface")) {
        AndroidView(factory = { ctx -> PlayerView(ctx).apply { useController = false; layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT); keepScreenOn = true; setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING) } }, update = { view ->
            try {
                if (view.player !== player) view.player = player
                view.resizeMode = when (playbackState.videoResizeMode) {
                    VideoResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    VideoResizeMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    VideoResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    VideoResizeMode.RATIO_16_9 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                    VideoResizeMode.RATIO_4_3 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
                }
                SubtitleOverlayHelper.applyStyle(view.subtitleView, preferences.subtitleTextSizeSp, preferences.subtitleStylePreset)
            } catch (e: Exception) {}
        }, modifier = Modifier.fillMaxSize())

        AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.44f), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.56f))))) {
                // Floating macOS smoked glass top HUD bar
                MacGlassContainer(
                    shape = RoundedCornerShape(18.dp),
                    tier = GlassTier.HUD_DARK,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .align(Alignment.TopCenter),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        IconButton(onClick = onNavigateBack, modifier = Modifier.minimumInteractiveComponentSize().testTag("video_back_button")) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = playbackState.currentTrack?.title ?: "Video Player", style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (playbackState.codecSummary.isNotBlank()) {
                                Text(text = playbackState.codecSummary, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.78f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        FormatBadgePill(label = playbackState.videoResizeMode.label, containerColor = Color.White.copy(alpha = 0.16f), contentColor = Color.White, modifier = Modifier.clickable(onClick = onCycleResizeMode))
                        IconButton(onClick = { isLandscapeForced = !isLandscapeForced; activity?.requestedOrientation = if (isLandscapeForced) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }, modifier = Modifier.minimumInteractiveComponentSize()) {
                            Icon(Icons.Rounded.ScreenRotation, contentDescription = "Orientation", tint = Color.White)
                        }
                    }
                }

                // Floating glass center transport pods
                Row(modifier = Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    MacGlassContainer(shape = CircleShape, tier = GlassTier.HUD_DARK, modifier = Modifier.size(56.dp)) {
                        IconButton(onClick = { onSeekBy(-10_000L) }, modifier = Modifier.fillMaxSize().testTag("video_rewind_10")) {
                            Icon(Icons.Rounded.Replay10, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                    }
                    FilledIconButton(onClick = onTogglePlayPause, shape = CircleShape, colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f), contentColor = MaterialTheme.colorScheme.onPrimary), modifier = Modifier.size(68.dp).testTag("video_play_pause")) {
                        Icon(if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = if (playbackState.isPlaying) "Pause" else "Play", modifier = Modifier.size(38.dp))
                    }
                    MacGlassContainer(shape = CircleShape, tier = GlassTier.HUD_DARK, modifier = Modifier.size(56.dp)) {
                        IconButton(onClick = { onSeekBy(10_000L) }, modifier = Modifier.fillMaxSize().testTag("video_forward_10")) {
                            Icon(Icons.Rounded.Forward10, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                    }
                }

                // Floating macOS smoked glass bottom control deck
                MacGlassContainer(
                    shape = RoundedCornerShape(22.dp),
                    tier = GlassTier.HUD_DARK,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .align(Alignment.BottomCenter),
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                        val duration = playbackState.durationMs.coerceAtLeast(1L)
                        var isDragging by remember { mutableStateOf(false) }
                        var dragFraction by remember { mutableFloatStateOf(0f) }
                        val sliderVal = if (isDragging) dragFraction else (playbackState.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                        Slider(value = sliderVal, onValueChange = { isDragging = true; dragFraction = it }, onValueChangeFinished = { onSeekTo((dragFraction * duration).toLong()); isDragging = false }, colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary, inactiveTrackColor = Color.White.copy(alpha = 0.28f)), modifier = Modifier.fillMaxWidth())
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "${formatDurationMs(if (isDragging) (dragFraction * duration).toLong() else playbackState.positionMs)} / ${formatDurationMs(playbackState.durationMs)}", style = MaterialTheme.typography.labelMedium, color = Color.White)
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(onClick = onCycleResizeMode, modifier = Modifier.minimumInteractiveComponentSize().testTag("video_aspect_button")) { Icon(Icons.Rounded.AspectRatio, contentDescription = "Aspect", tint = Color.White) }
                                IconButton(onClick = onOpenSpeedSheet, modifier = Modifier.minimumInteractiveComponentSize().testTag("video_speed_button")) { Icon(Icons.Rounded.Speed, contentDescription = "Speed", tint = Color.White) }
                                IconButton(onClick = onOpenSleepTimerSheet, modifier = Modifier.minimumInteractiveComponentSize().testTag("video_sleep_button")) { Icon(Icons.Rounded.Nightlight, contentDescription = "Sleep timer", tint = if (sleepTimerState !is SleepTimerState.Off) MaterialTheme.colorScheme.secondary else Color.White) }
                                val hasSubtitles = playbackState.subtitleTracks.isNotEmpty()
                                IconButton(onClick = { showSubtitleSheet = true }, modifier = Modifier.minimumInteractiveComponentSize().testTag("video_subtitle_button")) { Icon(if (hasSubtitles && selectedTrack != null) Icons.Rounded.ClosedCaption else Icons.Rounded.ClosedCaptionDisabled, contentDescription = "Subtitles", tint = Color.White) }
                            }
                        }
                    }
                }
            }
        }

        if (showSubtitleSheet) {
            ModalBottomSheet(onDismissRequest = { showSubtitleSheet = false }, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f)) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(text = stringResource(R.string.subtitles_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 12.dp))
                    FilterChip(selected = selectedTrack == null, onClick = { selectedTrack = null; onSelectSubtitleTrack(null) }, label = { Text(stringResource(R.string.subtitles_off)) }, leadingIcon = if (selectedTrack == null) { { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) } } else null, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { items(playbackState.subtitleTracks) { track -> FilterChip(selected = selectedTrack == track, onClick = { selectedTrack = track; onSelectSubtitleTrack(track) }, label = { Text(text = track.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }, leadingIcon = if (selectedTrack == track) { { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) } } else null, modifier = Modifier.fillMaxWidth()) } }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    OutlinedButton(onClick = { subtitlePickerLauncher.launch(arrayOf("text/*")) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.load_sidecar_subtitle)) }
                }
            }
        }
    }
}
