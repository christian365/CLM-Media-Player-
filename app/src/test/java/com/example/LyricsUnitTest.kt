package com.example

import com.example.lyrics.EmbeddedLyricsExtractor
import com.example.lyrics.LrcParser
import com.example.lyrics.LyricsResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

class LyricsUnitTest {

    @Test
    fun lrcParser_parsesTimestampsMultiTagsAndActiveLine() {
        val raw = """
            [ti:Sample Track]
            [00:05.00]First line
            [00:12.50][00:30.00]Chorus line
            [00:20.00]Second verse
        """.trimIndent()

        val result = LrcParser.parse(raw, "Test")
        assertTrue(result is LyricsResult.Synchronized)
        val synced = result as LyricsResult.Synchronized
        assertEquals(4, synced.lines.size)
        assertEquals("First line", synced.lines[0].text)
        assertEquals(5_000L, synced.lines[0].timestampMs)
        assertEquals("Chorus line", synced.lines[1].text)
        assertEquals(12_500L, synced.lines[1].timestampMs)
        assertEquals("Second verse", synced.lines[2].text)
        assertEquals("Chorus line", synced.lines[3].text)
        assertEquals(30_000L, synced.lines[3].timestampMs)

        assertEquals(-1, LrcParser.activeLineIndex(synced.lines, 1_000L))
        assertEquals(0, LrcParser.activeLineIndex(synced.lines, 5_500L))
        assertEquals(1, LrcParser.activeLineIndex(synced.lines, 15_000L))
        assertEquals(3, LrcParser.activeLineIndex(synced.lines, 35_000L))
    }

    @Test
    fun embeddedLyricsExtractor_readsId3v2FlacAndMp4Lyrics() {
        // 1. ID3v2.3 USLT frame
        val lyricText = "[00:01.00]Hello ID3"
        val usltPayload = ByteArrayOutputStream().apply {
            write(3) // UTF-8
            write("eng".toByteArray(StandardCharsets.US_ASCII))
            write(0)
            write(lyricText.toByteArray(StandardCharsets.UTF_8))
        }.toByteArray()
        val frameHeader = ByteBuffer.allocate(10).apply {
            put("USLT".toByteArray(StandardCharsets.US_ASCII))
            putInt(usltPayload.size)
            putShort(0)
        }.array()
        val tagSize = frameHeader.size + usltPayload.size
        val id3Header = byteArrayOf(
            'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(),
            3, 0, 0,
            ((tagSize shr 21) and 0x7F).toByte(),
            ((tagSize shr 14) and 0x7F).toByte(),
            ((tagSize shr 7) and 0x7F).toByte(),
            (tagSize and 0x7F).toByte(),
        )
        val id3Result = EmbeddedLyricsExtractor.extractBytes(id3Header + frameHeader + usltPayload)
        assertTrue(id3Result is LyricsResult.Synchronized)
        assertEquals("Hello ID3", (id3Result as LyricsResult.Synchronized).lines.first().text)

        // 2. FLAC Vorbis comment block
        val vendor = "Cadence".toByteArray(StandardCharsets.UTF_8)
        val comment = "LYRICS=Line one\nLine two".toByteArray(StandardCharsets.UTF_8)
        val vorbisPayload = ByteBuffer.allocate(4 + vendor.size + 4 + 4 + comment.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                putInt(vendor.size)
                put(vendor)
                putInt(1)
                putInt(comment.size)
                put(comment)
            }.array()
        val flacBlockHeader = byteArrayOf(
            (0x80 or 4).toByte(),
            ((vorbisPayload.size shr 16) and 0xFF).toByte(),
            ((vorbisPayload.size shr 8) and 0xFF).toByte(),
            (vorbisPayload.size and 0xFF).toByte(),
        )
        val flacBytes = "fLaC".toByteArray(StandardCharsets.US_ASCII) + flacBlockHeader + vorbisPayload
        val flacResult = EmbeddedLyricsExtractor.extractBytes(flacBytes)
        assertTrue(flacResult is LyricsResult.Unsynchronized)
        assertEquals(listOf("Line one", "Line two"), (flacResult as LyricsResult.Unsynchronized).lines)

        // 3. MP4 ©lyr atom
        val mp4Lyrics = "Verse from M4A".toByteArray(StandardCharsets.UTF_8)
        val dataBoxSize = 16 + mp4Lyrics.size
        val lyrBoxSize = 8 + dataBoxSize
        val mp4Bytes = ByteArrayOutputStream().apply {
            write(byteArrayOf(0, 0, 0, 12))
            write("ftyp".toByteArray(StandardCharsets.US_ASCII))
            write("M4A ".toByteArray(StandardCharsets.US_ASCII))
            write(ByteBuffer.allocate(4).putInt(lyrBoxSize).array())
            write(byteArrayOf(0xA9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte()))
            write(ByteBuffer.allocate(4).putInt(dataBoxSize).array())
            write("data".toByteArray(StandardCharsets.US_ASCII))
            write(byteArrayOf(0, 0, 0, 1, 0, 0, 0, 0))
            write(mp4Lyrics)
        }.toByteArray()
        val mp4Result = EmbeddedLyricsExtractor.extractBytes(mp4Bytes)
        assertTrue(mp4Result is LyricsResult.Unsynchronized)
        assertEquals(listOf("Verse from M4A"), (mp4Result as LyricsResult.Unsynchronized).lines)
    }
}
