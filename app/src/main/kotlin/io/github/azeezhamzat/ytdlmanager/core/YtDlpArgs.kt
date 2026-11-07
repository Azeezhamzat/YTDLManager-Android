package io.github.azeezhamzat.ytdlmanager.core

/**
 * The yt-dlp command-line options this app uses, as (option, argument) pairs
 * (argument null for flags). Kept free of Android types so it can be unit
 * tested; the engine feeds the pairs to YoutubeDLRequest.addOption.
 *
 * youtubedl-android adds `--js-runtimes quickjs:<path>` (YouTube's JavaScript
 * challenges) and `--ffmpeg-location` to every command itself.
 */
object YtDlpArgs {
    /** Prefix of the progress lines we ask yt-dlp to print (see [ProgressParser]). */
    const val PROGRESS_TAG = "[ytdlm]"

    /** Output file name inside the task's private work directory. */
    const val OUTPUT_TEMPLATE_NAME = "%(title,id).150B.%(ext)s"

    private const val PROGRESS_TEMPLATE =
        "download:$PROGRESS_TAG %(progress.status)s|%(progress.downloaded_bytes)s|" +
            "%(progress.total_bytes)s|%(progress.total_bytes_estimate)s|%(progress.speed)s|" +
            "%(progress.eta)s|%(info.vcodec)s"

    /**
     * yt-dlp format selector, ported from the desktop app's
     * `build_format_selector` with its "mp4" container preference: phones
     * play MP4/M4A everywhere. "<=?" also accepts formats with no height.
     */
    fun formatSelector(options: DownloadOptions): String = when (options.kind) {
        MediaKind.AUDIO -> when (options.audioFormat) {
            // An M4A source needs no re-encode; anything else gets converted.
            AudioFormat.M4A -> "bestaudio[ext=m4a]/bestaudio/best"
            AudioFormat.MP3 -> "bestaudio/best"
        }
        MediaKind.VIDEO -> {
            val h = options.quality.maxHeight
            val cap = if (h != null) "[height<=?$h]" else ""
            // YouTube's AAC audio is "m4a", never "mp4".
            val mp4 = "bestvideo*[ext=mp4]$cap+bestaudio[ext=m4a]/best[ext=mp4]$cap"
            val any = "bestvideo*$cap+bestaudio/best$cap"
            // Nothing at or under the cap: still download the best there is
            // rather than failing.
            val fallback = if (cap.isNotEmpty()) "/bestvideo*+bestaudio/best" else ""
            "$mp4/$any$fallback"
        }
    }

    private fun common(cacheDir: String): List<Pair<String, String?>> = listOf(
        "--ignore-config" to null,
        "--no-playlist" to null,
        // Lets yt-dlp keep YouTube's player code and solved challenges between
        // runs; QuickJS is slow, so this saves seconds per video.
        "--cache-dir" to cacheDir,
        "--socket-timeout" to "30",
    )

    /** Metadata only: title, thumbnail, duration, formats (-J = one JSON object). */
    fun infoArgs(cacheDir: String): List<Pair<String, String?>> =
        common(cacheDir) + listOf(
            "--dump-single-json" to null,
            // A playlist link returns just the list, not every video's details.
            "--flat-playlist" to null,
        )

    fun downloadArgs(
        options: DownloadOptions,
        outputTemplate: String,
        cacheDir: String,
    ): List<Pair<String, String?>> {
        val args = common(cacheDir).toMutableList()
        args += "-f" to formatSelector(options)
        args += "-o" to outputTemplate
        args += "--no-mtime" to null
        args += "--newline" to null
        args += "--progress-template" to PROGRESS_TEMPLATE
        args += "--progress-delta" to "0.5"
        when (options.kind) {
            MediaKind.VIDEO -> args += "--merge-output-format" to "mp4"
            MediaKind.AUDIO -> {
                args += "--extract-audio" to null
                args += "--audio-format" to options.audioFormat.ext
                args += "--audio-quality" to "0"
            }
        }
        if (options.embedMetadata) {
            args += "--embed-metadata" to null
            // Cover art only for audio, where M4A and MP3 both support it
            // (same rule as the desktop app).
            if (options.kind == MediaKind.AUDIO) args += "--embed-thumbnail" to null
        }
        return args
    }
}
