package io.github.azeezhamzat.ytdlmanager.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InfoParserTest {
    @Test fun video() {
        val json = """
            {"id":"abc","title":"My clip","webpage_url":"https://www.youtube.com/watch?v=abc",
             "thumbnail":"https://i.ytimg.com/vi/abc/maxresdefault.jpg","uploader":"Me","duration":245.0,
             "extractor_key":"Youtube","live_status":"not_live","_type":"video",
             "formats":[{"format_id":"140","vcodec":"none","acodec":"mp4a"},
                        {"format_id":"137","vcodec":"avc1","height":1080},
                        {"format_id":"313","vcodec":"vp9","height":2160}]}
        """.trimIndent().replace("\n", "")
        val info = InfoParser.parse(json, "https://youtu.be/abc")
        assertEquals("My clip", info.title)
        assertEquals("https://www.youtube.com/watch?v=abc", info.url)
        assertEquals("https://i.ytimg.com/vi/abc/maxresdefault.jpg", info.thumbnailUrl)
        assertEquals("Me", info.uploader)
        assertEquals(245L, info.durationSeconds)
        assertEquals(2160, info.maxHeight)
        assertFalse(info.isPlaylist)
        assertFalse(info.isLive)
    }

    @Test fun warningsBeforeTheJsonAreSkipped() {
        val info = InfoParser.parse("WARNING: slow\n{\"id\":\"x\",\"title\":\"T\"}\n", "https://e.com/x")
        assertEquals("T", info.title)
        assertEquals("https://e.com/x", info.url)
        assertNull(info.maxHeight)
    }

    @Test fun playlist() {
        val info = InfoParser.parse(
            """{"_type":"playlist","id":"PL1","title":"Mix","entries":[{"id":"a"},{"id":"b"}]}""",
            "https://www.youtube.com/playlist?list=PL1",
        )
        assertTrue(info.isPlaylist)
        assertEquals(2, info.playlistCount)
    }

    @Test fun liveAndMissingFields() {
        val info = InfoParser.parse(
            """{"id":"x","is_live":true,"thumbnails":[{"url":"https://t/1.jpg"},{"url":"https://t/2.jpg"}]}""",
            "https://e.com/x",
        )
        assertTrue(info.isLive)
        assertEquals("x", info.title)
        assertEquals("https://t/2.jpg", info.thumbnailUrl)
    }

    @Test(expected = IllegalArgumentException::class)
    fun noJson() {
        InfoParser.parse("nothing here", "https://e.com/x")
    }
}
