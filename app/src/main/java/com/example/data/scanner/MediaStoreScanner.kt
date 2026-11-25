package com.example.data.scanner

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.data.local.MediaTrackEntity
import com.example.data.local.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaStoreScanner(private val context: Context) {

    data class ScanOutput(
        val tracks: List<MediaTrackEntity>,
        val folderCounts: Map<String, Pair<String, Int>>,
    )

    suspend fun scanAll(): ScanOutput = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val genreMap = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            loadLegacyAudioGenres(resolver)
        } else {
            emptyMap()
        }

        val audioTracks = scanAudio(resolver, genreMap)
        val videoTracks = scanVideos(resolver)
        val allTracks = audioTracks + videoTracks

        val folderCounts = mutableMapOf<String, Pair<String, Int>>()
        for (track in allTracks) {
            val existing = folderCounts[track.folderPath]
            if (existing == null) {
                folderCounts[track.folderPath] = track.folderName to 1
            } else {
                folderCounts[track.folderPath] = existing.first to (existing.second + 1)
            }
        }

        ScanOutput(tracks = allTracks, folderCounts = folderCounts)
    }

    private fun scanAudio(
        resolver: ContentResolver,
        legacyGenreMap: Map<Long, String>,
    ): List<MediaTrackEntity> {
        val results = mutableListOf<MediaTrackEntity>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projectionList = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projectionList.add(MediaStore.Audio.Media.RELATIVE_PATH)
        }
        @Suppress("DEPRECATION")
        projectionList.add(MediaStore.Audio.Media.DATA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            projectionList.add(MediaStore.Audio.Media.GENRE)
            projectionList.add(MediaStore.Audio.Media.BITRATE)
        }

        val selection = "${MediaStore.Audio.Media.DURATION} >= ?"
        val selectionArgs = arrayOf("1000")

        runCatching {
            resolver.query(
                collection,
                projectionList.toTypedArray(),
                selection,
                selectionArgs,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val addedCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                val modifiedCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                val displayNameCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                val trackCol = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
                val yearCol = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                } else -1
                @Suppress("DEPRECATION")
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val genreCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
                } else -1
                val bitrateCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.BITRATE)
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id,
                    ).toString()

                    val displayName = if (displayNameCol >= 0) cursor.getString(displayNameCol).orEmpty() else ""
                    val rawTitle = cursor.getString(titleCol).orEmpty().ifBlank {
                        displayName.substringBeforeLast('.')
                    }
                    val artist = if (artistCol >= 0) {
                        cursor.getString(artistCol).orEmpty().ifBlank { "Unknown Artist" }
                    } else "Unknown Artist"
                    val album = if (albumCol >= 0) {
                        cursor.getString(albumCol).orEmpty().ifBlank { "Unknown Album" }
                    } else "Unknown Album"
                    val albumId = if (albumIdCol >= 0) cursor.getLong(albumIdCol) else 0L
                    val durationMs = if (durationCol >= 0) cursor.getLong(durationCol).coerceAtLeast(0L) else 0L
                    val sizeBytes = if (sizeCol >= 0) cursor.getLong(sizeCol).coerceAtLeast(0L) else 0L
                    val dateAdded = if (addedCol >= 0) cursor.getLong(addedCol) else 0L
                    val dateModified = if (modifiedCol >= 0) cursor.getLong(modifiedCol) else 0L
                    val mimeType = if (mimeCol >= 0) cursor.getString(mimeCol).orEmpty() else "audio/*"
                    val trackNumber = if (trackCol >= 0) cursor.getInt(trackCol) % 1000 else 0
                    val year = if (yearCol >= 0) cursor.getInt(yearCol) else 0
                    val bitrateBps = if (bitrateCol >= 0) cursor.getInt(bitrateCol).coerceAtLeast(0) else 0

                    val rawRelPath = if (relPathCol >= 0) cursor.getString(relPathCol) else null
                    val rawDataPath = if (dataCol >= 0) cursor.getString(dataCol) else null
                    val (folderPath, folderName) = resolveFolder(rawRelPath, rawDataPath)

                    val genre = when {
                        genreCol >= 0 -> cursor.getString(genreCol).orEmpty().trim()
                        else -> legacyGenreMap[id].orEmpty()
                    }

                    results.add(
                        MediaTrackEntity(
                            mediaStoreId = id,
                            contentUri = contentUri,
                            mediaType = MediaType.AUDIO,
                            title = rawTitle.ifBlank { "Untitled Track" },
                            artist = if (artist == "<unknown>") "Unknown Artist" else artist,
                            album = if (album == "<unknown>") "Unknown Album" else album,
                            albumId = albumId,
                            genre = genre,
                            durationMs = durationMs,
                            sizeBytes = sizeBytes,
                            dateAddedSec = dateAdded,
                            dateModifiedSec = dateModified,
                            mimeType = mimeType.ifBlank { guessAudioMime(displayName) },
                            folderPath = folderPath,
                            folderName = folderName,
                            fileName = displayName.ifBlank { "$rawTitle.audio" },
                            trackNumber = trackNumber,
                            year = year,
                            bitrateBps = bitrateBps,
                        )
                    )
                }
            }
        }
        return results
    }

    private fun scanVideos(resolver: ContentResolver): List<MediaTrackEntity> {
        val results = mutableListOf<MediaTrackEntity>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projectionList = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.ARTIST,
            MediaStore.Video.Media.ALBUM,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projectionList.add(MediaStore.Video.Media.RELATIVE_PATH)
        }
        @Suppress("DEPRECATION")
        projectionList.add(MediaStore.Video.Media.DATA)

        runCatching {
            resolver.query(
                collection,
                projectionList.toTypedArray(),
                "${MediaStore.Video.Media.SIZE} > ?",
                arrayOf("0"),
                "${MediaStore.Video.Media.DATE_ADDED} DESC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val artistCol = cursor.getColumnIndex(MediaStore.Video.Media.ARTIST)
                val albumCol = cursor.getColumnIndex(MediaStore.Video.Media.ALBUM)
                val durationCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                val addedCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
                val modifiedCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
                val displayNameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val widthCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
                } else -1
                @Suppress("DEPRECATION")
                val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id,
                    ).toString()

                    val displayName = if (displayNameCol >= 0) cursor.getString(displayNameCol).orEmpty() else ""
                    val rawTitle = cursor.getString(titleCol).orEmpty().ifBlank {
                        displayName.substringBeforeLast('.')
                    }
                    val artist = if (artistCol >= 0) {
                        cursor.getString(artistCol).orEmpty().ifBlank { "Local Video" }
                    } else "Local Video"
                    val album = if (albumCol >= 0) cursor.getString(albumCol).orEmpty() else ""
                    val durationMs = if (durationCol >= 0) cursor.getLong(durationCol).coerceAtLeast(0L) else 0L
                    val sizeBytes = if (sizeCol >= 0) cursor.getLong(sizeCol).coerceAtLeast(0L) else 0L
                    val dateAdded = if (addedCol >= 0) cursor.getLong(addedCol) else 0L
                    val dateModified = if (modifiedCol >= 0) cursor.getLong(modifiedCol) else 0L
                    val mimeType = if (mimeCol >= 0) cursor.getString(mimeCol).orEmpty() else "video/*"
                    val width = if (widthCol >= 0) cursor.getInt(widthCol).coerceAtLeast(0) else 0
                    val height = if (heightCol >= 0) cursor.getInt(heightCol).coerceAtLeast(0) else 0

                    val rawRelPath = if (relPathCol >= 0) cursor.getString(relPathCol) else null
                    val rawDataPath = if (dataCol >= 0) cursor.getString(dataCol) else null
                    val (folderPath, folderName) = resolveFolder(rawRelPath, rawDataPath)

                    results.add(
                        MediaTrackEntity(
                            mediaStoreId = -id, // Offset negative ID space only if needed? Wait: keep positive or distinct namespace!
                            contentUri = contentUri,
                            mediaType = MediaType.VIDEO,
                            title = rawTitle.ifBlank { "Untitled Video" },
                            artist = if (artist == "<unknown>") "Local Video" else artist,
                            album = album.ifBlank { folderName },
                            albumId = 0L,
                            genre = "Video",
                            durationMs = durationMs,
                            sizeBytes = sizeBytes,
                            dateAddedSec = dateAdded,
                            dateModifiedSec = dateModified,
                            mimeType = mimeType.ifBlank { guessVideoMime(displayName) },
                            folderPath = folderPath,
                            folderName = folderName,
                            fileName = displayName.ifBlank { "$rawTitle.mp4" },
                            width = width,
                            height = height,
                        )
                    )
                }
            }
        }
        return results
    }

    private fun loadLegacyAudioGenres(resolver: ContentResolver): Map<Long, String> {
        val map = mutableMapOf<Long, String>()
        runCatching {
            val genresUri = MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI
            resolver.query(
                genresUri,
                arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
                null,
                null,
                null,
            )?.use { genreCursor ->
                val idCol = genreCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID)
                val nameCol = genreCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME)
                while (genreCursor.moveToNext()) {
                    val genreId = genreCursor.getLong(idCol)
                    val genreName = genreCursor.getString(nameCol)?.trim().orEmpty()
                    if (genreName.isBlank()) continue
                    val membersUri = MediaStore.Audio.Genres.Members.getContentUri("external", genreId)
                    resolver.query(
                        membersUri,
                        arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID),
                        null,
                        null,
                        null,
                    )?.use { memberCursor ->
                        val audioIdCol = memberCursor.getColumnIndex(MediaStore.Audio.Genres.Members.AUDIO_ID)
                        if (audioIdCol >= 0) {
                            while (memberCursor.moveToNext()) {
                                map.putIfAbsent(memberCursor.getLong(audioIdCol), genreName)
                            }
                        }
                    }
                }
            }
        }
        return map
    }

    suspend fun enrichTechnicalAudioMetadata(contentUri: String): Pair<Int, Int>? =
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(contentUri))
                val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                    ?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                val sampleRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                        ?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                } else {
                    0
                }
                bitrate to sampleRate
            } catch (_: Exception) {
                null
            } finally {
                runCatching { retriever.release() }
            }
        }

    private fun resolveFolder(relPath: String?, dataPath: String?): Pair<String, String> {
        val parentFromData = dataPath?.takeIf { it.isNotBlank() }?.let { File(it).parent }
        if (!parentFromData.isNullOrBlank()) {
            val name = File(parentFromData).name.ifBlank { "Internal Storage" }
            return parentFromData to name
        }
        val cleanRel = relPath?.trim('/')?.takeIf { it.isNotBlank() } ?: "Root"
        val name = cleanRel.substringAfterLast('/').ifBlank { "Root" }
        return cleanRel to name
    }

    private fun guessAudioMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "mp3" -> "audio/mpeg"
            "m4a", "aac", "alac" -> "audio/mp4"
            "flac" -> "audio/flac"
            "ogg", "oga" -> "audio/ogg"
            "opus" -> "audio/opus"
            "wav" -> "audio/wav"
            "aiff", "aif" -> "audio/aiff"
            "wma" -> "audio/x-ms-wma"
            else -> "audio/*"
        }
    }

    private fun guessVideoMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "3gp" -> "video/3gpp"
            else -> "video/*"
        }
    }
}
