package io.github.azeezhamzat.ytdlmanager.core

/** A failure explained for people, plus yt-dlp's own words for "details". */
data class Failure(val message: String, val detail: String?, val isBotCheck: Boolean = false)

/**
 * Maps yt-dlp's stderr to a clear message. Patterns follow the desktop app's
 * `_PERMANENT_ERROR_PATTERNS` / `_TRANSIENT_ERROR_PATTERNS`.
 */
object ErrorText {
    // The apostrophe is a typographic one in current yt-dlp output.
    private val BOT_CHECK = Regex("confirm you.?re not a bot", RegexOption.IGNORE_CASE)

    const val BOT_CHECK_MESSAGE =
        "YouTube is asking this network to confirm it's not a bot. That's tied to " +
            "your connection, not to this video, and usually clears after a while " +
            "(often an hour or more). Wait, or switch between Wi-Fi and mobile data, " +
            "then retry. This app doesn't sign in to YouTube."

    private val RULES: List<Pair<Regex, String>> = listOf(
        "private video" to "This video is private.",
        "not available in your country|geo.?restrict|not made this video available in your country"
            to "This video isn't available in your country.",
        "sign in to confirm your age|age.?restrict|inappropriate for some users"
            to "This video is age-restricted and needs a signed-in account, which this app doesn't use.",
        "members.?only|join this channel"
            to "This video is for channel members only.",
        "logged.?in|log in to|login required|requires authentication|private account|rate-limit reached or login required"
            to "This content needs a logged-in account, which this app doesn't use.",
        "this live event will begin|premieres in"
            to "This video hasn't started yet.",
        "video unavailable|video is unavailable|has been removed|no longer available|account associated with this video has been terminated|this video is not available"
            to "This video is unavailable or has been removed.",
        "unsupported url" to "This link isn't supported.",
        "\\bdrm\\b" to "This video is DRM-protected and can't be downloaded.",
        "requested format is not available|no video formats found"
            to "None of the requested formats is available for this video.",
        "no space left on device|errno 28|disk quota exceeded"
            to "Your phone is out of storage space.",
        "http error 403|403: forbidden|403 forbidden"
            to "The site refused the download (HTTP 403). Updating yt-dlp in Settings often fixes this; otherwise try again later.",
        "http error 429|too many requests"
            to "The site says this network made too many requests. Wait a while before trying again.",
        "unable to download webpage|timed out|timeout|connection reset|temporary failure|network is unreachable|failed to resolve|getaddrinfo|no address associated|remote end closed connection|http error 5\\d\\d"
            to "Network problem. Check your connection and retry.",
    ).map { (pattern, message) -> Regex(pattern, RegexOption.IGNORE_CASE) to message }

    fun isBotCheck(text: String?): Boolean = text != null && BOT_CHECK.containsMatchIn(text)

    /** [failedWhat] starts the fallback message, e.g. "Couldn't get video info". */
    fun describe(stderr: String?, failedWhat: String = "Download failed"): Failure {
        val detail = lastErrorLine(stderr)
        val haystack = detail ?: stderr.orEmpty()
        if (isBotCheck(stderr)) return Failure(BOT_CHECK_MESSAGE, detail, isBotCheck = true)
        for ((regex, message) in RULES) {
            if (regex.containsMatchIn(haystack)) return Failure(message, detail)
        }
        return Failure(
            if (detail != null) "$failedWhat: ${detail.removePrefix("ERROR:").trim()}" else "$failedWhat.",
            detail,
        )
    }

    /** The last "ERROR: ..." line, or else the last non-blank line, shortened. */
    fun lastErrorLine(stderr: String?): String? {
        val lines = stderr.orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }
        val line = lines.lastOrNull { it.startsWith("ERROR:") } ?: lines.lastOrNull() ?: return null
        return if (line.length > 400) line.take(400) + "…" else line
    }
}
