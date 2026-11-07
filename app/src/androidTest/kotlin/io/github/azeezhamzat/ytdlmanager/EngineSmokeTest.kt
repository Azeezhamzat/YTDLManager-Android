package io.github.azeezhamzat.ytdlmanager

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.azeezhamzat.ytdlmanager.core.AudioFormat
import io.github.azeezhamzat.ytdlmanager.core.DownloadOptions
import io.github.azeezhamzat.ytdlmanager.core.ErrorText
import io.github.azeezhamzat.ytdlmanager.core.MediaKind
import io.github.azeezhamzat.ytdlmanager.core.VideoQuality
import io.github.azeezhamzat.ytdlmanager.engine.EngineState
import io.github.azeezhamzat.ytdlmanager.engine.YtDlpException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Runs the real Python + yt-dlp + FFmpeg + QuickJS runtime on a device or
 * emulator. Needs internet. Override the sample with
 * `-Pandroid.testInstrumentationRunnerArguments.sampleUrl=...`.
 */
@RunWith(AndroidJUnit4::class)
class EngineSmokeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val graph = (context.applicationContext as YtdlApp).graph
    private val engine = graph.engine
    private val sampleUrl = InstrumentationRegistry.getArguments().getString("sampleUrl") ?: SAMPLE

    private fun ready() = runBlocking {
        withTimeout(180_000) { engine.state.first { it is EngineState.Ready } as EngineState.Ready }
    }

    /** Waits for the first-launch update to replace the yt-dlp bundled in the APK. */
    private fun updated(): EngineState.Ready = runBlocking {
        val result = withTimeoutOrNull(300_000) {
            engine.state.first { it is EngineState.Ready && (it.ytDlpVersion ?: "") > BUNDLED_YT_DLP } as EngineState.Ready
        }
        result ?: throw AssertionError("yt-dlp wasn't updated from the bundled $BUNDLED_YT_DLP: ${engine.state.value}")
    }

    @Test
    fun engineStartsAndReportsItsVersion() {
        val version = ready().ytDlpVersion
        Log.i(TAG, "yt-dlp version: $version")
        assertTrue("version: $version", version != null && Regex("""\d{4}\.\d{2}\.\d{2}.*""").matches(version))
    }

    /** YouTube rejects the months-old copy in the APK, so this must work. */
    @Test
    fun ytDlpIsUpdatedOnFirstLaunch() {
        Log.i(TAG, "updated yt-dlp: ${updated().ytDlpVersion}")
    }

    @Test
    fun downloadsVideoConvertsAudioAndSavesToDownloads(): Unit = runBlocking {
        ready()
        withTimeout(600_000) {
            val info = engine.fetchInfo(sampleUrl)
            Log.i(TAG, "info: $info")
            assertTrue(info.title.isNotBlank())

            val videoDir = File(context.cacheDir, "smoke-video").apply { deleteRecursively() }
            val video = engine.download("smoke-video", sampleUrl, DownloadOptions(MediaKind.VIDEO, VideoQuality.P720), videoDir) {}
            Log.i(TAG, "video: ${video.name} ${video.length()}")
            assertEquals("mp4", video.extension)
            assertTrue(video.length() > 100_000)

            // MP3 needs FFmpeg to re-encode: proves the bundled FFmpeg runs.
            val audioDir = File(context.cacheDir, "smoke-audio").apply { deleteRecursively() }
            val audio = engine.download(
                "smoke-audio", sampleUrl,
                DownloadOptions(MediaKind.AUDIO, audioFormat = AudioFormat.MP3), audioDir,
            ) {}
            Log.i(TAG, "audio: ${audio.name} ${audio.length()}")
            assertEquals("mp3", audio.extension)
            assertTrue(audio.length() > 10_000)

            val saved = graph.saver.save(audio)
            Log.i(TAG, "saved: ${saved.uri} ${saved.displayName}")
            assertTrue(graph.saver.exists(saved.uri))
            assertTrue(graph.saver.delete(saved.uri))
            videoDir.deleteRecursively()
            audioDir.deleteRecursively()
        }
    }

    /**
     * YouTube needs the bundled QuickJS to solve its JavaScript challenges;
     * without it, yt-dlp only sees a few low-quality formats. Skipped (not
     * failed) when YouTube bot-checks the test machine's network, which is
     * common on CI.
     */
    @Test
    fun youTubeFormatsNeedTheJavaScriptRuntime(): Unit = runBlocking {
        updated()
        withTimeout(600_000) {
            val info = try {
                engine.fetchInfo(YOUTUBE_SAMPLE)
            } catch (e: YtDlpException) {
                assumeFalse("YouTube bot-checked this network: ${e.stderr}", ErrorText.isBotCheck(e.stderr))
                throw e
            }
            Log.i(TAG, "youtube: ${info.title} maxHeight=${info.maxHeight}")
            assertTrue("only ${info.maxHeight}p offered", (info.maxHeight ?: 0) >= 1080)
        }
    }

    companion object {
        private const val TAG = "EngineSmokeTest"
        // yt-dlp version inside youtubedl-android 0.18.1 (versions are dates, so they sort as text).
        private const val BUNDLED_YT_DLP = "2025.11.12"
        // 5-second 720p clip with AAC audio (2.8 MB), published for testing.
        private const val SAMPLE = "https://download.samplelib.com/mp4/sample-5s.mp4"
        // "Big Buck Bunny" by the Blender Foundation (CC BY 3.0), official upload.
        private const val YOUTUBE_SAMPLE = "https://www.youtube.com/watch?v=aqz-KE-bpKQ"
    }
}
