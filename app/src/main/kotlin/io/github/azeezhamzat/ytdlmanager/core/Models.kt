package io.github.azeezhamzat.ytdlmanager.core

import kotlinx.serialization.Serializable

@Serializable
enum class MediaKind { VIDEO, AUDIO }

/** "Max quality" cap for video, like the desktop app's MAX_HEIGHT_CHOICES. */
@Serializable
enum class VideoQuality(val maxHeight: Int?, val label: String) {
    BEST(null, "Best"),
    P1080(1080, "1080p"),
    P720(720, "720p"),
    P480(480, "480p"),
    P360(360, "360p"),
}

@Serializable
enum class AudioFormat(val ext: String, val label: String) {
    M4A("m4a", "M4A"),
    MP3("mp3", "MP3"),
}

@Serializable
data class DownloadOptions(
    val kind: MediaKind = MediaKind.VIDEO,
    val quality: VideoQuality = VideoQuality.BEST,
    val audioFormat: AudioFormat = AudioFormat.M4A,
    /** Title/artist tags for all files, plus cover art for audio. */
    val embedMetadata: Boolean = true,
) {
    /** Short human description, e.g. "Video · 720p max" or "Audio · MP3". */
    fun describe(): String = when (kind) {
        MediaKind.VIDEO ->
            if (quality == VideoQuality.BEST) "Video · best quality" else "Video · ${quality.label} max"
        MediaKind.AUDIO -> "Audio · ${audioFormat.label}"
    }
}

@Serializable
enum class TaskState { QUEUED, RUNNING, FAILED, CANCELLED }

/** One entry in the download queue. Finished downloads move to history. */
@Serializable
data class DownloadTask(
    val id: String,
    val url: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val uploader: String? = null,
    val durationSeconds: Long? = null,
    val options: DownloadOptions,
    val state: TaskState = TaskState.QUEUED,
    val createdAt: Long,
    /** User-facing reason when [state] is FAILED. */
    val error: String? = null,
    /** yt-dlp's own last error line, for the "details" view. */
    val errorDetail: String? = null,
)

@Serializable
data class HistoryEntry(
    val id: String,
    val url: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val options: DownloadOptions,
    val fileName: String,
    val mimeType: String,
    /** content:// URI of the file in MediaStore.Downloads. */
    val contentUri: String,
    val sizeBytes: Long,
    val completedAt: Long,
)

/** What a running task is doing right now (kept in memory, never persisted). */
enum class Stage { STARTING, DOWNLOADING, PROCESSING, SAVING }

data class TaskProgress(
    val stage: Stage,
    /** 0..1 when known. */
    val fraction: Float? = null,
    val downloadedBytes: Long? = null,
    val totalBytes: Long? = null,
    val speedBytesPerSec: Double? = null,
    val etaSeconds: Long? = null,
    /** True while the audio half of a video+audio download is downloading. */
    val audioPart: Boolean = false,
    /** For PROCESSING: what FFmpeg is doing ("Merging video and audio"). */
    val label: String? = null,
)
