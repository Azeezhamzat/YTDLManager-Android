package io.github.azeezhamzat.ytdlmanager

import android.app.Application
import io.github.azeezhamzat.ytdlmanager.data.DownloadRepository
import io.github.azeezhamzat.ytdlmanager.data.NetworkMonitor
import io.github.azeezhamzat.ytdlmanager.data.NetworkStatus
import io.github.azeezhamzat.ytdlmanager.data.SettingsStore
import io.github.azeezhamzat.ytdlmanager.engine.EngineState
import io.github.azeezhamzat.ytdlmanager.engine.UpdateResult
import io.github.azeezhamzat.ytdlmanager.engine.YtDlpEngine
import io.github.azeezhamzat.ytdlmanager.storage.MediaStoreSaver
import io.github.azeezhamzat.ytdlmanager.work.Notifications
import io.github.azeezhamzat.ytdlmanager.work.QueueWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Everything the app shares, created once per process. */
class AppGraph(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsStore(app)
    val repository = DownloadRepository(app.filesDir, scope)
    val engine = YtDlpEngine(app)
    val saver = MediaStoreSaver(app.contentResolver)
    val network = NetworkMonitor(app)
}

/**
 * The yt-dlp inside the APK goes stale within weeks as YouTube changes, so by
 * default the app fetches the latest stable release at most once a day
 * (respecting "Wi-Fi only"). When it's due, the engine does it at startup
 * before running anything; if that isn't possible (offline, or the attempt
 * failed), it's retried once the network allows and nothing is downloading.
 */
class YtdlApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        Notifications.createChannels(this)
        graph.engine.start(
            graph.scope,
            updateDue = { ytDlpUpdateDue() && networkAllowsUpdate(graph.network.status.value) },
            onUpdate = ::recordUpdate,
        )
        graph.repository.pruneWorkDirs()
        // Tasks still queued from last time (WorkManager usually has this
        // covered already; scheduling again is harmless).
        if (graph.repository.hasQueued()) QueueWorker.schedule(this, graph.settings.current.wifiOnly)
        graph.scope.launch { retryMissedUpdate() }
    }

    private fun ytDlpUpdateDue(): Boolean {
        val s = graph.settings.current
        return s.autoUpdateYtDlp && System.currentTimeMillis() - s.lastYtDlpCheck >= DAY_MS
    }

    private fun networkAllowsUpdate(net: NetworkStatus): Boolean =
        net.connected && (!graph.settings.current.wifiOnly || net.unmetered)

    private fun recordUpdate(result: UpdateResult) {
        if (result is UpdateResult.Updated || result is UpdateResult.AlreadyCurrent) {
            graph.settings.update { it.copy(lastYtDlpCheck = System.currentTimeMillis()) }
        }
    }

    private suspend fun retryMissedUpdate() {
        val started = graph.engine.state.first { it is EngineState.Ready || it is EngineState.Failed }
        if (started is EngineState.Failed) return
        repeat(MAX_RETRIES) {
            if (!ytDlpUpdateDue()) return
            graph.network.status.first { networkAllowsUpdate(it) }
            val result = graph.engine.updateYtDlp(::recordUpdate)
            if (result is UpdateResult.Updated || result is UpdateResult.AlreadyCurrent) return
            delay(RETRY_DELAY_MS)
        }
    }

    companion object {
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val MAX_RETRIES = 6
        private const val RETRY_DELAY_MS = 5L * 60 * 1000
    }
}
