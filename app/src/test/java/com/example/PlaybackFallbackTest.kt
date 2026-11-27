package com.example

import androidx.media3.common.PlaybackException
import com.example.playback.CodecCandidate
import com.example.playback.FallbackMediaCodecSelector
import com.example.playback.PlaybackDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
