package com.example.online

import android.content.Context
import android.net.Uri
import android.util.Xml
import androidx.media3.common.MimeTypes
import com.example.data.scanner.SidecarSubtitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.StringReader
import java.security.MessageDigest
import java.util.zip.ZipInputStream

enum class SubtitleProvider(val label: String) {
    PODNAPISI("Podnapisi"),
    OPENSUBTITLES("OpenSubtitles"),
}

data class OnlineSubtitleCandidate(
    val id: String,
    val title: String,
    val language: String,
    val format: String,
    val downloadUrl: String,
    val provider: SubtitleProvider,
    val openSubtitlesFileId: Long? = null,
    val downloadsCount: Int = 0,
)

sealed interface OnlineSubtitleSearchResult {
    data class Success(val items: List<OnlineSubtitleCandidate>) : OnlineSubtitleSearchResult
    data object Offline : OnlineSubtitleSearchResult
    data class Error(val message: String) : OnlineSubtitleSearchResult
}

sealed interface OnlineSubtitleDownloadResult {
    data class Success(val sidecar: SidecarSubtitle) : OnlineSubtitleDownloadResult
    data object Offline : OnlineSubtitleDownloadResult
    data class Error(val message: String) : OnlineSubtitleDownloadResult
}

@Serializable
internal data class OpenSubtitlesSearchResponse(
    val data: List<OpenSubtitlesEntry> = emptyList(),
)

@Serializable
internal data class OpenSubtitlesEntry(
    val id: String = "",
    val attributes: OpenSubtitlesAttributes = OpenSubtitlesAttributes(),
)

@Serializable
internal data class OpenSubtitlesAttributes(
    val language: String = "en",
    val release: String = "",
    val download_count: Int = 0,
    val files: List<OpenSubtitlesFile> = emptyList(),
)

@Serializable
internal data class OpenSubtitlesFile(
    val file_id: Long = 0L,
    val file_name: String = "",
)

@Serializable
internal data class OpenSubtitlesDownloadResponse(
    val link: String = "",
    val file_name: String = "",
)

class OnlineSubtitleService(
    private val context: Context,
    private val networkClient: OnlineNetworkClient,
    private val connectivityChecker: NetworkConnectivityChecker,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val cacheDir by lazy {
        File(context.filesDir, "online_subtitles").apply { mkdirs() }
    }

    suspend fun search(
        query: String,
        languageCode: String = "",
        openSubtitlesApiKey: String = "",
    ): OnlineSubtitleSearchResult = withContext(Dispatchers.IO) {
        if (!connectivityChecker.isOnline()) {
            return@withContext OnlineSubtitleSearchResult.Offline
        }
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            return@withContext OnlineSubtitleSearchResult.Success(emptyList())
        }

        val combined = mutableListOf<OnlineSubtitleCandidate>()
        var lastError: String? = null

        // 1. Optional OpenSubtitles REST v1 if user provided an API key
        if (openSubtitlesApiKey.isNotBlank()) {
            val osUrl = buildOpenSubtitlesSearchUrl(cleanQuery, languageCode)
            when (val res = networkClient.getText(osUrl, headers = mapOf("Api-Key" to openSubtitlesApiKey))) {
                is HttpFetchResult.Success -> combined.addAll(parseOpenSubtitlesJson(res.body))
                is HttpFetchResult.HttpError -> lastError = "OpenSubtitles HTTP ${res.code}"
                is HttpFetchResult.NetworkError -> lastError = res.message
            }
        }

        // 2. Keyless Podnapisi XML search
        val podUrl = buildPodnapisiSearchUrl(cleanQuery, languageCode)
        when (val res = networkClient.getText(podUrl)) {
            is HttpFetchResult.Success -> combined.addAll(parsePodnapisiXml(res.body))
            is HttpFetchResult.HttpError -> if (combined.isEmpty()) lastError = "Podnapisi HTTP ${res.code}"
            is HttpFetchResult.NetworkError -> if (combined.isEmpty()) lastError = res.message
        }

        if (combined.isEmpty() && lastError != null) {
            OnlineSubtitleSearchResult.Error(lastError)
        } else {
            OnlineSubtitleSearchResult.Success(
                combined.sortedByDescending { it.downloadsCount }.take(30)
            )
        }
    }

    suspend fun downloadAndSave(
        candidate: OnlineSubtitleCandidate,
        openSubtitlesApiKey: String = "",
    ): OnlineSubtitleDownloadResult = withContext(Dispatchers.IO) {
        if (!connectivityChecker.isOnline()) {
            return@withContext OnlineSubtitleDownloadResult.Offline
        }

        val resolvedDownloadUrl = when (candidate.provider) {
            SubtitleProvider.PODNAPISI -> candidate.downloadUrl
            SubtitleProvider.OPENSUBTITLES -> {
                val fileId = candidate.openSubtitlesFileId
                    ?: return@withContext OnlineSubtitleDownloadResult.Error("Missing OpenSubtitles file ID")
                if (openSubtitlesApiKey.isBlank()) {
                    return@withContext OnlineSubtitleDownloadResult.Error("OpenSubtitles API key required")
                }
                val body = buildJsonObject { put("file_id", fileId) }.toString()
                when (
                    val dlMeta = networkClient.postJson(
                        url = "https://api.opensubtitles.com/api/v1/download",
                        jsonBody = body,
                        headers = mapOf("Api-Key" to openSubtitlesApiKey),
                    )
                ) {
                    is HttpFetchResult.Success -> {
                        val parsed = runCatching {
                            json.decodeFromString<OpenSubtitlesDownloadResponse>(dlMeta.body)
                        }.getOrNull()
                        parsed?.link?.takeIf { it.isNotBlank() }
                            ?: return@withContext OnlineSubtitleDownloadResult.Error("No download link returned")
                    }
                    is HttpFetchResult.HttpError -> return@withContext OnlineSubtitleDownloadResult.Error("HTTP ${dlMeta.code}")
                    is HttpFetchResult.NetworkError -> return@withContext OnlineSubtitleDownloadResult.Error(dlMeta.message)
                }
            }
        }

        val rawBytes = when (val bytesRes = networkClient.getBytes(resolvedDownloadUrl)) {
            is HttpFetchResult.Success -> bytesRes.body
            is HttpFetchResult.HttpError -> return@withContext OnlineSubtitleDownloadResult.Error("Download HTTP ${bytesRes.code}")
            is HttpFetchResult.NetworkError -> return@withContext OnlineSubtitleDownloadResult.Error(bytesRes.message)
        }

        val extracted = extractSubtitleBytes(rawBytes, fallbackExt = candidate.format)
            ?: return@withContext OnlineSubtitleDownloadResult.Error("Downloaded archive had no supported subtitle (.srt/.ass/.vtt)")

        val (subBytes, ext) = extracted
        val safeId = sha256Short("${candidate.provider.name}_${candidate.id}_${candidate.language}")
        val outFile = File(cacheDir, "sub_$safeId.$ext")
        val canonicalBase = cacheDir.canonicalPath + File.separator
        if (!outFile.canonicalPath.startsWith(canonicalBase)) {
            return@withContext OnlineSubtitleDownloadResult.Error("Invalid subtitle destination path")
        }
        outFile.writeBytes(subBytes)

        val mimeType = when (ext) {
            "vtt" -> MimeTypes.TEXT_VTT
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            else -> MimeTypes.APPLICATION_SUBRIP
        }
        OnlineSubtitleDownloadResult.Success(
            SidecarSubtitle(
                uri = Uri.fromFile(outFile).toString(),
                displayName = "${candidate.title.take(40)} (${candidate.language}).$ext",
                mimeType = mimeType,
                language = candidate.language.ifBlank { "und" },
            )
        )
    }

    internal fun parsePodnapisiXml(xml: String): List<OnlineSubtitleCandidate> {
        if (xml.isBlank()) return emptyList()
        val items = mutableListOf<OnlineSubtitleCandidate>()
        runCatching {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xml))

            var inSubtitle = false
            var pid = ""
            var title = ""
            var release = ""
            var lang = "en"
            var downloads = 0
            var currentTag = ""

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name.orEmpty()
                        if (currentTag == "subtitle") {
                            inSubtitle = true
                            pid = ""
                            title = ""
                            release = ""
                            lang = "en"
                            downloads = 0
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inSubtitle) {
                            val text = parser.text.orEmpty().trim()
                            if (text.isNotEmpty()) {
                                when (currentTag) {
                                    "pid" -> pid = text
                                    "title" -> title = text
                                    "release" -> release = text
                                    "language" -> lang = text.lowercase()
                                    "downloads" -> downloads = text.toIntOrNull() ?: 0
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "subtitle" && inSubtitle) {
                            inSubtitle = false
                            if (pid.isNotBlank() && pid.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
                                val label = release.ifBlank { title }.ifBlank { "Podnapisi #$pid" }
                                items.add(
                                    OnlineSubtitleCandidate(
                                        id = pid,
                                        title = label,
                                        language = lang.ifBlank { "und" },
                                        format = "srt",
                                        downloadUrl = "https://www.podnapisi.net/subtitles/$pid/download",
                                        provider = SubtitleProvider.PODNAPISI,
                                        downloadsCount = downloads,
                                    )
                                )
                            }
                        }
                        currentTag = ""
                    }
                }
                eventType = parser.next()
            }
        }
        return items
    }

    internal fun parseOpenSubtitlesJson(payload: String): List<OnlineSubtitleCandidate> {
        val parsed = runCatching { json.decodeFromString<OpenSubtitlesSearchResponse>(payload) }
            .getOrNull() ?: return emptyList()
        return parsed.data.mapNotNull { entry ->
            val file = entry.attributes.files.firstOrNull { it.file_id > 0L } ?: return@mapNotNull null
            val ext = file.file_name.substringAfterLast('.', "srt").lowercase()
                .takeIf { it in SUPPORTED_EXTS } ?: "srt"
            OnlineSubtitleCandidate(
                id = entry.id.ifBlank { file.file_id.toString() },
                title = entry.attributes.release.ifBlank { file.file_name }.ifBlank { "OpenSubtitles #${file.file_id}" },
                language = entry.attributes.language.ifBlank { "en" },
                format = ext,
                downloadUrl = "https://api.opensubtitles.com/api/v1/download",
                provider = SubtitleProvider.OPENSUBTITLES,
                openSubtitlesFileId = file.file_id,
                downloadsCount = entry.attributes.download_count,
            )
        }
    }

    internal fun extractSubtitleBytes(rawBytes: ByteArray, fallbackExt: String = "srt"): Pair<ByteArray, String>? {
        if (rawBytes.size < 4) return null
        val isZip = rawBytes[0] == 0x50.toByte() && rawBytes[1] == 0x4B.toByte()
        if (!isZip) {
            val ext = fallbackExt.lowercase().takeIf { it in SUPPORTED_EXTS } ?: "srt"
            return rawBytes to ext
        }

        return runCatching {
            ZipInputStream(ByteArrayInputStream(rawBytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name.orEmpty()
                    if (!entry.isDirectory && !name.contains("..")) {
                        val ext = name.substringAfterLast('.', "").lowercase()
                        if (ext in SUPPORTED_EXTS) {
                            val out = ByteArrayOutputStream()
                            val buf = ByteArray(4096)
                            var total = 0
                            while (true) {
                                val read = zis.read(buf)
                                if (read <= 0) break
                                total += read
                                if (total > MAX_SUBTITLE_BYTES) return@runCatching null
                                out.write(buf, 0, read)
                            }
                            if (out.size() > 0) {
                                return@runCatching out.toByteArray() to ext
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
                null
            }
        }.getOrNull()
    }

    private fun buildPodnapisiSearchUrl(query: String, languageCode: String): String {
        val builder = Uri.parse("https://www.podnapisi.net/subtitles/search/old").buildUpon()
            .appendQueryParameter("sXML", "1")
            .appendQueryParameter("sK", query)
        if (languageCode.isNotBlank()) {
            builder.appendQueryParameter("sL", languageCode)
        }
        return builder.build().toString()
    }

    private fun buildOpenSubtitlesSearchUrl(query: String, languageCode: String): String {
        val builder = Uri.parse("https://api.opensubtitles.com/api/v1/subtitles").buildUpon()
            .appendQueryParameter("query", query)
        if (languageCode.isNotBlank()) {
            builder.appendQueryParameter("languages", languageCode)
        }
        return builder.build().toString()
    }

    private fun sha256Short(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

    companion object {
        private val SUPPORTED_EXTS = setOf("srt", "vtt", "ass", "ssa")
        private const val MAX_SUBTITLE_BYTES = 2 * 1024 * 1024
    }
}
