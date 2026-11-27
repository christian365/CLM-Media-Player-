package com.example.online

import android.content.Context
import android.net.Uri
import com.example.data.local.MediaTrackEntity
import com.example.lyrics.LrcParser
import com.example.lyrics.LyricsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.charset.StandardCharsets

@Serializable
internal data class LrcLibRecord(
    val id: Long? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
)

sealed interface OnlineLyricsFetchResult {
    data class Found(val lyrics: LyricsResult) : OnlineLyricsFetchResult
    data object NotFound : OnlineLyricsFetchResult
    data object Offline : OnlineLyricsFetchResult
    data class Error(val message: String) : OnlineLyricsFetchResult
}

class OnlineLyricsService(
    private val context: Context,
    private val networkClient: OnlineNetworkClient,
    private val connectivityChecker: NetworkConnectivityChecker,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val cacheDir by lazy {
        File(context.filesDir, "online_lyrics").apply { mkdirs() }
    }

    suspend fun getCachedLyrics(track: MediaTrackEntity): LyricsResult = withContext(Dispatchers.IO) {
        val file = cacheFileFor(track.mediaStoreId)
        if (!file.exists() || !file.isFile) return@withContext LyricsResult.Missing
        val raw = runCatching { file.readText(StandardCharsets.UTF_8) }.getOrNull()
            ?: return@withContext LyricsResult.Missing
        val lines = raw.lines()
        val sourceTag = lines.firstOrNull()?.removePrefix(CACHE_SOURCE_PREFIX)?.takeIf { it.isNotBlank() }
            ?: "Cached Online"
        val body = if (lines.firstOrNull()?.startsWith(CACHE_SOURCE_PREFIX) == true) {
            lines.drop(1).joinToString("\n")
        } else {
            raw
        }
        LrcParser.parse(body, sourceTag = sourceTag)
    }

    suspend fun fetchAndCache(
        track: MediaTrackEntity,
        forceRefresh: Boolean = false,
    ): OnlineLyricsFetchResult = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            val cached = getCachedLyrics(track)
            if (cached !is LyricsResult.Missing) {
                return@withContext OnlineLyricsFetchResult.Found(cached)
            }
        }

        if (!connectivityChecker.isOnline()) {
            return@withContext OnlineLyricsFetchResult.Offline
        }

        val cleanTitle = MediaQuerySanitizer.cleanTrackTitle(track.title, track.fileName)
        if (cleanTitle.isBlank()) return@withContext OnlineLyricsFetchResult.NotFound
        val cleanArtist = MediaQuerySanitizer.cleanArtistName(track.artist)
        val cleanAlbum = track.album.takeIf {
            it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true)
        }
        val durationSec = (track.durationMs / 1000L).takeIf { it > 0L }

        // 1. Try exact match when artist is known
        if (!cleanArtist.isNullOrBlank()) {
            val getUrl = buildGetUrl(cleanTitle, cleanArtist, cleanAlbum, durationSec)
            when (val res = networkClient.getText(getUrl)) {
                is HttpFetchResult.Success -> {
                    val record = runCatching { json.decodeFromString<LrcLibRecord>(res.body) }.getOrNull()
                    record?.let { parseAndSaveRecord(track.mediaStoreId, it) }?.let { parsed ->
                        return@withContext OnlineLyricsFetchResult.Found(parsed)
                    }
                }
                is HttpFetchResult.NetworkError -> return@withContext OnlineLyricsFetchResult.Error(res.message)
                is HttpFetchResult.HttpError -> Unit // fall through to search endpoint on 404
            }
        }

        // 2. Fallback to LRCLIB search endpoint
        val searchUrl = buildSearchUrl(cleanTitle, cleanArtist)
        when (val res = networkClient.getText(searchUrl)) {
            is HttpFetchResult.Success -> {
                val records = runCatching { json.decodeFromString<List<LrcLibRecord>>(res.body) }
                    .getOrDefault(emptyList())
                val best = selectBestRecord(records, durationSec)
                val parsed = best?.let { parseAndSaveRecord(track.mediaStoreId, it) }
                if (parsed != null) {
                    OnlineLyricsFetchResult.Found(parsed)
                } else {
                    OnlineLyricsFetchResult.NotFound
                }
            }
            is HttpFetchResult.HttpError -> OnlineLyricsFetchResult.Error("LRCLIB returned HTTP ${res.code}")
            is HttpFetchResult.NetworkError -> OnlineLyricsFetchResult.Error(res.message)
        }
    }

    internal fun parseSearchResponse(jsonPayload: String, targetDurationSec: Long?): LyricsResult {
        val records = runCatching { json.decodeFromString<List<LrcLibRecord>>(jsonPayload) }
            .getOrDefault(emptyList())
        val best = selectBestRecord(records, targetDurationSec) ?: return LyricsResult.Missing
        val raw = best.syncedLyrics?.takeIf { it.isNotBlank() }
            ?: best.plainLyrics?.takeIf { it.isNotBlank() }
            ?: return LyricsResult.Missing
        return LrcParser.parse(raw, sourceTag = ONLINE_SOURCE_TAG)
    }

    private fun selectBestRecord(records: List<LrcLibRecord>, targetDurationSec: Long?): LrcLibRecord? {
        val withLyrics = records.filter {
            !it.instrumental && (!it.syncedLyrics.isNullOrBlank() || !it.plainLyrics.isNullOrBlank())
        }
        if (withLyrics.isEmpty()) return null

        return withLyrics.sortedWith(
            compareByDescending<LrcLibRecord> { !it.syncedLyrics.isNullOrBlank() }
                .thenBy { record ->
                    if (targetDurationSec != null && record.duration != null) {
                        kotlin.math.abs(record.duration - targetDurationSec.toDouble())
                    } else {
                        0.0
                    }
                }
        ).firstOrNull()
    }

    private fun parseAndSaveRecord(mediaStoreId: Long, record: LrcLibRecord): LyricsResult? {
        if (record.instrumental) return null
        val raw = record.syncedLyrics?.takeIf { it.isNotBlank() }
            ?: record.plainLyrics?.takeIf { it.isNotBlank() }
            ?: return null
        val parsed = LrcParser.parse(raw, sourceTag = ONLINE_SOURCE_TAG)
        if (parsed is LyricsResult.Missing) return null

        runCatching {
            val file = cacheFileFor(mediaStoreId)
            file.writeText("$CACHE_SOURCE_PREFIX$ONLINE_SOURCE_TAG\n$raw", StandardCharsets.UTF_8)
        }
        return parsed
    }

    private fun cacheFileFor(mediaStoreId: Long): File = File(cacheDir, "lyric_$mediaStoreId.lrc")

    private fun buildGetUrl(
        trackName: String,
        artistName: String,
        albumName: String?,
        durationSec: Long?,
    ): String {
        val builder = Uri.parse("https://lrclib.net/api/get").buildUpon()
            .appendQueryParameter("track_name", trackName)
            .appendQueryParameter("artist_name", artistName)
        if (!albumName.isNullOrBlank()) {
            builder.appendQueryParameter("album_name", albumName)
        }
        if (durationSec != null && durationSec > 0) {
            builder.appendQueryParameter("duration", durationSec.toString())
        }
        return builder.build().toString()
    }

    private fun buildSearchUrl(trackName: String, artistName: String?): String {
        val builder = Uri.parse("https://lrclib.net/api/search").buildUpon()
        if (!artistName.isNullOrBlank()) {
            builder.appendQueryParameter("track_name", trackName)
            builder.appendQueryParameter("artist_name", artistName)
        } else {
            builder.appendQueryParameter("q", trackName)
        }
        return builder.build().toString()
    }

    companion object {
        private const val ONLINE_SOURCE_TAG = "LRCLIB Online"
        private const val CACHE_SOURCE_PREFIX = "#CLM_SOURCE:"
    }
}
