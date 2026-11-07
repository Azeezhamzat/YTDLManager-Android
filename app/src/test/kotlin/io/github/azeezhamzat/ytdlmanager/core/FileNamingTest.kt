package io.github.azeezhamzat.ytdlmanager.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNamingTest {
    @Test fun forbiddenCharactersBecomeUnderscores() {
        assertEquals("AC_DC_ Back in Black _Live_.mp4", FileNaming.sanitize("AC/DC: Back in Black \"Live\".mp4"))
        assertEquals("a_b_c.m4a", FileNaming.sanitize("a\\b|c.m4a"))
    }

    @Test fun whitespaceAndDotsAreTidied() {
        assertEquals("Title.mp3", FileNaming.sanitize("  Title  .mp3"))
        assertEquals("download.mp4", FileNaming.sanitize("....mp4"))
        assertEquals("a b.mp4", FileNaming.sanitize("a\t\nb.mp4"))
    }

    @Test fun longNamesKeepTheirExtensionAndFitTheByteBudget() {
        val name = FileNaming.sanitize("é".repeat(300) + ".mp4")
        assertTrue(name.endsWith(".mp4"))
        assertTrue(name.toByteArray(Charsets.UTF_8).size <= FileNaming.MAX_NAME_BYTES)
    }

    @Test fun truncationNeverSplitsACharacter() {
        val emoji = "😀".repeat(10) // 4 bytes each
        assertEquals("😀😀", FileNaming.truncateUtf8(emoji, 9))
    }

    @Test fun extensionIsLowercased() {
        assertEquals("Song.mp3", FileNaming.sanitize("Song.MP3"))
    }

    @Test fun mimeTypes() {
        assertEquals("video/mp4", FileNaming.mimeTypeFor("mp4"))
        assertEquals("audio/mp4", FileNaming.mimeTypeFor("m4a"))
        assertEquals("audio/mpeg", FileNaming.mimeTypeFor("MP3"))
        assertEquals("video/webm", FileNaming.mimeTypeFor("webm"))
        assertEquals("application/octet-stream", FileNaming.mimeTypeFor("xyz"))
    }

    @Test fun temporaryFilesAreNeverTheResult() {
        assertTrue(FileNaming.isTemporary("a.mp4.part"))
        assertTrue(FileNaming.isTemporary("a.f137.mp4.part-Frag3"))
        assertTrue(FileNaming.isTemporary("a.mp4.ytdl"))
        assertTrue(FileNaming.isTemporary("a.webp"))
        assertFalse(FileNaming.isTemporary("a.mp4"))
        assertFalse(FileNaming.isTemporary("a.m4a"))
    }
}
