package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.lyrics.LyricsResult
import com.example.online.MediaQuerySanitizer
import com.example.online.NetworkConnectivityChecker
import com.example.online.OnlineLyricsService
import com.example.online.OnlineNetworkClient
import com.example.online.OnlineSubtitleService
import com.example.online.SubtitleProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OnlineServicesUnitTest {

    private val offlineChecker = object : NetworkConnectivityChecker {
        override fun isOnline(): Boolean = false
    }

    @Test
    fun mediaQuerySanitizer_cleansTrackAndVideoQueries() {
        assertEquals(
            "Midnight City",
            MediaQuerySanitizer.cleanTrackTitle("01 - Midnight City (Official Audio).flac", "01 - Midnight City.flac"),
        )
        assertNull(MediaQuerySanitizer.cleanArtistName("<unknown>"))
        assertNull(MediaQuerySanitizer.cleanArtistName("Unknown Artist"))
        assertEquals("M83", MediaQuerySanitizer.cleanArtistName("  M83  "))
        assertEquals(
            "Haikyuu!! 2 01",
            MediaQuerySanitizer.cleanVideoSearchQuery(
                "01. Haikyuu!! 2 [T4TSA.cc]",
                "01. Haikyuu!! 2 [T4TSA.cc].mkv",
            ),
        )
        assertEquals(
            "Inception 2010",
            MediaQuerySanitizer.cleanVideoSearchQuery(
                "Inception",
                "Inception.2010.1080p.BluRay.x264.mkv",
            ),
        )
    }

    @Test
    fun networkClient_enforcesHttpsAllowlist() {
        assertTrue(OnlineNetworkClient.isAllowedHttpsUrl("https://lrclib.net/api/get?track_name=Test"))
        assertTrue(OnlineNetworkClient.isAllowedHttpsUrl("https://www.podnapisi.net/subtitles/search/old?sXML=1"))
        assertTrue(OnlineNetworkClient.isAllowedHttpsUrl("https://api.opensubtitles.com/api/v1/subtitles"))
        assertTrue(OnlineNetworkClient.isAllowedHttpsUrl("https://en.wikipedia.org/api/rest_v1/page/summary/Matroska"))

        assertFalse(OnlineNetworkClient.isAllowedHttpsUrl("http://lrclib.net/api/get"))
        assertFalse(OnlineNetworkClient.isAllowedHttpsUrl("https://localhost/test"))
        assertFalse(OnlineNetworkClient.isAllowedHttpsUrl("https://127.0.0.1/test"))
        assertFalse(OnlineNetworkClient.isAllowedHttpsUrl("https://169.254.169.254/latest/meta-data"))
        assertFalse(OnlineNetworkClient.isAllowedHttpsUrl("https://evil.example.com/payload.srt"))
    }

    @Test
    fun onlineLyricsService_prefersSyncedLyricsMatchingDuration() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val service = OnlineLyricsService(context, OnlineNetworkClient(), offlineChecker)

        val payload = """
            [
              {
                "id": 1,
                "trackName": "Test Song",
                "duration": 210.0,
                "instrumental": false,
                "plainLyrics": "Plain only line",
                "syncedLyrics": ""
              },
              {
                "id": 2,
                "trackName": "Test Song",
                "duration": 200.0,
                "instrumental": false,
                "plainLyrics": "Synced track plain",
                "syncedLyrics": "[00:02.00]Closer duration line"
              },
              {
                "id": 3,
                "trackName": "Test Song (Live)",
                "duration": 350.0,
                "instrumental": false,
                "syncedLyrics": "[00:05.00]Farther duration line"
              }
            ]
        """.trimIndent()

        val result = service.parseSearchResponse(payload, targetDurationSec = 202L)
        assertTrue(result is LyricsResult.Synchronized)
        val synced = result as LyricsResult.Synchronized
        assertEquals("Closer duration line", synced.lines.first().text)
    }

    @Test
    fun onlineSubtitleService_parsesPodnapisiXmlAndOpenSubtitlesJson() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val service = OnlineSubtitleService(context, OnlineNetworkClient(), offlineChecker)

        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <results>
              <subtitle>
                <pid>abc123</pid>
                <title>Haikyuu!! S02E01</title>
                <release>Haikyuu.S02E01.1080p.WEB</release>
                <language>en</language>
                <downloads>420</downloads>
              </subtitle>
            </results>
        """.trimIndent()

        val podResults = service.parsePodnapisiXml(xml)
        assertEquals(1, podResults.size)
        assertEquals("abc123", podResults[0].id)
        assertEquals("Haikyuu.S02E01.1080p.WEB", podResults[0].title)
        assertEquals("en", podResults[0].language)
        assertEquals(420, podResults[0].downloadsCount)
        assertEquals(SubtitleProvider.PODNAPISI, podResults[0].provider)
        assertEquals("https://www.podnapisi.net/subtitles/abc123/download", podResults[0].downloadUrl)

        val osJson = """
            {
              "data": [
                {
                  "id": "999",
                  "attributes": {
                    "language": "en",
                    "release": "Haikyuu.2x01.WEBRip",
                    "download_count": 150,
                    "files": [
                      { "file_id": 554433, "file_name": "haikyuu_01.srt" }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val osResults = service.parseOpenSubtitlesJson(osJson)
        assertEquals(1, osResults.size)
        assertEquals(SubtitleProvider.OPENSUBTITLES, osResults[0].provider)
        assertEquals(554433L, osResults[0].openSubtitlesFileId)
        assertEquals("srt", osResults[0].format)
    }

    @Test
    fun onlineSubtitleService_extractsZipAndBlocksZipSlip() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val service = OnlineSubtitleService(context, OnlineNetworkClient(), offlineChecker)

        val validZip = ByteArrayOutputStream().apply {
            ZipOutputStream(this).use { zos ->
                zos.putNextEntry(ZipEntry("subs/episode01.srt"))
                zos.write("1\n00:00:01,000 --> 00:00:03,000\nHello world\n".toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()
            }
        }.toByteArray()

        val extracted = service.extractSubtitleBytes(validZip)
        assertNotNull(extracted)
        assertEquals("srt", extracted?.second)
        assertTrue(String(extracted!!.first, StandardCharsets.UTF_8).contains("Hello world"))

        val traversalZip = ByteArrayOutputStream().apply {
            ZipOutputStream(this).use { zos ->
                zos.putNextEntry(ZipEntry("../../evil.srt"))
                zos.write("1\n00:00:01,000 --> 00:00:02,000\nBad\n".toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()
            }
        }.toByteArray()

        assertNull(service.extractSubtitleBytes(traversalZip))
    }
}
