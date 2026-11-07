package io.github.azeezhamzat.ytdlmanager.engine

import android.content.Context
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import io.github.azeezhamzat.ytdlmanager.core.DownloadOptions
import io.github.azeezhamzat.ytdlmanager.core.FileNaming
import io.github.azeezhamzat.ytdlmanager.core.InfoParser
import io.github.azeezhamzat.ytdlmanager.core.MediaInfo
import io.github.azeezhamzat.ytdlmanager.core.ProgressParser
import io.github.azeezhamzat.ytdlmanager.core.TaskProgress
import io.github.azeezhamzat.ytdlmanager.core.YtDlpArgs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

sealed interface EngineState {
    data object Starting : EngineState
    data class Ready(val ytDlpVersion: String?) : EngineState
    data class Updating(val ytDlpVersion: String?) : EngineState
    data class Failed(val message: String) : EngineState
}

sealed interface UpdateResult {
    data class Updated(val version: String?) : UpdateResult
    data class AlreadyCurrent(val version: String?) : UpdateResult
    data object Busy : UpdateResult
    data class Failed(val message: String) : UpdateResult
}

/** Thrown when yt-dlp exits with an error; [stderr] is its full error output. */
class YtDlpException(val stderr: String, cause: Throwable? = null) : Exception(stderr, cause)

/**
 * The one place that talks to youtubedl-android (Python + yt-dlp + QuickJS +
 * FFmpeg packaged as native libraries).
 */
class YtDlpEngine(private val context: Context) {
    private val ytdl = YoutubeDL.getInstance()
    private val ready = CompletableDeferred<Unit>()
    private val _state = MutableStateFlow<EngineState>(EngineState.Starting)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    // yt-dlp reads its own zip lazily while running, so it must never be
    // replaced under a running command: updates wait for idle, and new
    // commands wait for an update to finish.
    private val updateLock = Mutex()
    private val activeCommands = AtomicInteger(0)

    private val cacheDir: String get() = File(context.noBackupFilesDir, "yt-dlp-cache").absolutePath

    /** Unpacks Python and FFmpeg on first launch (a few seconds), then reports the version. */
    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                ytdl.init(context)
                FFmpeg.getInstance().init(context)
                ready.complete(Unit)
                _state.value = EngineState.Ready(readVersion())
            } catch (e: Throwable) {
                Log.e(TAG, "init failed", e)
                _state.value = EngineState.Failed(e.message ?: e.javaClass.simpleName)
                ready.completeExceptionally(e)
            }
        }
    }

    suspend fun awaitReady() = ready.await()

    private suspend fun <T> command(block: suspend () -> T): T {
        awaitReady()
        updateLock.withLock { activeCommands.incrementAndGet() }
        try {
            return block()
        } finally {
            activeCommands.decrementAndGet()
        }
    }

    /**
     * Runs yt-dlp. Cancelling the calling coroutine interrupts the wait, and
     * youtubedl-android then destroys the process.
     */
    private suspend fun runYtDlp(
        request: YoutubeDLRequest,
        processId: String,
        onLine: ((String) -> Unit)? = null,
    ) = runInterruptible(Dispatchers.IO) {
        try {
            if (onLine == null) {
                ytdl.execute(request, processId, null)
            } else {
                ytdl.execute(request, processId) { _: Float, _: Long, line: String -> onLine(line) }
            }
        } catch (e: YoutubeDLException) {
            throw YtDlpException(e.message.orEmpty(), e)
        }
    }

    private fun YoutubeDLRequest.addAll(args: List<Pair<String, String?>>) = apply {
        for ((option, value) in args) if (value == null) addOption(option) else addOption(option, value)
    }

    suspend fun fetchInfo(url: String): MediaInfo = command {
        val request = YoutubeDLRequest(url).addAll(YtDlpArgs.infoArgs(cacheDir))
        val response = runYtDlp(request, "info-" + UUID.randomUUID())
        withContext(Dispatchers.Default) { InfoParser.parse(response.out, url) }
    }

    /**
     * Downloads into [workDir] and returns the finished file. Partial files
     * stay in [workDir], so a re-run after an interruption resumes them.
     */
    suspend fun download(
        taskId: String,
        url: String,
        options: DownloadOptions,
        workDir: File,
        onProgress: (TaskProgress) -> Unit,
    ): File = command {
        workDir.mkdirs()
        val template = File(workDir, YtDlpArgs.OUTPUT_TEMPLATE_NAME).absolutePath
        val request = YoutubeDLRequest(url).addAll(YtDlpArgs.downloadArgs(options, template, cacheDir))
        runYtDlp(request, taskId) { line -> ProgressParser.parse(line)?.let(onProgress) }
        workDir.listFiles()
            ?.filter { it.isFile && !FileNaming.isTemporary(it.name) }
            ?.maxByOrNull { it.length() }
            ?: throw YtDlpException("ERROR: yt-dlp finished but no file was produced")
    }

    /** Fetches the newest stable yt-dlp from its GitHub releases. */
    suspend fun updateYtDlp(): UpdateResult = withContext(Dispatchers.IO) {
        try {
            awaitReady()
        } catch (e: Throwable) {
            return@withContext UpdateResult.Failed(e.message ?: "yt-dlp isn't available")
        }
        updateLock.withLock {
            if (activeCommands.get() > 0) return@withLock UpdateResult.Busy
            val before = currentVersion()
            _state.value = EngineState.Updating(before)
            try {
                val status = ytdl.updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
                val version = readVersion()
                if (status == YoutubeDL.UpdateStatus.DONE) UpdateResult.Updated(version)
                else UpdateResult.AlreadyCurrent(version)
            } catch (e: Exception) {
                Log.w(TAG, "update failed", e)
                UpdateResult.Failed(e.message ?: e.javaClass.simpleName)
            } finally {
                _state.value = EngineState.Ready(currentVersion())
            }
        }
    }

    private fun currentVersion(): String? = when (val s = _state.value) {
        is EngineState.Ready -> s.ytDlpVersion
        is EngineState.Updating -> s.ytDlpVersion
        else -> null
    }

    /** Asks yt-dlp itself, which also covers the version bundled in the APK. */
    private fun readVersion(): String? = try {
        val request = YoutubeDLRequest(emptyList()).addOption("--ignore-config").addOption("--version")
        ytdl.execute(request, "version-" + UUID.randomUUID(), null).out.trim().lines().lastOrNull()
    } catch (e: Exception) {
        Log.w(TAG, "version check failed", e)
        ytdl.version(context)
    }

    companion object {
        private const val TAG = "YtDlpEngine"
    }
}
