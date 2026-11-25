package com.example.lyrics

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Extracts embedded lyrics directly from local audio files on Dispatchers.IO without loading full files into RAM.
 * Supports:
 * - ID3v2 (USLT unsynchronized & SYLT synchronized frames in MP3 / AIFF / WAV)
 * - Vorbis comments (LYRICS / UNSYNCEDLYRICS in FLAC and OGG Vorbis / Opus)
 * - MP4 / M4A atoms (moov -> udta -> meta -> ilst -> ©lyr)
 * - MediaMetadataRetriever platform fallback
 */
class EmbeddedLyricsExtractor(private val context: Context) {

    suspend fun extract(contentUri: String): LyricsResult = withContext(Dispatchers.IO) {
        val uri = runCatching { Uri.parse(contentUri) }.getOrNull() ?: return@withContext LyricsResult.Missing

        // 1. Direct container tag parsing (streamed header-only, memory bounded)
        val headerResult = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                extractFromStream(stream)
            }
        }.getOrNull()

        if (headerResult != null && headerResult !is LyricsResult.Missing) {
            return@withContext headerResult
        }

        // 2. MediaMetadataRetriever fallback (API / OEM metadata keys)
        val mmrText = extractViaMediaMetadataRetriever(uri)
        if (!mmrText.isNullOrBlank()) {
            val parsed = LrcParser.parse(mmrText, sourceTag = "MediaMetadata")
            if (parsed !is LyricsResult.Missing) return@withContext parsed
        }

        LyricsResult.Missing
    }

    internal fun extractFromBytes(bytes: ByteArray): LyricsResult {
        return extractBytes(bytes)
    }

    private fun extractFromStream(stream: InputStream): LyricsResult {
        return extractStream(stream)
    }

    private fun extractStream(stream: InputStream): LyricsResult = Companion.extractStream(stream)

    private fun extractViaMediaMetadataRetriever(uri: Uri): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            null
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    companion object {
        private const val MAX_METADATA_READ_BYTES = 2 * 1024 * 1024
        private const val MAX_MP4_SCAN_BYTES = 512 * 1024

        fun extractBytes(bytes: ByteArray): LyricsResult = extractStream(bytes.inputStream())

        fun extractStream(stream: InputStream): LyricsResult {
            val buffered = stream.buffered(16 * 1024)
            buffered.mark(64)
            val probe = ByteArray(16)
            val read = readFullyUpTo(buffered, probe, 16)
            buffered.reset()
            if (read < 4) return LyricsResult.Missing

            if (probe[0] == 'I'.code.toByte() && probe[1] == 'D'.code.toByte() && probe[2] == '3'.code.toByte()) {
                parseId3v2Lyrics(buffered)?.let { return it }
            }

            if (probe[0] == 'f'.code.toByte() && probe[1] == 'L'.code.toByte() &&
                probe[2] == 'a'.code.toByte() && probe[3] == 'C'.code.toByte()
            ) {
                parseFlacVorbisLyrics(buffered)?.let { return it }
            }

            if (probe[0] == 'O'.code.toByte() && probe[1] == 'g'.code.toByte() &&
                probe[2] == 'g'.code.toByte() && probe[3] == 'S'.code.toByte()
            ) {
                parseOggVorbisLyrics(buffered)?.let { return it }
            }

            if (read >= 8 && probe[4] == 'f'.code.toByte() && probe[5] == 't'.code.toByte() &&
                probe[6] == 'y'.code.toByte() && probe[7] == 'p'.code.toByte()
            ) {
                parseMp4Lyrics(buffered)?.let { return it }
            }

            return LyricsResult.Missing
        }

        private fun parseId3v2Lyrics(stream: InputStream): LyricsResult? {
            val header = ByteArray(10)
            if (readFullyUpTo(stream, header, 10) < 10) return null
            val majorVersion = header[3].toInt() and 0xFF
            val flags = header[5].toInt() and 0xFF
            val tagSize = decodeSynchsafeInt(header, 6).coerceAtMost(MAX_METADATA_READ_BYTES)
            if (tagSize <= 0) return null

            var tagBytes = ByteArray(tagSize)
            val actual = readFullyUpTo(stream, tagBytes, tagSize)
            if (actual <= 0) return null
            if (actual < tagSize) tagBytes = tagBytes.copyOf(actual)

            val unsync = (flags and 0x80) != 0
            if (unsync && majorVersion < 4) {
                tagBytes = removeUnsynchronisation(tagBytes)
            }

            var pos = 0
            if ((flags and 0x40) != 0 && pos + 4 <= tagBytes.size) {
                val extSize = if (majorVersion >= 4) {
                    decodeSynchsafeInt(tagBytes, pos)
                } else {
                    ByteBuffer.wrap(tagBytes, pos, 4).int + 4
                }
                if (extSize > 0) pos += extSize
            }

            var usltFallback: String? = null

            while (pos + (if (majorVersion == 2) 6 else 10) <= tagBytes.size) {
                if (tagBytes[pos].toInt() == 0) break
                val frameId: String
                val frameSize: Int
                val frameHeaderLen: Int
                if (majorVersion == 2) {
                    frameId = String(tagBytes, pos, 3, StandardCharsets.ISO_8859_1)
                    frameSize = ((tagBytes[pos + 3].toInt() and 0xFF) shl 16) or
                        ((tagBytes[pos + 4].toInt() and 0xFF) shl 8) or
                        (tagBytes[pos + 5].toInt() and 0xFF)
                    frameHeaderLen = 6
                } else {
                    frameId = String(tagBytes, pos, 4, StandardCharsets.ISO_8859_1)
                    frameSize = if (majorVersion >= 4) {
                        decodeSynchsafeInt(tagBytes, pos + 4)
                    } else {
                        ByteBuffer.wrap(tagBytes, pos + 4, 4).int
                    }
                    frameHeaderLen = 10
                }

                if (frameSize <= 0 || pos + frameHeaderLen + frameSize > tagBytes.size) break
                val payloadStart = pos + frameHeaderLen
                val payload = tagBytes.copyOfRange(payloadStart, payloadStart + frameSize)

                when (frameId) {
                    "SYLT", "SLT" -> {
                        parseId3SyltFrame(payload)?.let { lrcText ->
                            val parsed = LrcParser.parse(lrcText, sourceTag = "ID3v2 SYLT")
                            if (parsed !is LyricsResult.Missing) return parsed
                        }
                    }
                    "USLT", "ULT" -> {
                        if (usltFallback == null) {
                            usltFallback = parseId3UsltFrame(payload)
                        }
                    }
                }

                pos += frameHeaderLen + frameSize
            }

            return usltFallback?.let {
                LrcParser.parse(it, sourceTag = "ID3v2 USLT")
            }
        }

        private fun parseId3UsltFrame(payload: ByteArray): String? {
            if (payload.size < 5) return null
            val encoding = payload[0].toInt() and 0xFF
            val charset = id3Charset(encoding)
            val termLen = if (encoding == 1 || encoding == 2) 2 else 1
            var idx = 4
            idx = findNullTerminator(payload, idx, termLen)
            if (idx < 0) return null
            idx += termLen
            if (idx >= payload.size) return null
            return decodeId3String(payload, idx, payload.size - idx, charset)
        }

        private fun parseId3SyltFrame(payload: ByteArray): String? {
            if (payload.size < 7) return null
            val encoding = payload[0].toInt() and 0xFF
            val timeFormat = payload[4].toInt() and 0xFF
            val charset = id3Charset(encoding)
            val termLen = if (encoding == 1 || encoding == 2) 2 else 1
            var idx = 6
            val descEnd = findNullTerminator(payload, idx, termLen)
            if (descEnd < 0) return null
            idx = descEnd + termLen

            val builder = StringBuilder()
            while (idx < payload.size) {
                val textEnd = findNullTerminator(payload, idx, termLen)
                if (textEnd < 0 || textEnd + termLen + 4 > payload.size) break
                val lineText = decodeId3String(payload, idx, textEnd - idx, charset)
                    .replace("\n", " ")
                    .trim()
                val tsOffset = textEnd + termLen
                val rawTimestamp = ByteBuffer.wrap(payload, tsOffset, 4).int.toLong() and 0xFFFFFFFFL
                val timestampMs = if (timeFormat == 1) rawTimestamp * 24L else rawTimestamp
                val mins = timestampMs / 60_000L
                val secs = (timestampMs % 60_000L) / 1000L
                val centis = (timestampMs % 1000L) / 10L
                if (lineText.isNotEmpty()) {
                    builder.append(String.format("[%02d:%02d.%02d]%s\n", mins, secs, centis, lineText))
                }
                idx = tsOffset + 4
            }
            return builder.toString().takeIf { it.isNotBlank() }
        }

        private fun parseFlacVorbisLyrics(stream: InputStream): LyricsResult? {
            val marker = ByteArray(4)
            if (readFullyUpTo(stream, marker, 4) < 4) return null

            var isLast = false
            while (!isLast) {
                val blockHeader = ByteArray(4)
                if (readFullyUpTo(stream, blockHeader, 4) < 4) break
                val b0 = blockHeader[0].toInt() and 0xFF
                isLast = (b0 and 0x80) != 0
                val blockType = b0 and 0x7F
                val blockLen = ((blockHeader[1].toInt() and 0xFF) shl 16) or
                    ((blockHeader[2].toInt() and 0xFF) shl 8) or
                    (blockHeader[3].toInt() and 0xFF)
                if (blockLen <= 0 || blockLen > MAX_METADATA_READ_BYTES) break

                if (blockType == 4) {
                    val commentBytes = ByteArray(blockLen)
                    val got = readFullyUpTo(stream, commentBytes, blockLen)
                    if (got > 0) {
                        parseVorbisCommentPayload(commentBytes, offset = 0, sourceTag = "FLAC Vorbis")?.let {
                            return it
                        }
                    }
                    return null
                } else {
                    skipFully(stream, blockLen.toLong())
                }
            }
            return null
        }

        private fun parseOggVorbisLyrics(stream: InputStream): LyricsResult? {
            val content = readCappedBytes(stream, 256 * 1024)
            val vorbisMagic = byteArrayOf(0x03, 'v'.code.toByte(), 'o'.code.toByte(), 'r'.code.toByte(), 'b'.code.toByte(), 'i'.code.toByte(), 's'.code.toByte())
            val opusMagic = "OpusTags".toByteArray(StandardCharsets.US_ASCII)

            val vorbisPos = indexOfBytes(content, vorbisMagic)
            if (vorbisPos >= 0) {
                parseVorbisCommentPayload(content, offset = vorbisPos + vorbisMagic.size, sourceTag = "OGG Vorbis")?.let {
                    return it
                }
            }

            val opusPos = indexOfBytes(content, opusMagic)
            if (opusPos >= 0) {
                parseVorbisCommentPayload(content, offset = opusPos + opusMagic.size, sourceTag = "Opus Tags")?.let {
                    return it
                }
            }

            return null
        }

        private fun parseVorbisCommentPayload(
            bytes: ByteArray,
            offset: Int,
            sourceTag: String,
        ): LyricsResult? {
            if (offset + 8 > bytes.size) return null
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            bb.position(offset)
            val vendorLen = bb.int
            if (vendorLen < 0 || bb.position() + vendorLen + 4 > bytes.size) return null
            bb.position(bb.position() + vendorLen)
            val commentCount = bb.int.coerceAtMost(1024)
            var fallbackUnsync: String? = null

            for (i in 0 until commentCount) {
                if (bb.remaining() < 4) break
                val len = bb.int
                if (len <= 0 || len > bb.remaining()) break
                val entryBytes = ByteArray(len)
                bb.get(entryBytes)
                val entry = String(entryBytes, StandardCharsets.UTF_8)
                val eq = entry.indexOf('=')
                if (eq > 0) {
                    val key = entry.substring(0, eq).uppercase()
                    val value = entry.substring(eq + 1)
                    if (key == "LYRICS" || key == "SYNCEDLYRICS") {
                        val parsed = LrcParser.parse(value, sourceTag = sourceTag)
                        if (parsed is LyricsResult.Synchronized) return parsed
                        if (fallbackUnsync == null && value.isNotBlank()) fallbackUnsync = value
                    } else if (key == "UNSYNCEDLYRICS" || key == "UNSYNCED LYRICS") {
                        if (fallbackUnsync == null && value.isNotBlank()) fallbackUnsync = value
                    }
                }
            }

            return fallbackUnsync?.let { LrcParser.parse(it, sourceTag = sourceTag) }
        }

        private fun parseMp4Lyrics(stream: InputStream): LyricsResult? {
            val bytes = readCappedBytes(stream, MAX_MP4_SCAN_BYTES)
            val target = byteArrayOf(0xA9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte())
            var searchFrom = 0
            while (searchFrom < bytes.size - 16) {
                val idx = indexOfBytes(bytes, target, searchFrom)
                if (idx < 4) break
                val boxSize = ByteBuffer.wrap(bytes, idx - 4, 4).int
                if (boxSize in 16..MAX_METADATA_READ_BYTES && idx - 4 + boxSize <= bytes.size) {
                    val dataMagic = "data".toByteArray(StandardCharsets.US_ASCII)
                    val dataIdx = indexOfBytes(bytes, dataMagic, idx + 4, (idx - 4 + boxSize).coerceAtMost(bytes.size))
                    if (dataIdx >= 4) {
                        val dataSize = ByteBuffer.wrap(bytes, dataIdx - 4, 4).int
                        val payloadStart = dataIdx + 12
                        val payloadEnd = (dataIdx - 4 + dataSize).coerceAtMost(bytes.size)
                        if (payloadStart < payloadEnd) {
                            val lyricsStr = String(bytes, payloadStart, payloadEnd - payloadStart, StandardCharsets.UTF_8)
                            val parsed = LrcParser.parse(lyricsStr, sourceTag = "MP4 ©lyr")
                            if (parsed !is LyricsResult.Missing) return parsed
                        }
                    }
                }
                searchFrom = idx + 4
            }
            return null
        }

        private fun decodeSynchsafeInt(bytes: ByteArray, offset: Int): Int {
            if (offset + 4 > bytes.size) return 0
            return ((bytes[offset].toInt() and 0x7F) shl 21) or
                ((bytes[offset + 1].toInt() and 0x7F) shl 14) or
                ((bytes[offset + 2].toInt() and 0x7F) shl 7) or
                (bytes[offset + 3].toInt() and 0x7F)
        }

        private fun removeUnsynchronisation(bytes: ByteArray): ByteArray {
            val out = ByteArrayOutputStream(bytes.size)
            var i = 0
            while (i < bytes.size) {
                out.write(bytes[i].toInt())
                if (bytes[i] == 0xFF.toByte() && i + 1 < bytes.size && bytes[i + 1] == 0x00.toByte()) {
                    i += 2
                } else {
                    i++
                }
            }
            return out.toByteArray()
        }

        private fun id3Charset(encoding: Int): Charset = when (encoding) {
            1 -> StandardCharsets.UTF_16
            2 -> StandardCharsets.UTF_16BE
            3 -> StandardCharsets.UTF_8
            else -> StandardCharsets.ISO_8859_1
        }

        private fun decodeId3String(bytes: ByteArray, offset: Int, len: Int, charset: Charset): String {
            if (len <= 0) return ""
            return String(bytes, offset, len, charset).trim('\u0000', ' ')
        }

        private fun findNullTerminator(bytes: ByteArray, start: Int, termLen: Int): Int {
            var i = start
            if (termLen == 1) {
                while (i < bytes.size) {
                    if (bytes[i] == 0.toByte()) return i
                    i++
                }
            } else {
                while (i + 1 < bytes.size) {
                    if (bytes[i] == 0.toByte() && bytes[i + 1] == 0.toByte()) return i
                    i += 2
                }
            }
            return -1
        }

        private fun readFullyUpTo(stream: InputStream, target: ByteArray, len: Int): Int {
            var total = 0
            while (total < len) {
                val count = stream.read(target, total, len - total)
                if (count <= 0) break
                total += count
            }
            return total
        }

        private fun skipFully(stream: InputStream, bytesToSkip: Long) {
            var remaining = bytesToSkip
            while (remaining > 0) {
                val skipped = stream.skip(remaining)
                if (skipped <= 0) {
                    if (stream.read() == -1) break
                    remaining--
                } else {
                    remaining -= skipped
                }
            }
        }

        private fun readCappedBytes(stream: InputStream, maxBytes: Int): ByteArray {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            var total = 0
            while (total < maxBytes) {
                val toRead = minOf(buf.size, maxBytes - total)
                val count = stream.read(buf, 0, toRead)
                if (count <= 0) break
                out.write(buf, 0, count)
                total += count
            }
            return out.toByteArray()
        }

        private fun indexOfBytes(
            haystack: ByteArray,
            needle: ByteArray,
            start: Int = 0,
            end: Int = haystack.size,
        ): Int {
            if (needle.isEmpty() || end - start < needle.size) return -1
            val limit = end - needle.size
            for (i in start..limit) {
                var match = true
                for (j in needle.indices) {
                    if (haystack[i + j] != needle[j]) {
                        match = false
                        break
                    }
                }
                if (match) return i
            }
            return -1
        }
    }
}
