package com.example.data.scanner

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.media3.common.MimeTypes
import com.example.data.local.MediaTrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class SidecarSubtitle(
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val language: String = "und",
)

class SidecarSubtitleFinder(private val context: Context) {

    private val supportedExtensions = setOf("srt", "vtt", "ass", "ssa")

    suspend fun findMatchingSubtitles(video: MediaTrackEntity): List<SidecarSubtitle> =
        withContext(Dispatchers.IO) {
            val baseName = video.fileName.substringBeforeLast('.').lowercase()
            if (baseName.isBlank()) return@withContext emptyList()

            val results = mutableListOf<SidecarSubtitle>()
            val seenUris = mutableSetOf<String>()

            // 1. Direct directory scan when filesystem path is readable (Android <=12 or public folders)
            val folderFile = File(video.folderPath)
            if (folderFile.exists() && folderFile.isDirectory) {
                folderFile.listFiles()?.forEach { candidate ->
                    if (candidate.isFile) {
                        val ext = candidate.extension.lowercase()
                        val candBase = candidate.nameWithoutExtension.lowercase()
                        if (ext in supportedExtensions &&
                            (candBase == baseName || candBase.startsWith("$baseName."))
                        ) {
                            val uriStr = Uri.fromFile(candidate).toString()
                            if (seenUris.add(uriStr)) {
                                results.add(
                                    SidecarSubtitle(
                                        uri = uriStr,
                                        displayName = candidate.name,
                                        mimeType = mimeTypeForExtension(ext),
                                        language = extractLanguageHint(candBase, baseName),
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 2. MediaStore.Files scan for scoped-storage environments
            runCatching {
                val filesUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
                } else {
                    MediaStore.Files.getContentUri("external")
                }
                val projection = arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                )
                val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
                val args = arrayOf("$baseName%")
                context.contentResolver.query(filesUri, projection, selection, args, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameCol).orEmpty()
                        val ext = name.substringAfterLast('.', "").lowercase()
                        val candBase = name.substringBeforeLast('.').lowercase()
                        if (ext in supportedExtensions &&
                            (candBase == baseName || candBase.startsWith("$baseName."))
                        ) {
                            val contentUri = ContentUris.withAppendedId(filesUri, cursor.getLong(idCol)).toString()
                            if (seenUris.add(contentUri)) {
                                results.add(
                                    SidecarSubtitle(
                                        uri = contentUri,
                                        displayName = name,
                                        mimeType = mimeTypeForExtension(ext),
                                        language = extractLanguageHint(candBase, baseName),
                                    )
                                )
                            }
                        }
                    }
                }
            }

            results
        }

    suspend fun resolvePickedSubtitle(uri: Uri): SidecarSubtitle? = withContext(Dispatchers.IO) {
        var displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "subtitle.srt"
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        cursor.getString(idx)?.takeIf { it.isNotBlank() }?.let { displayName = it }
                    }
                }
            }
        }
        val ext = displayName.substringAfterLast('.', "srt").lowercase()
        if (ext !in supportedExtensions && ext != "txt") {
            // Still allow if user explicitly picked a subtitle MIME type
            val crMime = context.contentResolver.getType(uri).orEmpty()
            val mapped = when {
                crMime.contains("vtt") -> MimeTypes.TEXT_VTT
                crMime.contains("ass") || crMime.contains("ssa") -> MimeTypes.TEXT_SSA
                else -> MimeTypes.APPLICATION_SUBRIP
            }
            return@withContext SidecarSubtitle(
                uri = uri.toString(),
                displayName = displayName,
                mimeType = mapped,
            )
        }
        SidecarSubtitle(
            uri = uri.toString(),
            displayName = displayName,
            mimeType = mimeTypeForExtension(ext),
        )
    }

    private fun extractLanguageHint(candBase: String, videoBase: String): String {
        val suffix = candBase.removePrefix(videoBase).trimStart('.', '_', '-', ' ')
        return suffix.takeIf { it.length in 2..5 } ?: "und"
    }

    private fun mimeTypeForExtension(ext: String): String = when (ext) {
        "vtt" -> MimeTypes.TEXT_VTT
        "ass", "ssa" -> MimeTypes.TEXT_SSA
        else -> MimeTypes.APPLICATION_SUBRIP
    }
}
