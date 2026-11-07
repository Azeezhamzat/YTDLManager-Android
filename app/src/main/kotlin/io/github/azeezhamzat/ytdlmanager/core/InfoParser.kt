package io.github.azeezhamzat.ytdlmanager.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/** What the preview card shows before you choose Video or Audio. */
data class MediaInfo(
    val id: String,
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val uploader: String?,
    val durationSeconds: Long?,
    val site: String?,
    val isPlaylist: Boolean,
    val playlistCount: Int?,
    val isLive: Boolean,
    /** Tallest video format on offer, if the site lists formats. */
    val maxHeight: Int?,
)

/** Parses the single JSON object yt-dlp prints for `--dump-single-json`. */
object InfoParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(stdout: String, requestedUrl: String): MediaInfo {
        val text = stdout.lineSequence().map { it.trim() }.lastOrNull { it.startsWith("{") }
            ?: throw IllegalArgumentException("yt-dlp returned no video information")
        val root = json.parseToJsonElement(text).jsonObject

        val type = root.str("_type")
        val isPlaylist = type == "playlist" || type == "multi_video"
        val entries = (root["entries"] as? JsonArray)?.size
        val liveStatus = root.str("live_status")
        val isLive = root.bool("is_live") == true || liveStatus == "is_live" || liveStatus == "is_upcoming"

        val heights = (root["formats"] as? JsonArray).orEmpty().mapNotNull { f ->
            val obj = f as? JsonObject ?: return@mapNotNull null
            if (obj.str("vcodec") == "none") null else (obj["height"] as? JsonPrimitive)?.intOrNull
        }

        return MediaInfo(
            id = root.str("id").orEmpty(),
            url = root.str("webpage_url") ?: root.str("original_url") ?: requestedUrl,
            title = root.str("title") ?: root.str("fulltitle") ?: root.str("id") ?: "Untitled",
            thumbnailUrl = root.str("thumbnail") ?: lastThumbnail(root["thumbnails"]),
            uploader = root.str("uploader") ?: root.str("channel") ?: root.str("creator"),
            durationSeconds = (root["duration"] as? JsonPrimitive)?.doubleOrNull?.toLong(),
            site = root.str("extractor_key") ?: root.str("extractor"),
            isPlaylist = isPlaylist,
            playlistCount = (root["playlist_count"] as? JsonPrimitive)?.intOrNull ?: entries,
            isLive = isLive,
            maxHeight = heights.maxOrNull() ?: (root["height"] as? JsonPrimitive)?.intOrNull,
        )
    }

    private fun lastThumbnail(element: JsonElement?): String? =
        (element as? JsonArray)?.mapNotNull { (it as? JsonObject)?.str("url") }?.lastOrNull()

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.bool(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
}
