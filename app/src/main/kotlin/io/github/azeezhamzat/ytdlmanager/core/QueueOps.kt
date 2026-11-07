package io.github.azeezhamzat.ytdlmanager.core

/** Pure queue transitions, shared by the repository and its tests. */
object QueueOps {
    /** After the app was killed mid-download, those tasks go back in line. */
    fun recoverAfterRestart(tasks: List<DownloadTask>): List<DownloadTask> =
        tasks.map { if (it.state == TaskState.RUNNING) it.copy(state = TaskState.QUEUED) else it }

    fun nextQueued(tasks: List<DownloadTask>): DownloadTask? =
        tasks.firstOrNull { it.state == TaskState.QUEUED }

    /**
     * YouTube's bot check is about the network, so every other queued YouTube
     * item would hit it too. Stop them instead of hammering YouTube; the user
     * can retry them later in one tap.
     */
    fun holdYouTubeAfterBotCheck(tasks: List<DownloadTask>): List<DownloadTask> =
        tasks.map {
            if (it.state == TaskState.QUEUED && UrlText.isYouTube(it.url)) {
                it.copy(
                    state = TaskState.FAILED,
                    error = "Not started: " + ErrorText.BOT_CHECK_MESSAGE,
                    errorDetail = null,
                )
            } else {
                it
            }
        }

    fun retry(tasks: List<DownloadTask>, id: String): List<DownloadTask> =
        tasks.map {
            if (it.id == id && (it.state == TaskState.FAILED || it.state == TaskState.CANCELLED)) {
                it.copy(state = TaskState.QUEUED, error = null, errorDetail = null)
            } else {
                it
            }
        }

    fun retryAllFailed(tasks: List<DownloadTask>): List<DownloadTask> =
        tasks.map {
            if (it.state == TaskState.FAILED) it.copy(state = TaskState.QUEUED, error = null, errorDetail = null) else it
        }

    fun clearFinished(tasks: List<DownloadTask>): List<DownloadTask> =
        tasks.filter { it.state == TaskState.QUEUED || it.state == TaskState.RUNNING }
}
