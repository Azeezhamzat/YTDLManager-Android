package io.github.azeezhamzat.ytdlmanager.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorTextTest {
    @Test fun botCheckWithTypographicApostrophe() {
        val f = ErrorText.describe(
            "WARNING: something\nERROR: [youtube] abc: Sign in to confirm you’re not a bot. Use --cookies-from-browser",
        )
        assertTrue(f.isBotCheck)
        assertEquals(ErrorText.BOT_CHECK_MESSAGE, f.message)
        assertTrue(f.detail!!.startsWith("ERROR:"))
    }

    @Test fun botCheckMessageNeverSuggestsSigningIn() {
        assertFalse(ErrorText.BOT_CHECK_MESSAGE.contains("cookies", ignoreCase = true))
        assertTrue(ErrorText.BOT_CHECK_MESSAGE.contains("doesn't sign in"))
    }

    @Test fun commonFailures() {
        assertEquals("This video is private.", ErrorText.describe("ERROR: [youtube] x: Private video. Sign in if you've been granted access").message)
        assertEquals(
            "This video is unavailable or has been removed.",
            ErrorText.describe("ERROR: [youtube] x: Video unavailable").message,
        )
        assertTrue(ErrorText.describe("ERROR: [youtube] x: Sign in to confirm your age").message.contains("age-restricted"))
        assertTrue(ErrorText.describe("ERROR: [Instagram] x: login required").message.contains("logged-in"))
        assertTrue(ErrorText.describe("ERROR: Unsupported URL: https://example.com").message.contains("isn't supported"))
        assertTrue(ErrorText.describe("ERROR: unable to download video data: HTTP Error 403: Forbidden").message.contains("403"))
        assertTrue(ErrorText.describe("ERROR: [Errno 28] No space left on device").message.contains("storage"))
        assertTrue(ErrorText.describe("ERROR: Unable to download webpage: <urlopen error timed out>").message.startsWith("Network"))
    }

    @Test fun unknownErrorsShowYtDlpsLine() {
        val f = ErrorText.describe("ERROR: something new went wrong")
        assertFalse(f.isBotCheck)
        assertEquals("Download failed: something new went wrong", f.message)
        assertEquals("Couldn't get video info: something new went wrong", ErrorText.describe("ERROR: something new went wrong", "Couldn't get video info").message)
    }

    @Test fun emptyStderr() {
        assertEquals("Download failed.", ErrorText.describe("").message)
        assertEquals("Download failed.", ErrorText.describe(null).message)
    }

    @Test fun lastErrorLineIsShortened() {
        val line = ErrorText.lastErrorLine("ERROR: " + "x".repeat(1000))!!
        assertTrue(line.length <= 401)
    }
}
