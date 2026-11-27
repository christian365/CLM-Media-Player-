package com.example

import androidx.media3.common.PlaybackException
import com.example.playback.CodecCandidate
import com.example.playback.FallbackMediaCodecSelector
import com.example.playback.PlaybackDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaybackFallbackTest {

    @Test
    fun `software fallback filters hardware decoders and keeps software codecs`() {
        val candidates = listOf(
            CodecCandidate(name = "c2.qti.hevc.decoder", hardwareAccelerated = true, softwareOnly = false),
            CodecCandidate(name = "c2.android.hevc.decoder", hardwareAccelerated = false, softwareOnly = true),
            CodecCandidate(name = "OMX.google.hevc.decoder", hardwareAccelerated = false, softwareOnly = false),
        )

        val defaultOrder = FallbackMediaCodecSelector.selectDecoderNames(
            candidates = candidates,
            preferSoftwareOnly = false,
            excludedCodecNames = emptySet(),
        )
        assertEquals(
            listOf("c2.qti.hevc.decoder", "c2.android.hevc.decoder", "OMX.google.hevc.decoder"),
            defaultOrder,
        )

        val fallbackOrder = FallbackMediaCodecSelector.selectDecoderNames(
            candidates = candidates,
            preferSoftwareOnly = true,
            excludedCodecNames = setOf("c2.qti.hevc.decoder"),
        )
        assertEquals(
            listOf("c2.android.hevc.decoder", "OMX.google.hevc.decoder"),
            fallbackOrder,
        )
    }

    @Test
    fun `hardware codec excluded falls back cleanly when no software codecs exist`() {
        val candidates = listOf(
            CodecCandidate(name = "c2.vendor.av1.decoder", hardwareAccelerated = true, softwareOnly = false),
            CodecCandidate(name = "c2.vendor.av1.backup", hardwareAccelerated = true, softwareOnly = false),
        )

        val result = FallbackMediaCodecSelector.selectDecoderNames(
            candidates = candidates,
            preferSoftwareOnly = true,
            excludedCodecNames = setOf("c2.vendor.av1.decoder"),
        )
        assertEquals(listOf("c2.vendor.av1.backup"), result)
    }

    @Test
    fun `recoverable codec errors are classified accurately`() {
        assertTrue(PlaybackDiagnostics.isRecoverableCodecErrorCode(PlaybackException.ERROR_CODE_DECODING_FAILED))
        assertTrue(PlaybackDiagnostics.isRecoverableCodecErrorCode(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED))
        assertTrue(PlaybackDiagnostics.isRecoverableCodecErrorCode(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED))
        assertFalse(PlaybackDiagnostics.isRecoverableCodecErrorCode(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND))
    }

    @Test
    fun `error formatting includes codec diagnostic detail when available`() {
        val message = PlaybackDiagnostics.formatUserErrorMessage(
            trackName = "01. Haikyuu!! 2 [T4TSA.cc]",
            errorCodeName = "ERROR_CODE_DECODING_FAILED",
            diagnosticDetail = "video/hevc 1920x1080 • decoder: c2.qti.hevc.decoder",
            fallbackTried = true,
        )
        assertTrue(message.contains("01. Haikyuu!! 2 [T4TSA.cc]"))
        assertTrue(message.contains("video/hevc 1920x1080"))
        assertTrue(message.contains("c2.qti.hevc.decoder"))
    }

    @Test
    fun `ffmpeg audio preference bypasses MediaCodec only for supported audio mimes`() {
        val fakeCodec = androidx.media3.exoplayer.mediacodec.MediaCodecInfo.newInstance(
            "c2.android.aac.decoder",
            "audio/mp4a-latm",
            "audio/mp4a-latm",
            null,
            false,
            true,
            false,
            false,
            false,
        )
        val fakeVideoCodec = androidx.media3.exoplayer.mediacodec.MediaCodecInfo.newInstance(
            "c2.android.hevc.decoder",
            "video/hevc",
            "video/hevc",
            null,
            false,
            true,
            false,
            false,
            false,
        )
        val baseSelector = androidx.media3.exoplayer.mediacodec.MediaCodecSelector { mime, _, _ ->
            when (mime) {
                "audio/mp4a-latm" -> listOf(fakeCodec)
                "video/hevc" -> listOf(fakeVideoCodec)
                else -> emptyList()
            }
        }
        val selector = FallbackMediaCodecSelector(
            baseSelector = baseSelector,
            softwareSelector = baseSelector,
            isFfmpegAudioSupported = { it == "audio/mp4a-latm" },
        ).apply {
            preferFfmpegAudio = true
        }

        assertTrue(selector.getDecoderInfos("audio/mp4a-latm", false, false).isEmpty())
        assertEquals(1, selector.getDecoderInfos("video/hevc", false, false).size)
    }

    @Test
    fun `extractFromPlayerError safely classifies audio vs video errors`() {
        val audioErr = PlaybackException(
            "audio sink error",
            null,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
        )
        val extracted = PlaybackDiagnostics.extractFromPlayerError(audioErr)
        assertTrue(extracted.isAudioFailure)
        assertTrue(PlaybackDiagnostics.isAudioErrorCode(PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED))
        assertFalse(PlaybackDiagnostics.isAudioErrorCode(PlaybackException.ERROR_CODE_DECODING_FAILED))
    }
}
