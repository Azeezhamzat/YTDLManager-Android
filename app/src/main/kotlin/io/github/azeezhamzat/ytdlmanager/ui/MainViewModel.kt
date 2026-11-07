package io.github.azeezhamzat.ytdlmanager.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.azeezhamzat.ytdlmanager.YtdlApp
import io.github.azeezhamzat.ytdlmanager.core.AudioFormat
import io.github.azeezhamzat.ytdlmanager.core.DownloadOptions
import io.github.azeezhamzat.ytdlmanager.core.DownloadTask
import io.github.azeezhamzat.ytdlmanager.core.ErrorText
import io.github.azeezhamzat.ytdlmanager.core.HistoryEntry
import io.github.azeezhamzat.ytdlmanager.core.MediaInfo
import io.github.azeezhamzat.ytdlmanager.core.MediaKind
import io.github.azeezhamzat.ytdlmanager.core.UrlText
import io.github.azeezhamzat.ytdlmanager.core.VideoQuality
import io.github.azeezhamzat.ytdlmanager.data.Settings
import io.github.azeezhamzat.ytdlmanager.engine.UpdateResult
import io.github.azeezhamzat.ytdlmanager.engine.YtDlpException
import io.github.azeezhamzat.ytdlmanager.work.QueueWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class Tab { DOWNLOAD, HISTORY, SETTINGS }

sealed interface FetchState {
    data object Idle : FetchState
    data class Loading(val url: String) : FetchState
    data class Loaded(val info: MediaInfo) : FetchState
    data class Error(val message: String, val detail: String? = null) : FetchState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as YtdlApp).graph
    private val repository = graph.repository

    val tasks = repository.tasks
    val progress = repository.progress
    val history = repository.history
    val settings = graph.settings.settings
    val engineState = graph.engine.state
    val network = graph.network.status

    var tab by mutableStateOf(Tab.DOWNLOAD)
    var link by mutableStateOf("")
    var fetch by mutableStateOf<FetchState>(FetchState.Idle)
        private set
    var kind by mutableStateOf(graph.settings.current.defaultKind)
    var quality by mutableStateOf(graph.settings.current.videoQuality)
    var audioFormat by mutableStateOf(graph.settings.current.audioFormat)
    var updating by mutableStateOf(false)
        private set
    var updateMessage by mutableStateOf<String?>(null)
        private set

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()
    private var fetchJob: Job? = null

    private fun say(text: String) {
        _messages.trySend(text)
    }

    fun onSharedText(text: String?) {
        tab = Tab.DOWNLOAD
        val url = UrlText.extractUrl(text)
        if (url == null) {
            say("No link found in what was shared.")
            return
        }
        link = url
        fetchInfo()
    }

    fun fetchInfo() {
        val url = UrlText.extractUrl(link) ?: link.trim()
        if (!UrlText.looksLikeWebUrl(url)) {
            fetch = FetchState.Error("That doesn't look like a web link. Paste something starting with https://")
            return
        }
        link = url
        resetChoices()
        fetchJob?.cancel()
        fetch = FetchState.Loading(url)
        fetchJob = viewModelScope.launch {
            fetch = try {
                val info = graph.engine.fetchInfo(url)
                when {
                    info.isPlaylist -> FetchState.Error(
                        "This is a playlist" + (info.playlistCount?.let { " ($it items)" } ?: "") +
                            ". This version downloads one video at a time: open a single video and " +
                            "share or paste its link.",
                    )
                    info.isLive -> FetchState.Error("Live streams can't be downloaded while they're live.")
                    else -> FetchState.Loaded(info)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: YtDlpException) {
                val failure = ErrorText.describe(e.stderr, "Couldn't get video info")
                FetchState.Error(failure.message, failure.detail)
            } catch (e: Exception) {
                FetchState.Error("Couldn't get video info: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun clearDraft() {
        fetchJob?.cancel()
        fetch = FetchState.Idle
        link = ""
        resetChoices()
    }

    private fun resetChoices() {
        val s = graph.settings.current
        kind = s.defaultKind
        quality = s.videoQuality
        audioFormat = s.audioFormat
    }

    /** Adds the previewed item to the queue. Returns true if something was queued. */
    fun download(): Boolean {
        val info = (fetch as? FetchState.Loaded)?.info ?: return false
        val s = graph.settings.current
        repository.enqueue(
            DownloadTask(
                id = UUID.randomUUID().toString(),
                url = info.url,
                title = info.title,
                thumbnailUrl = info.thumbnailUrl,
                uploader = info.uploader,
                durationSeconds = info.durationSeconds,
                options = DownloadOptions(kind, quality, audioFormat, s.embedMetadata),
                createdAt = System.currentTimeMillis(),
            ),
        )
        QueueWorker.schedule(getApplication<Application>(), s.wifiOnly)
        val net = network.value
        say(
            if (s.wifiOnly && !net.unmetered) "Queued. It starts when you're on Wi-Fi."
            else "Added to the queue",
        )
        clearDraft()
        return true
    }

    fun cancel(id: String) = repository.cancel(id)

    fun retry(id: String) {
        repository.retry(id)
        QueueWorker.schedule(getApplication<Application>(), graph.settings.current.wifiOnly)
    }

    fun retryAllFailed() {
        repository.retryAllFailed()
        QueueWorker.schedule(getApplication<Application>(), graph.settings.current.wifiOnly)
    }

    fun remove(id: String) = repository.remove(id)
    fun clearFinished() = repository.clearFinished()

    fun fileExists(entry: HistoryEntry) = graph.saver.exists(Uri.parse(entry.contentUri))
    fun removeHistory(entry: HistoryEntry) = repository.removeHistory(entry.id)

    fun deleteFile(entry: HistoryEntry) {
        val uri = Uri.parse(entry.contentUri)
        if (!graph.saver.exists(uri) || graph.saver.delete(uri)) {
            repository.removeHistory(entry.id)
            say("Deleted ${entry.fileName}")
        } else {
            say("Couldn't delete it here. Delete it in the Files app (Downloads/YTDL Manager).")
        }
    }

    fun clearHistory() = repository.clearHistory()

    // --- Settings

    fun updateSettings(change: (Settings) -> Settings) {
        val before = graph.settings.current
        graph.settings.update(change)
        val after = graph.settings.current
        if (before.wifiOnly != after.wifiOnly && repository.hasPending()) {
            // The network rule is part of the scheduled work, so reschedule.
            QueueWorker.schedule(getApplication<Application>(), after.wifiOnly, restart = true)
        }
        if (fetch !is FetchState.Loaded) resetChoices()
    }

    fun markAskedForNotifications() = graph.settings.update { it.copy(askedForNotifications = true) }

    fun updateYtDlp() {
        if (updating) return
        updating = true
        updateMessage = "Checking for a new yt-dlp…"
        viewModelScope.launch {
            val result = graph.engine.updateYtDlp()
            updateMessage = when (result) {
                is UpdateResult.Updated -> "Updated to ${result.version ?: "the latest version"}."
                is UpdateResult.AlreadyCurrent -> "Already up to date (${result.version ?: "latest"})."
                UpdateResult.Busy -> "Downloads are running. Try again when the queue is idle."
                is UpdateResult.Failed -> "Update failed: ${result.message}"
            }
            if (result is UpdateResult.Updated || result is UpdateResult.AlreadyCurrent) {
                graph.settings.update { it.copy(lastYtDlpCheck = System.currentTimeMillis()) }
            }
            updating = false
        }
    }

    companion object {
        val qualityChoices = VideoQuality.entries
        val audioChoices = AudioFormat.entries
        val kindChoices = MediaKind.entries
    }
}
