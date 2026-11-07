package io.github.azeezhamzat.ytdlmanager.core

/** Turns yt-dlp's output lines into [TaskProgress] updates. */
object ProgressParser {
    // Postprocessor lines yt-dlp prints while FFmpeg works, e.g.
    // "[Merger] Merging formats into ...", "[ExtractAudio] Destination: ...".
    private val POSTPROCESSORS = linkedMapOf(
        "[Merger]" to "Merging video and audio",
        "[VideoRemuxer]" to "Converting video",
        "[VideoConvertor]" to "Converting video",
        "[ExtractAudio]" to "Converting audio",
        "[ThumbnailsConvertor]" to "Preparing cover art",
        "[EmbedThumbnail]" to "Adding cover art",
        "[Metadata]" to "Writing tags",
        "[FixupM4a]" to "Finishing",
        "[FixupM3u8]" to "Finishing",
        "[FixupDuplicateMoov]" to "Finishing",
        "[FixupTimestamp]" to "Finishing",
    )

    fun parse(line: String): TaskProgress? {
        val text = line.trim()
        if (text.startsWith(YtDlpArgs.PROGRESS_TAG)) return parseProgress(text)
        for ((prefix, label) in POSTPROCESSORS) {
            if (text.startsWith(prefix)) return TaskProgress(Stage.PROCESSING, label = label)
        }
        return null
    }

    private fun parseProgress(text: String): TaskProgress? {
        val fields = text.removePrefix(YtDlpArgs.PROGRESS_TAG).trim().split('|')
        if (fields.size < 7) return null
        val status = fields[0]
        val downloaded = fields[1].toLongOrNullNumber()
        val total = fields[2].toLongOrNullNumber() ?: fields[3].toLongOrNullNumber()
        val speed = fields[4].toDoubleOrNull()
        val eta = fields[5].toLongOrNullNumber()
        val audioPart = fields[6] == "none"
        val fraction = when {
            status == "finished" -> 1f
            downloaded != null && total != null && total > 0 ->
                (downloaded.toDouble() / total).coerceIn(0.0, 1.0).toFloat()
            else -> null
        }
        return TaskProgress(
            stage = Stage.DOWNLOADING,
            fraction = fraction,
            downloadedBytes = downloaded,
            totalBytes = total,
            speedBytesPerSec = speed,
            etaSeconds = eta,
            audioPart = audioPart,
        )
    }

    /** yt-dlp prints "NA" for unknown values and floats for estimates. */
    private fun String.toLongOrNullNumber(): Long? = toLongOrNull() ?: toDoubleOrNull()?.toLong()
}
