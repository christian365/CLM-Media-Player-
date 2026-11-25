package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object BackgroundImageStorage {
    private const val BACKGROUNDS_DIR = "custom_backgrounds"
    private const val MAX_DIMENSION_PX = 1600

    suspend fun importPickedImage(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, BACKGROUNDS_DIR).apply { mkdirs() }

            // Measure bounds first to prevent OOM on large camera photos
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION_PX)
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            var bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return@withContext null

            // Normalize orientation from EXIF if present
            val rotationDegrees = context.contentResolver.openInputStream(uri)?.use { stream ->
                runCatching {
                    val exif = ExifInterface(stream)
                    when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                }.getOrDefault(0f)
            } ?: 0f

            if (rotationDegrees != 0f) {
                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated != bitmap) {
                    bitmap.recycle()
                    bitmap = rotated
                }
            }

            // Clean up older background files
            dir.listFiles()?.forEach { it.delete() }

            val outFile = File(dir, "bg_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            bitmap.recycle()
            outFile.absolutePath
        }.getOrNull()
    }

    suspend fun loadBitmap(path: String?, maxDimensionPx: Int = MAX_DIMENSION_PX): Bitmap? =
        withContext(Dispatchers.IO) {
            if (path.isNullOrBlank()) return@withContext null
            val file = File(path)
            if (!file.exists() || !file.isFile) return@withContext null
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimensionPx)
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeFile(path, opts)
            }.getOrNull()
        }

    private fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var inSampleSize = 1
        var curW = width
        var curH = height
        while (curW > maxDim || curH > maxDim) {
            curW /= 2
            curH /= 2
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }
}
