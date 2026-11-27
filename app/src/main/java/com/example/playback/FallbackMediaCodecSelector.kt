package com.example.playback

import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.DecoderException
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.mediacodec.MediaCodecDecoderException
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegLibrary
import java.util.concurrent.CopyOnWriteArraySet

data class CodecCandidate(
    val name: String,
    val hardwareAccelerated: Boolean,
    val softwareOnly: Boolean,
)

@OptIn(UnstableApi::class)
class FallbackMediaCodecSelector(
    private val baseSelector: MediaCodecSelector = MediaCodecSelector.DEFAULT,
    private val softwareSelector: MediaCodecSelector = MediaCodecSelector.PREFER_SOFTWARE,
    private val isFfmpegAudioSupported: (String) -> Boolean = { mime ->
        runCatching { FfmpegLibrary.isAvailable() && FfmpegLibrary.supportsFormat(mime) }
            .getOrDefault(false)
    },
    private val isFfmpegVideoSupported: (String) -> Boolean = { mime ->
        runCatching { FfmpegLibrary.isAvailable() && FfmpegLibrary.supportsFormat(mime) }
            .getOrDefault(false)
    },
) : MediaCodecSelector {

    @Volatile
    var preferSoftwareOnly: Boolean = false

    @Volatile
    var preferFfmpegAudio: Boolean = false

    @Volatile
    var preferFfmpegVideo: Boolean = false

    private val excludedCodecs = CopyOnWriteArraySet<String>()

    fun excludeCodec(codecName: String?) {
        val trimmed = codecName?.trim().orEmpty()
        if (trimmed.isNotEmpty()) {
            excludedCodecs.add(trimmed)
        }
    }

    fun clearExcludedCodecs() {
        excludedCodecs.clear()
    }

    fun getExcludedCodecsSnapshot(): Set<String> = excludedCodecs.toSet()

    override fun getDecoderInfos(
        mimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunnelingDecoder: Boolean,
    ): List<MediaCodecInfo> {
        if (!requiresSecureDecoder) {
            if (preferFfmpegVideo && MimeTypes.isVideo(mimeType) && isFfmpegVideoSupported(mimeType)) {
                return emptyList()
            }
            if (preferFfmpegAudio && MimeTypes.isAudio(mimeType) && isFfmpegAudioSupported(mimeType)) {
                return emptyList()
            }
        }

        val sourceInfos = if (preferSoftwareOnly) {
            softwareSelector.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder,
            )
        } else {
            baseSelector.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder,
            )
        }

        if (!preferSoftwareOnly && excludedCodecs.isEmpty()) {
            return sourceInfos
        }

        val candidates = sourceInfos.map {
            CodecCandidate(
                name = it.name,
                hardwareAccelerated = it.hardwareAccelerated,
                softwareOnly = it.softwareOnly,
            )
        }
        val selectedNames = selectDecoderNames(
            candidates = candidates,
            preferSoftwareOnly = preferSoftwareOnly,
            excludedCodecNames = excludedCodecs,
        )

        val infoByName = sourceInfos.associateBy { it.name }
        val reordered = selectedNames.mapNotNull { infoByName[it] }
        return if (reordered.isNotEmpty()) reordered else sourceInfos
    }

    companion object {
        fun isSoftwareCodec(
            name: String,
            hardwareAccelerated: Boolean,
            softwareOnly: Boolean,
        ): Boolean {
            if (softwareOnly) return true
            if (!hardwareAccelerated) return true
            val lower = name.lowercase()
            return lower.startsWith("c2.android.") ||
                lower.startsWith("omx.google.") ||
                lower.contains(".sw.") ||
                lower.endsWith(".sw")
        }

        fun selectDecoderNames(
            candidates: List<CodecCandidate>,
            preferSoftwareOnly: Boolean,
            excludedCodecNames: Set<String> = emptySet(),
        ): List<String> {
            if (candidates.isEmpty()) return emptyList()

            val nonExcluded = candidates.filterNot { it.name in excludedCodecNames }
            val pool = if (nonExcluded.isNotEmpty()) nonExcluded else candidates

            if (!preferSoftwareOnly) {
                return pool.map { it.name }
            }

            val software = pool.filter {
                isSoftwareCodec(
                    name = it.name,
                    hardwareAccelerated = it.hardwareAccelerated,
                    softwareOnly = it.softwareOnly,
                )
            }
            return if (software.isNotEmpty()) {
                software.map { it.name }
            } else {
                pool.map { it.name }
            }
        }
    }
}

object PlaybackDiagnostics {

    data class ExtractedFailure(
        val failedCodecName: String? = null,
        val detailMessage: String? = null,
        val isAudioFailure: Boolean = false,
    )

    fun isRecoverableCodecErrorCode(errorCode: Int): Boolean {
        return when (errorCode) {
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED -> true
            else -> false
        }
    }

    fun isAudioErrorCode(errorCode: Int): Boolean {
        return errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ||
            errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED
    }

    @OptIn(UnstableApi::class)
    fun extractFromPlayerError(error: PlaybackException): ExtractedFailure {
        return runCatching {
            val causeChain = mutableListOf<Throwable>()
            val visited = HashSet<Throwable>()
            var cur: Throwable? = error
            while (cur != null && visited.add(cur) && causeChain.size < 16) {
                causeChain.add(cur)
                cur = cur.cause
            }

            val codecDecoderEx = causeChain
                .filterIsInstance<MediaCodecDecoderException>()
                .firstOrNull()
            val initEx = causeChain
                .filterIsInstance<MediaCodecRenderer.DecoderInitializationException>()
                .firstOrNull()
            val genericDecoderEx = causeChain
                .filterIsInstance<DecoderException>()
                .firstOrNull()
            val exoEx = error as? ExoPlaybackException

            val failedCodecName = codecDecoderEx?.codecInfo?.name
                ?: initEx?.codecInfo?.name
            val formatMime = exoEx?.rendererFormat?.sampleMimeType
                ?: initEx?.mimeType
                ?: codecDecoderEx?.codecInfo?.mimeType
            val width = exoEx?.rendererFormat?.width ?: -1
            val height = exoEx?.rendererFormat?.height ?: -1
            val codecsStr = exoEx?.rendererFormat?.codecs?.takeIf { it.isNotBlank() }

            val isAudio = isAudioErrorCode(error.errorCode) ||
                (formatMime?.startsWith("audio/", ignoreCase = true) == true) ||
                (exoEx?.rendererName?.contains("Audio", ignoreCase = true) == true)

            val parts = buildList {
                if (!formatMime.isNullOrBlank()) {
                    val res = if (width > 0 && height > 0) " ${width}x${height}" else ""
                    val profile = if (codecsStr != null) " ($codecsStr)" else ""
                    add("$formatMime$res$profile")
                }
                if (!failedCodecName.isNullOrBlank()) {
                    add("decoder: $failedCodecName")
                } else if (!exoEx?.rendererName.isNullOrBlank()) {
                    add("renderer: ${exoEx?.rendererName}")
                }
                val decoderMsg = genericDecoderEx?.message?.takeIf { it.isNotBlank() && it != failedCodecName }
                if (decoderMsg != null && failedCodecName.isNullOrBlank()) {
                    add(decoderMsg)
                }
            }

            ExtractedFailure(
                failedCodecName = failedCodecName,
                detailMessage = if (parts.isNotEmpty()) parts.joinToString(" • ") else null,
                isAudioFailure = isAudio,
            )
        }.getOrElse {
            ExtractedFailure(
                failedCodecName = null,
                detailMessage = null,
                isAudioFailure = isAudioErrorCode(error.errorCode),
            )
        }
    }

    fun formatUserErrorMessage(
        trackName: String,
        errorCodeName: String,
        diagnosticDetail: String?,
        fallbackTried: Boolean,
    ): String {
        val base = "Unable to decode \"$trackName\" ($errorCodeName)."
        val hint = if (fallbackTried) {
            "Hardware, software, and bundled FFmpeg decoders could not decode this stream (the file may be corrupted or use an unsupported profile)."
        } else {
            "The file may be corrupted or use an unsupported codec."
        }
        return if (!diagnosticDetail.isNullOrBlank()) {
            "$base $diagnosticDetail. $hint"
        } else {
            "$base $hint"
        }
    }
}
