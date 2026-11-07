package io.github.azeezhamzat.ytdlmanager

import android.app.Application
import io.github.azeezhamzat.ytdlmanager.data.DownloadRepository
import io.github.azeezhamzat.ytdlmanager.data.NetworkMonitor
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

class YtdlApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        Notifications.createChannels(this)
        graph.engine.start(graph.scope)
        graph.repository.pruneWorkDirs()
        // Tasks still queued from last time (WorkManager usually has this
        // covered already; scheduling again is harmless).
        if (graph.repository.hasQueued()) QueueWorker.schedule(this, graph.settings.current.wifiOnly)
        graph.scope.launch { autoUpdateYtDlp() }
    }

    /**
     * The yt-dlp inside the APK goes stale within weeks as YouTube changes, so
     * by default the app fetches the latest stable release at most once a day
     * (respecting "Wi-Fi only").
     */
    private suspend fun autoUpdateYtDlp() {
        val s = graph.settings.current
        if (!s.autoUpdateYtDlp) return
        if (System.currentTimeMillis() - s.lastYtDlpCheck < DAY_MS) return
        graph.engine.state.first { it !is EngineState.Starting }
        val net = graph.network.status.value
        if (!net.connected || (s.wifiOnly && !net.unmetered)) return
        when (graph.engine.updateYtDlp()) {
            is UpdateResult.Updated, is UpdateResult.AlreadyCurrent ->
                graph.settings.update { it.copy(lastYtDlpCheck = System.currentTimeMillis()) }
            else -> Unit
        }
    }

    companion object {
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
