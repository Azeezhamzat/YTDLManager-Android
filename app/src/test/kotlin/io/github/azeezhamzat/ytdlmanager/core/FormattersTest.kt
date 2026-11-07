package io.github.azeezhamzat.ytdlmanager.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {
    @Test fun bytes() {
        assertEquals("999 B", Formatters.bytes(999))
        assertEquals("1.5 kB", Formatters.bytes(1_500))
        assertEquals("45.2 MB", Formatters.bytes(45_200_000))
        assertEquals("120 MB", Formatters.bytes(120_000_000))
        assertEquals("2.1 GB", Formatters.bytes(2_100_000_000))
    }

    @Test fun duration() {
        assertEquals("0:05", Formatters.duration(5))
        assertEquals("4:05", Formatters.duration(245))
        assertEquals("1:02:03", Formatters.duration(3723))
    }

    @Test fun optionsDescription() {
        assertEquals("Video · best quality", DownloadOptions(MediaKind.VIDEO, VideoQuality.BEST).describe())
        assertEquals("Video · 720p max", DownloadOptions(MediaKind.VIDEO, VideoQuality.P720).describe())
        assertEquals("Audio · MP3", DownloadOptions(MediaKind.AUDIO, audioFormat = AudioFormat.MP3).describe())
    }
}
