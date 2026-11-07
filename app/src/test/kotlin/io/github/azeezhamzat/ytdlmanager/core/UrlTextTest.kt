package io.github.azeezhamzat.ytdlmanager.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlTextTest {
    @Test fun youTubeAppShareText() {
        assertEquals(
            "https://youtu.be/dQw4w9WgXcQ?si=abc123",
            UrlText.extractUrl("Check this out https://youtu.be/dQw4w9WgXcQ?si=abc123"),
        )
    }

    @Test fun plainLink() {
        assertEquals("https://www.youtube.com/watch?v=abc", UrlText.extractUrl("  https://www.youtube.com/watch?v=abc \n"))
    }

    @Test fun trailingSentencePunctuationIsDropped() {
        assertEquals("https://vimeo.com/123", UrlText.extractUrl("Watch https://vimeo.com/123."))
        assertEquals("https://x.com/a/status/1", UrlText.extractUrl("(see https://x.com/a/status/1)"))
        assertEquals("https://x.com/a/status/1", UrlText.extractUrl("“https://x.com/a/status/1”"))
    }

    @Test fun balancedParenthesesStay() {
        assertEquals(
            "https://en.wikipedia.org/wiki/Foo_(bar)",
            UrlText.extractUrl("https://en.wikipedia.org/wiki/Foo_(bar)"),
        )
    }

    @Test fun firstOfSeveralLinks() {
        assertEquals("https://a.example/1", UrlText.extractUrl("https://a.example/1 and https://b.example/2"))
    }

    @Test fun linkWithoutScheme() {
        assertEquals("https://youtu.be/abc", UrlText.extractUrl("youtu.be/abc"))
        assertEquals("https://www.tiktok.com/@me/video/1", UrlText.extractUrl("www.tiktok.com/@me/video/1"))
    }

    @Test fun noLink() {
        assertNull(UrlText.extractUrl("just some words"))
        assertNull(UrlText.extractUrl("someone@example.com"))
        assertNull(UrlText.extractUrl(""))
        assertNull(UrlText.extractUrl(null))
    }

    @Test fun recognisesYouTubeHosts() {
        assertTrue(UrlText.isYouTube("https://youtu.be/x"))
        assertTrue(UrlText.isYouTube("https://www.youtube.com/watch?v=x"))
        assertTrue(UrlText.isYouTube("https://m.youtube.com/shorts/x"))
        assertTrue(UrlText.isYouTube("https://music.youtube.com/watch?v=x"))
        assertFalse(UrlText.isYouTube("https://notyoutube.com/x"))
        assertFalse(UrlText.isYouTube("https://vimeo.com/1"))
    }

    @Test fun webUrlCheck() {
        assertTrue(UrlText.looksLikeWebUrl("https://vimeo.com/1"))
        assertFalse(UrlText.looksLikeWebUrl("ftp://example.com/file"))
        assertFalse(UrlText.looksLikeWebUrl("not a url"))
    }
}
