package io.github.azeezhamzat.ytdlmanager.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.azeezhamzat.ytdlmanager.YtdlApp
import io.github.azeezhamzat.ytdlmanager.core.DownloadTask
import io.github.azeezhamzat.ytdlmanager.core.ErrorText
import io.github.azeezhamzat.ytdlmanager.core.Failure
import io.github.azeezhamzat.ytdlmanager.core.Formatters
import io.github.azeezhamzat.ytdlmanager.core.HistoryEntry
import io.github.azeezhamzat.ytdlmanager.core.Stage
import io.github.azeezhamzat.ytdlmanager.core.TaskProgress
import io.github.azeezhamzat.ytdlmanager.core.TaskState
import io.github.azeezhamzat.ytdlmanager.data.SettingsStore
import io.github.azeezhamzat.ytdlmanager.engine.YtDlpException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs the download queue: takes queued tasks one (or two) at a time until
 * none are left. WorkManager restarts it after the app is killed and holds it
 * back while "Wi-Fi only" is on and there's no Wi-Fi.
 */
class QueueWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val graph = (context.applicationContext as YtdlApp).graph
    private val repository = graph.repository

    override suspend fun doWork(): Result {
        if (!repository.hasQueued()) return Result.success()
        promoteToForeground("Starting downloads…", null)
        try {
            graph.engine.awaitReady()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val failure = Failure("yt-dlp couldn't start on this device: ${e.message}", null)
            while (true) {
                val task = repository.claimNext() ?: break
                repository.markFailed(task.id, failure)
            }
            return Result.success()
        }
        val lanes = graph.settings.current.parallelDownloads.coerceIn(1, SettingsStore.MAX_PARALLEL)
        coroutineScope {
            val notifier = launch { keepNotificationCurrent() }
            val workers = List(lanes) { launch { lane() } }
            workers.forEach { it.join() }
            notifier.cancel()
        }
        return Result.success()
    }

    private suspend fun lane() = coroutineScope {
        while (true) {
            ensureActive()
            val task = repository.claimNext() ?: break
            val job = launch { runTask(task) }
            repository.attachJob(task.id, job)
            if (repository.isCancelRequested(task.id)) job.cancel()
            try {
                job.join()
            } finally {
                withContext(NonCancellable) {
                    job.join()
                    repository.detachJob(task.id)
                    if (job.isCancelled) {
                        if (repository.consumeUserCancel(task.id)) {
                            repository.markCancelled(task.id)
                            repository.workDirFor(task.id).deleteRecursively()
                        } else {
                            // Android stopped us (Wi-Fi lost, app update...). Keep
                            // the partial files; yt-dlp resumes them next time.
                            repository.requeue(task.id)
                        }
                    }
                }
            }
        }
    }

    private suspend fun runTask(task: DownloadTask) {
        val workDir = repository.workDirFor(task.id)
        repository.setProgress(task.id, TaskProgress(Stage.STARTING))
        try {
            val file = graph.engine.download(task.id, task.url, task.options, workDir) { progress ->
                repository.setProgress(task.id, progress)
            }
            repository.setProgress(task.id, TaskProgress(Stage.SAVING))
            val saved = graph.saver.save(file)
            repository.complete(
                task.id,
                HistoryEntry(
                    id = task.id,
                    url = task.url,
                    title = task.title,
                    thumbnailUrl = task.thumbnailUrl,
                    options = task.options,
                    fileName = saved.displayName,
                    mimeType = saved.mimeType,
                    contentUri = saved.uri.toString(),
                    sizeBytes = saved.sizeBytes,
                    completedAt = System.currentTimeMillis(),
                ),
            )
            workDir.deleteRecursively()
            Notifications.finished(
                applicationContext, task.id.hashCode(), "Saved: ${task.title}",
                "Downloads/YTDL Manager/${saved.displayName}",
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: YtDlpException) {
            fail(task, ErrorText.describe(e.stderr), workDir)
        } catch (e: Exception) {
            Log.e(TAG, "task ${task.id} failed", e)
            fail(task, Failure("Download failed: ${e.message ?: e.javaClass.simpleName}", e.toString()), workDir)
        }
    }

    private fun fail(task: DownloadTask, failure: Failure, workDir: java.io.File) {
        repository.markFailed(task.id, failure)
        workDir.deleteRecursively()
        Notifications.finished(applicationContext, task.id.hashCode(), "Failed: ${task.title}", failure.message)
    }

    /** Mirrors the running task's progress in the ongoing notification, once a second. */
    private suspend fun keepNotificationCurrent() {
        while (true) {
            val running = repository.tasks.value.filter { it.state == TaskState.RUNNING }
            val queued = repository.tasks.value.count { it.state == TaskState.QUEUED }
            val first = running.firstOrNull()
            if (first != null) {
                val p = repository.progress.value[first.id]
                val more = (running.size - 1 + queued).let { if (it > 0) " · $it more" else "" }
                promoteToForeground(first.title, describe(p) + more, p?.fraction)
            }
            delay(1000)
        }
    }

    private fun describe(p: TaskProgress?): String = if (p == null) "Starting…" else when (p.stage) {
        Stage.STARTING -> "Starting…"
        Stage.DOWNLOADING -> buildString {
            append(if (p.audioPart) "Audio " else "")
            p.fraction?.let { append("${(it * 100).toInt()}%") } ?: append("Downloading")
            p.speedBytesPerSec?.let { append(" · ").append(Formatters.speed(it)) }
        }
        Stage.PROCESSING -> p.label ?: "Processing…"
        Stage.SAVING -> "Saving to Downloads…"
    }

    private suspend fun promoteToForeground(title: String, text: String?, fraction: Float? = null) {
        val notification = Notifications.progress(applicationContext, title, text ?: "", fraction)
        try {
            setForeground(
                ForegroundInfo(Notifications.PROGRESS_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC),
            )
        } catch (e: IllegalStateException) {
            // Android 12+ won't start a foreground service from the background
            // (e.g. Wi-Fi came back while the app was closed). The work still
            // runs; if Android stops it, tasks are re-queued and resume later.
            Log.w(TAG, "Couldn't run in the foreground", e)
        }
    }

    companion object {
        private const val TAG = "QueueWorker"
        private const val UNIQUE_NAME = "download-queue"

        /**
         * Makes sure a queue run is scheduled. [restart] replaces a running
         * one, needed when "Wi-Fi only" changes (it's part of the constraints).
         */
        fun schedule(context: Context, wifiOnly: Boolean, restart: Boolean = false) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<QueueWorker>().setConstraints(constraints).build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NAME,
                if (restart) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.APPEND_OR_REPLACE,
                request,
            )
        }
    }
}
