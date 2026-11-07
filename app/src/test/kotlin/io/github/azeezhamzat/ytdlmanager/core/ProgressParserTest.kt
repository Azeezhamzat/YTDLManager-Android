package io.github.azeezhamzat.ytdlmanager.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressParserTest {
    @Test fun videoPartWithKnownSize() {
        val p = ProgressParser.parse("[ytdlm] downloading|2500000|10000000|NA|1250000.5|6|avc1.640028")!!
        assertEquals(Stage.DOWNLOADING, p.stage)
        assertEquals(0.25f, p.fraction!!, 0.0001f)
        assertEquals(2_500_000L, p.downloadedBytes)
        assertEquals(10_000_000L, p.totalBytes)
        assertEquals(1250000.5, p.speedBytesPerSec!!, 0.01)
        assertEquals(6L, p.etaSeconds)
        assertFalse(p.audioPart)
    }

    @Test fun audioPartUsesTheEstimateWhenTheSizeIsUnknown() {
        val p = ProgressParser.parse("[ytdlm] downloading|500|NA|2000.0|NA|NA|none")!!
        assertEquals(0.25f, p.fraction!!, 0.0001f)
        assertEquals(2000L, p.totalBytes)
        assertNull(p.speedBytesPerSec)
        assertNull(p.etaSeconds)
        assertTrue(p.audioPart)
    }

    @Test fun finishedIsComplete() {
        assertEquals(1f, ProgressParser.parse("[ytdlm] finished|10|10|NA|NA|NA|vp9")!!.fraction)
    }

    @Test fun unknownTotalGivesNoFraction() {
        assertNull(ProgressParser.parse("[ytdlm] downloading|500|NA|NA|NA|NA|avc1")!!.fraction)
    }

    @Test fun postprocessingStages() {
        assertEquals("Merging video and audio", ProgressParser.parse("[Merger] Merging formats into \"/w/a.mp4\"")!!.label)
        assertEquals("Converting audio", ProgressParser.parse("[ExtractAudio] Destination: /w/a.mp3")!!.label)
        assertEquals(Stage.PROCESSING, ProgressParser.parse("[EmbedThumbnail] ffmpeg: Adding thumbnail")!!.stage)
    }

    @Test fun otherLinesAreIgnored() {
        assertNull(ProgressParser.parse("[youtube] abc: Downloading webpage"))
        assertNull(ProgressParser.parse("[ytdlm] garbage"))
        assertNull(ProgressParser.parse(""))
    }
}
