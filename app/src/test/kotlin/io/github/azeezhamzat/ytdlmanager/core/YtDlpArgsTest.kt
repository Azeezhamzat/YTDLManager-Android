package io.github.azeezhamzat.ytdlmanager.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpArgsTest {
    private fun video(q: VideoQuality) = DownloadOptions(MediaKind.VIDEO, quality = q)
    private fun audio(f: AudioFormat, meta: Boolean = true) =
        DownloadOptions(MediaKind.AUDIO, audioFormat = f, embedMetadata = meta)

    private fun List<Pair<String, String?>>.value(option: String) = firstOrNull { it.first == option }?.second
    private fun List<Pair<String, String?>>.has(option: String) = any { it.first == option }

    @Test fun bestVideoPrefersMp4WithoutCap() {
        assertEquals(
            "bestvideo*[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/bestvideo*+bestaudio/best",
            YtDlpArgs.formatSelector(video(VideoQuality.BEST)),
        )
    }

    @Test fun cappedVideoFallsBackToBestWhenNothingFitsTheCap() {
        assertEquals(
            "bestvideo*[ext=mp4][height<=?720]+bestaudio[ext=m4a]/best[ext=mp4][height<=?720]/" +
                "bestvideo*[height<=?720]+bestaudio/best[height<=?720]/bestvideo*+bestaudio/best",
            YtDlpArgs.formatSelector(video(VideoQuality.P720)),
        )
    }

    @Test fun everyQualityCapUsesItsHeight() {
        for (q in VideoQuality.entries) {
            val selector = YtDlpArgs.formatSelector(video(q))
            val h = q.maxHeight
            if (h == null) assertFalse(selector.contains("height")) else assertTrue(selector.contains("[height<=?$h]"))
        }
    }

    @Test fun m4aAudioPrefersAnM4aSourceSoNothingIsReencoded() {
        assertEquals("bestaudio[ext=m4a]/bestaudio/best", YtDlpArgs.formatSelector(audio(AudioFormat.M4A)))
        assertEquals("bestaudio/best", YtDlpArgs.formatSelector(audio(AudioFormat.MP3)))
    }

    @Test fun videoDownloadMergesToMp4AndHasNoCoverArt() {
        val args = YtDlpArgs.downloadArgs(video(VideoQuality.P1080), "/w/%(title)s.%(ext)s", "/c")
        assertEquals("mp4", args.value("--merge-output-format"))
        assertFalse(args.has("--extract-audio"))
        assertTrue(args.has("--embed-metadata"))
        assertFalse(args.has("--embed-thumbnail"))
        assertEquals("/w/%(title)s.%(ext)s", args.value("-o"))
    }

    @Test fun audioDownloadExtractsWithBestQualityAndCoverArt() {
        val args = YtDlpArgs.downloadArgs(audio(AudioFormat.MP3), "/w/x", "/c")
        assertTrue(args.has("--extract-audio"))
        assertEquals("mp3", args.value("--audio-format"))
        assertEquals("0", args.value("--audio-quality"))
        assertTrue(args.has("--embed-thumbnail"))
        assertFalse(args.has("--merge-output-format"))
    }

    @Test fun metadataCanBeTurnedOff() {
        val args = YtDlpArgs.downloadArgs(audio(AudioFormat.M4A, meta = false), "/w/x", "/c")
        assertFalse(args.has("--embed-metadata"))
        assertFalse(args.has("--embed-thumbnail"))
    }

    @Test fun neverUsesCookiesOrLoginsAndNeverDownloadsWholePlaylists() {
        for (args in listOf(
            YtDlpArgs.infoArgs("/c"),
            YtDlpArgs.downloadArgs(video(VideoQuality.BEST), "/w/x", "/c"),
            YtDlpArgs.downloadArgs(audio(AudioFormat.MP3), "/w/x", "/c"),
        )) {
            val options = args.map { it.first }
            assertTrue(options.contains("--no-playlist"))
            assertTrue(options.contains("--ignore-config"))
            assertFalse(options.any { it.startsWith("--cookies") || it == "--username" || it == "--password" || it == "--netrc" })
            assertEquals("/c", args.value("--cache-dir"))
        }
    }

    @Test fun infoIsASingleJsonObject() {
        val args = YtDlpArgs.infoArgs("/c")
        assertTrue(args.has("--dump-single-json"))
        assertTrue(args.has("--flat-playlist"))
    }

    @Test fun noOptionHasAnEmptyArgument() {
        // youtubedl-android treats "" as "no argument", which would shift the command.
        val args = YtDlpArgs.downloadArgs(video(VideoQuality.P480), "/w/x", "/c")
        assertTrue(args.none { it.second == "" })
    }

    @Test fun progressTemplateIsWhatTheParserReads() {
        val args = YtDlpArgs.downloadArgs(video(VideoQuality.BEST), "/w/x", "/c")
        val template = args.value("--progress-template")!!
        assertTrue(template.startsWith("download:" + YtDlpArgs.PROGRESS_TAG))
        assertEquals(7, template.substringAfter(YtDlpArgs.PROGRESS_TAG).split('|').size)
        assertTrue(args.has("--newline"))
    }
}
