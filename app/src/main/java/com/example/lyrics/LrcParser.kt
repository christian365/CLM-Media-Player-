package com.example.lyrics

data class LyricLine(
    val timestampMs: Long?,
    val text: String,
)

sealed interface LyricsResult {
    data class Synchronized(
        val lines: List<LyricLine>,
        val sourceTag: String,
    ) : LyricsResult

    data class Unsynchronized(
        val lines: List<String>,
        val sourceTag: String,
    ) : LyricsResult

    data object Missing : LyricsResult
}

object LrcParser {

    private val timestampRegex = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val offsetRegex = Regex("""\[offset:([+-]?\d+)]""", RegexOption.IGNORE_CASE)
    private val metadataTagRegex = Regex("""^\[[a-zA-Z]{1,10}:.*]$""")

    fun parse(rawText: String?, sourceTag: String = "Embedded"): LyricsResult {
        val cleaned = rawText?.replace("\r\n", "\n")?.replace("\r", "\n")?.trim().orEmpty()
        if (cleaned.isBlank()) return LyricsResult.Missing

        var globalOffsetMs = 0L
        val timedLines = mutableListOf<LyricLine>()
        val plainLines = mutableListOf<String>()

        for (rawLine in cleaned.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) {
                if (plainLines.isNotEmpty()) plainLines.add("")
                continue
            }

            offsetRegex.find(line)?.let { match ->
                globalOffsetMs = match.groupValues[1].toLongOrNull() ?: 0L
                return@let
            }

            val matches = timestampRegex.findAll(line).toList()
            if (matches.isNotEmpty()) {
                val lyricContent = timestampRegex.replace(line, "").trim()
                for (match in matches) {
                    val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                    val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                    val fractionRaw = match.groupValues[3]
                    val millis = when (fractionRaw.length) {
                        0 -> 0L
                        1 -> (fractionRaw.toLongOrNull() ?: 0L) * 100L
                        2 -> (fractionRaw.toLongOrNull() ?: 0L) * 10L
                        else -> fractionRaw.take(3).toLongOrNull() ?: 0L
                    }
                    val totalMs = ((minutes * 60L + seconds) * 1000L + millis - globalOffsetMs)
                        .coerceAtLeast(0L)
                    if (lyricContent.isNotEmpty()) {
                        timedLines.add(LyricLine(timestampMs = totalMs, text = lyricContent))
                    }
                }
            } else if (!metadataTagRegex.matches(line)) {
                plainLines.add(line)
            }
        }

        return when {
            timedLines.isNotEmpty() -> {
                val sorted = timedLines.sortedBy { it.timestampMs ?: 0L }
                LyricsResult.Synchronized(lines = sorted, sourceTag = sourceTag)
            }
            plainLines.any { it.isNotBlank() } -> {
                LyricsResult.Unsynchronized(
                    lines = plainLines.dropLastWhile { it.isBlank() },
                    sourceTag = sourceTag,
                )
            }
            else -> LyricsResult.Missing
        }
    }

    fun activeLineIndex(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var low = 0
        var high = lines.lastIndex
        var result = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val ts = lines[mid].timestampMs ?: 0L
            if (ts <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }
}
