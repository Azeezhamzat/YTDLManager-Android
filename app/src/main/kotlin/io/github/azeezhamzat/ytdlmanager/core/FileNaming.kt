package io.github.azeezhamzat.ytdlmanager.core

/** File names for the shared Downloads folder. */
object FileNaming {
    // Characters FAT/exFAT (SD cards, USB) and MediaStore refuse.
    private val FORBIDDEN = Regex("""[\\/:*?"<>|\u0000-\u001F\u007F]""")
    private val WHITESPACE = Regex("""\s+""")
    const val MAX_NAME_BYTES = 200

    /** Keeps the extension, makes the rest safe and at most [maxBytes] UTF-8 bytes. */
    fun sanitize(fileName: String, maxBytes: Int = MAX_NAME_BYTES): String {
        val dot = fileName.lastIndexOf('.')
        val hasExt = dot > 0 && fileName.length - dot in 2..6
        val ext = if (hasExt) fileName.substring(dot + 1).lowercase() else ""
        var stem = if (hasExt) fileName.substring(0, dot) else fileName
        // Whitespace first, so tabs and newlines become spaces, not "_".
        stem = stem.replace(WHITESPACE, " ").replace(FORBIDDEN, "_").trim().trim('.', ' ')
        if (stem.isEmpty()) stem = "download"
        val budget = maxBytes - (if (ext.isEmpty()) 0 else ext.length + 1)
        stem = truncateUtf8(stem, budget).trimEnd('.', ' ')
        if (stem.isEmpty()) stem = "download"
        return if (ext.isEmpty()) stem else "$stem.$ext"
    }

    /** Cuts at a code-point boundary so no character is split. */
    internal fun truncateUtf8(text: String, maxBytes: Int): String {
        if (text.toByteArray(Charsets.UTF_8).size <= maxBytes) return text
        val out = StringBuilder()
        var used = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val chars = Character.charCount(cp)
            val bytes = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8).size
            if (used + bytes > maxBytes) break
            out.appendCodePoint(cp)
            used += bytes
            i += chars
        }
        return out.toString()
    }

    fun mimeTypeFor(extension: String): String = when (extension.lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "mov" -> "video/quicktime"
        "3gp" -> "video/3gpp"
        "m4a" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "opus", "ogg", "oga" -> "audio/ogg"
        "aac" -> "audio/aac"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        else -> "application/octet-stream"
    }

    /** Leftovers yt-dlp may leave in a work directory; never the finished file. */
    fun isTemporary(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return lower.endsWith(".part") || lower.endsWith(".ytdl") || lower.endsWith(".temp") ||
            lower.endsWith(".tmp") || lower.contains(".part-frag") || lower.endsWith(".webp") ||
            lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".json")
    }
}
