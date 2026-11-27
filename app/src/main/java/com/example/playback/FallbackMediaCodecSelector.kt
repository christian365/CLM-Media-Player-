package com.example.playback

import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.mediacodec.MediaCodecDecoderException
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
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
) : MediaCodecSelector {

    @Volatile
    var preferSoftwareOnly: Boolean = false

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
        val defaultInfos = baseSelector.getDecoderInfos(
            mimeType,
            requiresSecureDecoder,
            requiresTunnelingDecoder,
        )
        if (!preferSoftwareOnly && excludedCodecs.isEmpty()) {
            return defaultInfos
        }

        val fallbackOrderInfos = if (preferSoftwareOnly) {
            softwareSelector.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder,
            )
        } else {
            defaultInfos
        }

        val candidates = fallbackOrderInfos.map {
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

        val infoByName = fallbackOrderInfos.associateBy { it.name }
        val reordered = selectedNames.mapNotNull { infoByName[it] }
        return if (reordered.isNotEmpty()) reordered else fallbackOrderInfos
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

    @OptIn(UnstableApi::class)
    fun extractFromPlayerError(error: PlaybackException): ExtractedFailure {
        val causeChain = generateSequence<Throwable>(error) { it.cause }.toList()

        val codecDecoderEx = causeChain
            .filterIsInstance<MediaCodecDecoderException>()
            .firstOrNull()
        val initEx = causeChain
            .filterIsInstance<MediaCodecRenderer.DecoderInitializationException>()
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

        val parts = buildList {
            if (!formatMime.isNullOrBlank()) {
                val res = if (width > 0 && height > 0) " ${width}x${height}" else ""
                val profile = if (codecsStr != null) " ($codecsStr)" else ""
                add("$formatMime$res$profile")
            }
            if (!failedCodecName.isNullOrBlank()) {
                add("decoder: $failedCodecName")
            }
        }

        val detail = if (parts.isNotEmpty()) parts.joinToString(" • ") else null
        return ExtractedFailure(
            failedCodecName = failedCodecName,
            detailMessage = detail,
        )
    }

    fun formatUserErrorMessage(
        trackName: String,
        errorCodeName: String,
        diagnosticDetail: String?,
        fallbackTried: Boolean,
    ): String {
        val base = "Unable to decode \"$trackName\" ($errorCodeName)."
        val hint = if (fallbackTried) {
            "Hardware and device software decoders could not decode this stream (common with 10-bit Hi10P or corrupted frames)."
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
