package io.github.azeezhamzat.ytdlmanager.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegalNoticeTest {
    @Test fun matchesTheDesktopWording() {
        // Sentences shared word for word with the desktop app's config.LEGAL_NOTICE.
        listOf(
            "Only download videos you own, that are in the public domain, that carry a licence permitting download, " +
                "or for which you otherwise have the rights holder's explicit permission.",
            "This application does not bypass DRM, CAPTCHAs, paywalls, or other access controls.",
            "It cannot guarantee that every item on YouTube, TikTok, Twitter/X, Instagram, Facebook, or Vimeo " +
                "is legally or technically downloadable.",
        ).forEach { assertTrue(it, LegalNotice.TEXT.contains(it)) }
    }

    @Test fun saysThereAreNoLogins() {
        assertTrue(LegalNotice.TEXT.contains("It uses no logins or cookies."))
        assertFalse(LegalNotice.TEXT.contains("sign-in in Settings"))
    }
}
