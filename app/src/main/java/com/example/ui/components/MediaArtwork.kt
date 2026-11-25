package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.MediaTrackEntity
import com.example.data.local.MediaType
import com.example.media.LocalArtworkLoader
import com.example.media.rememberTrackArtwork
import java.util.Locale

@Composable
fun TrackArtworkThumb(
    loader: LocalArtworkLoader,
    track: MediaTrackEntity,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    targetSizePx: Int = 256,
    showFallbackBadge: Boolean = false,
) {
    val bitmap = rememberTrackArtwork(loader = loader, track = track, targetSizePx = targetSizePx)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "${track.title} artwork",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            val fallbackRes = if (track.mediaType == MediaType.VIDEO) {
                R.drawable.img_video_placeholder
            } else {
                R.drawable.img_audio_placeholder
            }
            Image(
                painter = painterResource(id = fallbackRes),
                contentDescription = "${track.title} default artwork",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (showFallbackBadge) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f))
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (track.mediaType == MediaType.VIDEO) {
                            Icons.Rounded.Movie
                        } else {
                            Icons.Rounded.GraphicEq
                        },
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun AlbumArtworkThumb(
    loader: LocalArtworkLoader,
    albumId: Long,
    sampleContentUri: String,
    albumName: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
) {
    val key = "album_${albumId}_320"
    val bitmapState = produceState<ImageBitmap?>(initialValue = loader.getCached(key), key1 = key) {
        value = loader.loadAlbumArt(albumId, sampleContentUri, 320)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmapState.value
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = "$albumName cover",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.img_audio_placeholder),
                contentDescription = "$albumName default cover",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
fun FormatBadgePill(
    label: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = containerColor,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            maxLines = 1,
        )
    }
}

fun formatDurationMs(durationMs: Long): String {
    val totalSec = (durationMs.coerceAtLeast(0L) / 1000L)
    val hours = totalSec / 3600L
    val mins = (totalSec % 3600L) / 60L
    val secs = totalSec % 60L
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, mins, secs)
    } else {
        String.format(Locale.US, "%d:%02d", mins, secs)
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", kb)
    }
}

fun formatExtensionTag(track: MediaTrackEntity): String {
    val ext = track.fileName.substringAfterLast('.', "").uppercase()
    if (ext.isNotBlank()) return ext
    return track.mimeType.substringAfterLast('/').uppercase().ifBlank { "MEDIA" }
}
