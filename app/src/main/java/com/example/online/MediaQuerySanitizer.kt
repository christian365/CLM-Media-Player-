package com.example.online

object MediaQuerySanitizer {

    private val extensionRegex = Regex("""\.[a-zA-Z0-9]{2,5}$""")
    private val bracketReleaseRegex = Regex("""\[[^\]]*]""")
    private val parenReleaseRegex = Regex("""\((?:official|audio|video|lyric|lyrics|hd|4k|1080p|720p|480p|remaster(?:ed)?|explicit|clean|live|visualizer)[^)]*\)""", RegexOption.IGNORE_CASE)
    private val releaseTokensRegex = Regex(
        """\b(?:1080p|720p|480p|2160p|4k|uhd|hdr|10bit|hi10p|x264|x265|h264|h265|hevc|av1|aac|ac3|eac3|dts|flac|web-dl|webdl|webrip|bluray|bdrip|dvdrip|hdrip|remux|proper|repack)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val leadingTrackNumRegex = Regex("""^\d{1,3}\s*[.\-_)]\s+""")
    private val multiSpaceRegex = Regex("""\s{2,}""")

    fun cleanTrackTitle(rawTitle: String, fileName: String): String {
        val candidate = rawTitle.takeIf {
            it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true)
        } ?: fileName.replace(extensionRegex, "")

        return candidate
            .replace(extensionRegex, "")
            .replace(bracketReleaseRegex, " ")
            .replace(parenReleaseRegex, " ")
            .replace(leadingTrackNumRegex, "")
            .replace('_', ' ')
            .replace(multiSpaceRegex, " ")
            .trim()
    }

    fun cleanArtistName(rawArtist: String): String? {
        val trimmed = rawArtist.trim()
        if (trimmed.isBlank()) return null
        if (trimmed.equals("<unknown>", ignoreCase = true)) return null
        if (trimmed.equals("unknown", ignoreCase = true)) return null
        if (trimmed.equals("unknown artist", ignoreCase = true)) return null
        return trimmed
    }

    fun cleanVideoSearchQuery(rawTitle: String, fileName: String): String {
        val source = fileName.takeIf { it.isNotBlank() } ?: rawTitle
        val withoutExt = source.replace(extensionRegex, "")
        val leadingNumMatch = Regex("""^(\d{1,3})\s*[.\-_)]\s+(.+)$""").find(withoutExt)
        val normalizedBody = if (leadingNumMatch != null) {
            "${leadingNumMatch.groupValues[2]} ${leadingNumMatch.groupValues[1]}"
        } else {
            withoutExt
        }

        val cleaned = normalizedBody
            .replace(bracketReleaseRegex, " ")
            .replace(releaseTokensRegex, " ")
            .replace('.', ' ')
            .replace('_', ' ')
            .replace('-', ' ')
            .replace(multiSpaceRegex, " ")
            .trim()

        return cleaned.ifBlank {
            rawTitle.replace(extensionRegex, "").trim()
        }
    }
}
