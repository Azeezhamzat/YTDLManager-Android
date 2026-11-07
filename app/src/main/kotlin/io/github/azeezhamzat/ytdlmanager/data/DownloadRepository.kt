package io.github.azeezhamzat.ytdlmanager.data

import android.util.Log
import io.github.azeezhamzat.ytdlmanager.core.DownloadTask
import io.github.azeezhamzat.ytdlmanager.core.Failure
import io.github.azeezhamzat.ytdlmanager.core.HistoryEntry
import io.github.azeezhamzat.ytdlmanager.core.QueueOps
import io.github.azeezhamzat.ytdlmanager.core.TaskProgress
import io.github.azeezhamzat.ytdlmanager.core.TaskState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * The download queue and history: in-memory state flows, saved as small JSON
 * files in the app's private storage. Progress is memory-only (it changes
 * several times a second).
 */
class DownloadRepository(private val dataDir: File, scope: CoroutineScope) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val queueFile = File(dataDir, "queue.json")
    private val historyFile = File(dataDir, "history.json")

    private val _tasks = MutableStateFlow(QueueOps.recoverAfterRestart(load(queueFile, TASKS)))
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val _history = MutableStateFlow(load(historyFile, HISTORY))
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    private val _progress = MutableStateFlow<Map<String, TaskProgress>>(emptyMap())
    val progress: StateFlow<Map<String, TaskProgress>> = _progress.asStateFlow()

    private val runningJobs = ConcurrentHashMap<String, Job>()
    private val userCancelled = ConcurrentHashMap.newKeySet<String>()

    /** Private per-task folder where yt-dlp writes before the file is published. */
    val workRoot: File = File(dataDir, "work")

    init {
        // StateFlow conflates, so a burst of changes becomes one write.
        scope.launch(Dispatchers.IO) { _tasks.drop(1).collect { save(queueFile, TASKS, it) } }
        scope.launch(Dispatchers.IO) { _history.drop(1).collect { save(historyFile, HISTORY, it) } }
    }

    fun workDirFor(taskId: String) = File(workRoot, taskId)

    /** Deletes work folders of tasks that no longer exist (e.g. after "Clear"). */
    fun pruneWorkDirs() {
        val keep = _tasks.value.map { it.id }.toSet()
        workRoot.listFiles()?.filter { it.name !in keep }?.forEach { it.deleteRecursively() }
    }

    @Synchronized
    fun enqueue(task: DownloadTask) {
        _tasks.value = _tasks.value + task
    }

    /** Atomically takes the next queued task and marks it running. */
    @Synchronized
    fun claimNext(): DownloadTask? {
        val next = QueueOps.nextQueued(_tasks.value) ?: return null
        setState(next.id) { it.copy(state = TaskState.RUNNING, error = null, errorDetail = null) }
        return next
    }

    fun hasQueued(): Boolean = _tasks.value.any { it.state == TaskState.QUEUED }
    fun hasPending(): Boolean = _tasks.value.any { it.state == TaskState.QUEUED || it.state == TaskState.RUNNING }

    fun setProgress(id: String, progress: TaskProgress) {
        _progress.value = _progress.value + (id to progress)
    }

    private fun clearProgress(id: String) {
        _progress.value = _progress.value - id
    }

    @Synchronized
    fun complete(id: String, entry: HistoryEntry) {
        _tasks.value = _tasks.value.filterNot { it.id == id }
        _history.value = (listOf(entry) + _history.value).take(MAX_HISTORY)
        clearProgress(id)
        userCancelled.remove(id)
    }

    @Synchronized
    fun markFailed(id: String, failure: Failure) {
        setState(id) { it.copy(state = TaskState.FAILED, error = failure.message, errorDetail = failure.detail) }
        clearProgress(id)
        if (failure.isBotCheck) _tasks.value = QueueOps.holdYouTubeAfterBotCheck(_tasks.value)
    }

    /** Back in line, e.g. when Android stopped the queue (Wi-Fi lost). */
    @Synchronized
    fun requeue(id: String) {
        setState(id) { it.copy(state = TaskState.QUEUED) }
        clearProgress(id)
    }

    @Synchronized
    fun markCancelled(id: String) {
        setState(id) { it.copy(state = TaskState.CANCELLED) }
        clearProgress(id)
    }

    // --- Cancellation: the queue worker registers the job running each task.

    fun attachJob(id: String, job: Job) {
        runningJobs[id] = job
    }

    fun detachJob(id: String) {
        runningJobs.remove(id)
    }

    fun isCancelRequested(id: String): Boolean = id in userCancelled

    /** True once if the user (not Android) cancelled this task. */
    fun consumeUserCancel(id: String): Boolean = userCancelled.remove(id)

    @Synchronized
    fun cancel(id: String) {
        val task = _tasks.value.firstOrNull { it.id == id } ?: return
        when (task.state) {
            TaskState.QUEUED -> markCancelled(id)
            TaskState.RUNNING -> {
                // The queue worker turns this into CANCELLED once yt-dlp stops.
                userCancelled += id
                runningJobs[id]?.cancel()
            }
            else -> Unit
        }
    }

    @Synchronized
    fun retry(id: String) {
        _tasks.value = QueueOps.retry(_tasks.value, id)
    }

    @Synchronized
    fun retryAllFailed() {
        _tasks.value = QueueOps.retryAllFailed(_tasks.value)
    }

    @Synchronized
    fun remove(id: String) {
        val task = _tasks.value.firstOrNull { it.id == id } ?: return
        if (task.state == TaskState.RUNNING) return
        _tasks.value = _tasks.value.filterNot { it.id == id }
        workDirFor(id).deleteRecursively()
    }

    @Synchronized
    fun clearFinished() {
        _tasks.value = QueueOps.clearFinished(_tasks.value)
        pruneWorkDirs()
    }

    @Synchronized
    fun removeHistory(id: String) {
        _history.value = _history.value.filterNot { it.id == id }
    }

    @Synchronized
    fun clearHistory() {
        _history.value = emptyList()
    }

    private fun setState(id: String, change: (DownloadTask) -> DownloadTask) {
        _tasks.value = _tasks.value.map { if (it.id == id) change(it) else it }
    }

    private fun <T> load(file: File, serializer: kotlinx.serialization.KSerializer<List<T>>): List<T> =
        try {
            if (file.exists()) json.decodeFromString(serializer, file.readText()) else emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Ignoring unreadable ${file.name}", e)
            emptyList()
        }

    private fun <T> save(file: File, serializer: kotlinx.serialization.KSerializer<List<T>>, value: List<T>) {
        try {
            dataDir.mkdirs()
            val tmp = File(dataDir, file.name + ".tmp")
            tmp.writeText(json.encodeToString(serializer, value))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't save ${file.name}", e)
        }
    }

    companion object {
        private const val TAG = "DownloadRepository"
        private const val MAX_HISTORY = 500
        private val TASKS = ListSerializer(DownloadTask.serializer())
        private val HISTORY = ListSerializer(HistoryEntry.serializer())
    }
}
