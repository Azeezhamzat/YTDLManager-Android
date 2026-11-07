package io.github.azeezhamzat.ytdlmanager.core

import java.net.URI

/** Finding a link in pasted or shared text. */
object UrlText {
    private val HTTP_URL = Regex("""https?://[^\s<>"'`]+""", RegexOption.IGNORE_CASE)

    // "youtu.be/abc" or "www.tiktok.com/@x/video/1" pasted without a scheme.
    private val BARE_URL = Regex(
        """(?<![\w@.])(?:www\.|m\.)?[a-z0-9-]+(?:\.[a-z0-9-]+)*\.[a-z]{2,}/[^\s<>"'`]+""",
        RegexOption.IGNORE_CASE,
    )

    private const val TRAILING_PUNCTUATION = ".,;:!?'\"”’»…"

    /**
     * The first web link in [text], e.g. from YouTube's share sheet
     * ("Title https://youtu.be/xyz?si=..."). Returns null when there is none.
     */
    fun extractUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        HTTP_URL.find(text)?.let { return cleanTail(it.value) }
        BARE_URL.find(text)?.let { return "https://" + cleanTail(it.value) }
        return null
    }

    /** Drops sentence punctuation and unbalanced closing brackets after a link. */
    internal fun cleanTail(raw: String): String {
        var url = raw
        while (url.isNotEmpty()) {
            val last = url.last()
            val unbalanced = when (last) {
                ')' -> url.count { it == ')' } > url.count { it == '(' }
                ']' -> url.count { it == ']' } > url.count { it == '[' }
                else -> false
            }
            if (last in TRAILING_PUNCTUATION || unbalanced) url = url.dropLast(1) else break
        }
        return url
    }

    fun host(url: String): String? =
        runCatching { URI(url).host?.lowercase()?.removePrefix("www.") }.getOrNull()

    fun isYouTube(url: String): Boolean {
        val host = host(url) ?: return false
        return host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com") ||
            host == "youtube-nocookie.com" || host.endsWith(".youtube-nocookie.com")
    }

    fun looksLikeWebUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()
    }
}
