package com.example.online

import android.media.MediaCodecList
import android.net.Uri
import androidx.media3.common.MimeTypes
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class CodecAdviceReport(
    val trackTitle: String,
    val detectedMime: String?,
    val hardwareCodecs: List<String>,
    val softwareCodecs: List<String>,
    val bundledFfmpegSupported: Boolean,
    val onlineSummary: String?,
    val onlineReferenceUrl: String?,
    val recommendation: String,
    val usedNetwork: Boolean,
)

@Serializable
internal data class WikiSummaryResponse(
    val title: String? = null,
    val extract: String? = null,
)

class OnlineCodecAdvisor(
    private val networkClient: OnlineNetworkClient,
    private val connectivityChecker: NetworkConnectivityChecker,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun inspectAndAdvise(
        trackTitle: String,
        diagnosticDetail: String?,
        fileExtension: String,
        allowOnlineLookup: Boolean,
    ): CodecAdviceReport = withContext(Dispatchers.IO) {
        val detectedMime = extractMime(diagnosticDetail) ?: inferMimeFromExtension(fileExtension)
        val deviceDecoders = findDeviceDecoders(detectedMime)
        val hw = deviceDecoders.filter { !isSoftwareCodecName(it) }
        val sw = deviceDecoders.filter { isSoftwareCodecName(it) }
        val ffmpegSupported = detectedMime != null &&
            runCatching { FfmpegLibrary.isAvailable() && FfmpegLibrary.supportsFormat(detectedMime) }
                .getOrDefault(false)

        var onlineSummary: String? = null
        var onlineRefUrl: String? = null
        var usedNetwork = false

        if (allowOnlineLookup && connectivityChecker.isOnline()) {
            val slug = wikipediaSlugFor(detectedMime, fileExtension)
            val apiUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/${Uri.encode(slug)}"
            usedNetwork = true
            when (val res = networkClient.getText(apiUrl)) {
                is HttpFetchResult.Success -> {
                    val parsed = runCatching { json.decodeFromString<WikiSummaryResponse>(res.body) }.getOrNull()
                    if (!parsed?.extract.isNullOrBlank()) {
                        onlineSummary = parsed!!.extract!!.trim()
                        onlineRefUrl = "https://en.wikipedia.org/wiki/$slug"
                    }
                }
                else -> Unit
            }
        }

        val recommendation = buildRecommendation(
            detectedMime = detectedMime,
            ffmpegSupported = ffmpegSupported,
            hasSoftwareCodec = sw.isNotEmpty(),
            hasHardwareCodec = hw.isNotEmpty(),
        )

        CodecAdviceReport(
            trackTitle = trackTitle,
            detectedMime = detectedMime,
            hardwareCodecs = hw,
            softwareCodecs = sw,
            bundledFfmpegSupported = ffmpegSupported,
            onlineSummary = onlineSummary,
            onlineReferenceUrl = onlineRefUrl,
            recommendation = recommendation,
            usedNetwork = usedNetwork,
        )
    }

    private fun extractMime(detail: String?): String? {
        if (detail.isNullOrBlank()) return null
        val match = Regex("""\b(?:video|audio)/[a-zA-Z0-9.+-]+\b""").find(detail)
        return match?.value
    }

    private fun inferMimeFromExtension(ext: String): String? = when (ext.lowercase()) {
        "mkv", "webm" -> MimeTypes.VIDEO_MATROSKA
        "mp4", "m4v" -> MimeTypes.VIDEO_MP4
        "avi" -> "video/x-msvideo"
        "wmv" -> "video/x-ms-wmv"
        "flv" -> "video/x-flv"
        "ts" -> MimeTypes.VIDEO_MP2T
        else -> null
    }

    private fun findDeviceDecoders(mime: String?): List<String> {
        if (mime.isNullOrBlank()) return emptyList()
        return runCatching {
            val list = MediaCodecList(MediaCodecList.ALL_CODECS)
            list.codecInfos
                .filter { !it.isEncoder && it.supportedTypes.any { t -> t.equals(mime, ignoreCase = true) } }
                .map { it.name }
                .distinct()
        }.getOrDefault(emptyList())
    }

    private fun isSoftwareCodecName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.startsWith("c2.android.") || lower.startsWith("omx.google.") || lower.contains(".sw.")
    }

    private fun wikipediaSlugFor(mime: String?, ext: String): String = when (mime?.lowercase()) {
        MimeTypes.VIDEO_H265 -> "High_Efficiency_Video_Coding"
        MimeTypes.VIDEO_H264 -> "Advanced_Video_Coding"
        MimeTypes.VIDEO_AV1 -> "AV1"
        MimeTypes.VIDEO_VP9 -> "VP9"
        MimeTypes.VIDEO_VP8 -> "VP8"
        MimeTypes.VIDEO_DOLBY_VISION -> "Dolby_Vision"
        MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD -> "DTS_(company)"
        MimeTypes.AUDIO_AC3 -> "Dolby_Digital"
        MimeTypes.AUDIO_E_AC3 -> "Dolby_Digital_Plus"
        MimeTypes.AUDIO_TRUEHD -> "Dolby_TrueHD"
        MimeTypes.AUDIO_OPUS -> "Opus_(audio_format)"
        else -> when (ext.lowercase()) {
            "mkv" -> "Matroska"
            "avi" -> "Audio_Video_Interleave"
            "wmv" -> "Windows_Media_Video"
            else -> "Video_codec"
        }
    }

    private fun buildRecommendation(
        detectedMime: String?,
        ffmpegSupported: Boolean,
        hasSoftwareCodec: Boolean,
        hasHardwareCodec: Boolean,
    ): String = when {
        ffmpegSupported ->
            "This format ($detectedMime) is supported by the bundled FFmpeg software decoder. Enable \"Prefer FFmpeg Extension Decoders\" in Settings or retry playback; if it still fails, the file header or frames may be damaged."
        detectedMime == MimeTypes.VIDEO_DOLBY_VISION ->
            "Dolby Vision proprietary profiles require hardware decoder licensing on your device. Try opening the file in an external system player or transcoding to standard 10-bit HEVC/H.264."
        detectedMime == MimeTypes.VIDEO_AV1 && !hasHardwareCodec && !hasSoftwareCodec ->
            "This device does not expose a hardware or software AV1 decoder for this profile. Try opening in an external player or converting the video stream to HEVC/H.264."
        !hasHardwareCodec && !hasSoftwareCodec ->
            "Neither Android MediaCodec nor bundled FFmpeg supports this codec (${detectedMime ?: "unknown"}). For security and Play Store policy safety, native codec binaries cannot be downloaded at runtime; use \"Open in External Player\" or convert to H.264/HEVC in an MKV or MP4 container."
        else ->
            "Your device exposes decoders for ${detectedMime ?: "this stream"}, so the failure is likely an unsupported bit-depth/profile or corrupted frames inside the file."
    }
}
