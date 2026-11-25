package com.example.media

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.data.local.MediaTrackEntity
import com.example.data.local.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Memory-bounded local thumbnail & embedded artwork cache.
 * Zero network dependencies; gracefully handles large libraries on low-memory devices.
 */
class LocalArtworkLoader(private val context: Context) {

    private val cacheSizeKb: Int = run {
        val maxKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
        (maxKb / 10).coerceIn(4 * 1024, 24 * 1024)
    }

    private val memoryCache = object : LruCache<String, ImageBitmap>(cacheSizeKb) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            // 4 bytes per pixel in KB
            return ((value.width * value.height * 4L) / 1024L).toInt().coerceAtLeast(1)
        }
    }

    fun getCached(key: String): ImageBitmap? = memoryCache.get(key)

    suspend fun loadArtwork(track: MediaTrackEntity, targetSizePx: Int = 320): ImageBitmap? {
        val key = "${track.mediaStoreId}_$targetSizePx"
        memoryCache.get(key)?.let { return it }

        val loaded = withContext(Dispatchers.IO) {
            val bitmap = when (track.mediaType) {
                MediaType.AUDIO -> loadAudioArt(track, targetSizePx)
                MediaType.VIDEO -> loadVideoThumb(track, targetSizePx)
            }
            bitmap?.asImageBitmap()
        }

        if (loaded != null) {
            memoryCache.put(key, loaded)
        }
        return loaded
    }

    suspend fun loadAlbumArt(albumId: Long, sampleContentUri: String, targetSizePx: Int = 320): ImageBitmap? {
        val key = "album_${albumId}_$targetSizePx"
        memoryCache.get(key)?.let { return it }

        val loaded = withContext(Dispatchers.IO) {
            val albumArtUri = ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                albumId,
            )
            val bitmap = decodeAlbumArtUri(albumArtUri, targetSizePx)
                ?: decodeEmbeddedAudioArt(Uri.parse(sampleContentUri), targetSizePx)
            bitmap?.asImageBitmap()
        }

        if (loaded != null) {
            memoryCache.put(key, loaded)
        }
        return loaded
    }

    private fun loadAudioArt(track: MediaTrackEntity, targetSizePx: Int): Bitmap? {
        val uri = Uri.parse(track.contentUri)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                return context.contentResolver.loadThumbnail(uri, Size(targetSizePx, targetSizePx), null)
            }
        }
        if (track.albumId > 0L) {
            val albumArtUri = ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                track.albumId,
            )
            decodeAlbumArtUri(albumArtUri, targetSizePx)?.let { return it }
        }
        return decodeEmbeddedAudioArt(uri, targetSizePx)
    }

    private fun loadVideoThumb(track: MediaTrackEntity, targetSizePx: Int): Bitmap? {
        val uri = Uri.parse(track.contentUri)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                return context.contentResolver.loadThumbnail(
                    uri,
                    Size(targetSizePx, (targetSizePx * 9) / 16),
                    null,
                )
            }
        }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val raw = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            raw?.let { scaleDown(it, targetSizePx) }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun decodeAlbumArtUri(uri: Uri, targetSizePx: Int): Bitmap? {
        return runCatching {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOpts)
            }
            if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null
            val opts = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, targetSizePx)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        }.getOrNull()
    }

    private fun decodeEmbeddedAudioArt(uri: Uri, targetSizePx: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val pic = retriever.embeddedPicture ?: return null
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(pic, 0, pic.size, boundsOpts)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, targetSizePx)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeByteArray(pic, 0, pic.size, opts)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqSize: Int): Int {
        var inSampleSize = 1
        if (height > reqSize || width > reqSize) {
            val halfH = height / 2
            val halfW = width / 2
            while ((halfH / inSampleSize) >= reqSize && (halfW / inSampleSize) >= reqSize) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun scaleDown(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
        val newW = (w * ratio).toInt().coerceAtLeast(1)
        val newH = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true).also {
            if (it !== bitmap) bitmap.recycle()
        }
    }
}

@Composable
fun rememberTrackArtwork(
    loader: LocalArtworkLoader,
    track: MediaTrackEntity?,
    targetSizePx: Int = 320,
): ImageBitmap? {
    val key = track?.let { "${it.mediaStoreId}_$targetSizePx" }
    val initial = key?.let { loader.getCached(it) }
    val state = produceState<ImageBitmap?>(initialValue = initial, key1 = key) {
        value = if (track != null) {
            loader.loadArtwork(track, targetSizePx)
        } else {
            null
        }
    }
    return state.value
}
