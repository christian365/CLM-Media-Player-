package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlin.math.abs

private enum class Axis { NONE, HORIZONTAL, VERTICAL }
private enum class Side { LEFT, RIGHT }

private data class OsdState(
    val icon: ImageVector,
    val label: String,
    val visible: Boolean = true,
)

@Composable
fun VideoGestureOverlay(
    player: ExoPlayer,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var osd by remember { mutableStateOf<OsdState?>(null) }
    var osdVisible by remember { mutableStateOf(false) }

    LaunchedEffect(osd) {
        if (osd != null) {
            osdVisible = true
            delay(1200)
            osdVisible = false
            delay(300)
            osd = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        content()

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(player) {
                    var axis = Axis.NONE
                    var side: Side = Side.LEFT
                    var startX = 0f
                    var startY = 0f
                    var startBrightness = 0.5f
                    var startVolume = 0
                    var startPosition = 0L
                    var maxVolume = 15

                    detectDragGestures(
                        onDragStart = { offset ->
                            startX = offset.x
                            startY = offset.y
                            axis = Axis.NONE
                            side = if (offset.x < size.width / 2f) Side.LEFT else Side.RIGHT
                            startBrightness = activity?.window?.attributes?.screenBrightness?.let {
                                if (it < 0f) 0.5f else it
                            } ?: 0.5f
                            maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            startPosition = player.currentPosition
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val dx = dragAmount.x
                            val dy = dragAmount.y

                            if (axis == Axis.NONE) {
                                if (abs(dx) > 20f || abs(dy) > 20f) {
                                    axis = if (abs(dx) > abs(dy)) Axis.HORIZONTAL else Axis.VERTICAL
                                }
                                return@detectDragGestures
                            }

                            when (axis) {
                                Axis.HORIZONTAL -> {
                                    val deltaMs = (dx * 50).toLong()
                                    val dur = player.duration
                                    val newPos = (startPosition + deltaMs).coerceIn(
                                        0L, if (dur > 0) dur else Long.MAX_VALUE
                                    )
                                    player.seekTo(newPos)
                                    val sign = if (deltaMs >= 0) "+" else ""
                                    osd = OsdState(
                                        icon = if (deltaMs >= 0) Icons.Rounded.Forward10 else Icons.Rounded.Replay10,
                                        label = "${sign}${(deltaMs / 1000)}s",
                                    )
                                }
                                Axis.VERTICAL -> {
                                    if (side == Side.LEFT) {
                                        val delta = -dy / size.height
                                        val newB = (startBrightness + delta).coerceIn(0f, 1f)
                                        val window = activity?.window
                                        if (window != null) {
                                            val attrs = window.attributes
                                            attrs.screenBrightness = newB
                                            window.attributes = attrs
                                        }
                                        osd = OsdState(
                                            icon = Icons.Rounded.BrightnessHigh,
                                            label = "${(newB * 100).toInt()}%",
                                        )
                                    } else {
                                        val delta = -dy / size.height
                                        val newV = (startVolume + delta * maxVolume).toInt().coerceIn(0, maxVolume)
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newV, 0)
                                        osd = OsdState(
                                            icon = if (newV > startVolume) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeDown,
                                            label = "$newV",
                                        )
                                    }
                                }
                                Axis.NONE -> {}
                            }
                        },
                        onDragEnd = { /* OSD auto-hides via LaunchedEffect */ },
                        onDragCancel = { axis = Axis.NONE },
                    )
                }
        )

        osd?.let { state ->
            AnimatedVisibility(
                visible = osdVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            RoundedCornerShape(14.dp),
                        )
                        .padding(horizontal = 22.dp, vertical = 16.dp),
                ) {
                    Icon(
                        imageVector = state.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(36.dp),
                    )
                    Text(
                        text = state.label,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 22.sp,
                    )
                }
            }
        }
    }
}
