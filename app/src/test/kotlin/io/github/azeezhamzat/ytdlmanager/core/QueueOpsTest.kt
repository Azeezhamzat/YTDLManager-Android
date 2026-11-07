package io.github.azeezhamzat.ytdlmanager.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueOpsTest {
    private fun task(id: String, state: TaskState, url: String = "https://youtu.be/$id") =
        DownloadTask(id = id, url = url, title = id, options = DownloadOptions(), state = state, createdAt = 0)

    @Test fun interruptedTasksGoBackInLine() {
        val out = QueueOps.recoverAfterRestart(listOf(task("a", TaskState.RUNNING), task("b", TaskState.FAILED)))
        assertEquals(listOf(TaskState.QUEUED, TaskState.FAILED), out.map { it.state })
    }

    @Test fun nextIsTheFirstQueuedInShownOrder() {
        val tasks = listOf(task("a", TaskState.FAILED), task("b", TaskState.QUEUED), task("c", TaskState.QUEUED))
        assertEquals("b", QueueOps.nextQueued(tasks)!!.id)
        assertNull(QueueOps.nextQueued(listOf(task("a", TaskState.CANCELLED))))
    }

    @Test fun botCheckHoldsOnlyQueuedYouTubeItems() {
        val tasks = listOf(
            task("yt", TaskState.QUEUED),
            task("tt", TaskState.QUEUED, url = "https://www.tiktok.com/@a/video/1"),
            task("run", TaskState.RUNNING),
        )
        val out = QueueOps.holdYouTubeAfterBotCheck(tasks).associateBy { it.id }
        assertEquals(TaskState.FAILED, out["yt"]!!.state)
        assertTrue(out["yt"]!!.error!!.startsWith("Not started"))
        assertEquals(TaskState.QUEUED, out["tt"]!!.state)
        assertEquals(TaskState.RUNNING, out["run"]!!.state)
    }

    @Test fun retryClearsTheError() {
        val failed = task("a", TaskState.FAILED).copy(error = "x", errorDetail = "y")
        val out = QueueOps.retry(listOf(failed), "a").single()
        assertEquals(TaskState.QUEUED, out.state)
        assertNull(out.error)
        assertNull(out.errorDetail)
    }

    @Test fun retryAllOnlyTouchesFailures() {
        val out = QueueOps.retryAllFailed(listOf(task("a", TaskState.FAILED), task("b", TaskState.CANCELLED)))
        assertEquals(listOf(TaskState.QUEUED, TaskState.CANCELLED), out.map { it.state })
    }

    @Test fun clearKeepsActiveWork() {
        val out = QueueOps.clearFinished(
            listOf(task("a", TaskState.FAILED), task("b", TaskState.QUEUED), task("c", TaskState.RUNNING), task("d", TaskState.CANCELLED)),
        )
        assertEquals(listOf("b", "c"), out.map { it.id })
    }
}
