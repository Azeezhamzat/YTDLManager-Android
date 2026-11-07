@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.azeezhamzat.ytdlmanager.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.azeezhamzat.ytdlmanager.core.AudioFormat
import io.github.azeezhamzat.ytdlmanager.core.DownloadTask
import io.github.azeezhamzat.ytdlmanager.core.Formatters
import io.github.azeezhamzat.ytdlmanager.core.MediaInfo
import io.github.azeezhamzat.ytdlmanager.core.MediaKind
import io.github.azeezhamzat.ytdlmanager.core.Stage
import io.github.azeezhamzat.ytdlmanager.core.TaskProgress
import io.github.azeezhamzat.ytdlmanager.core.TaskState
import io.github.azeezhamzat.ytdlmanager.engine.EngineState
import io.github.azeezhamzat.ytdlmanager.ui.FetchState
import io.github.azeezhamzat.ytdlmanager.ui.MainViewModel
import io.github.azeezhamzat.ytdlmanager.ui.Thumbnail

@Composable
fun DownloadScreen(vm: MainViewModel, onDownload: () -> Unit, modifier: Modifier = Modifier) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val engine by vm.engineState.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val network by vm.network.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        engineBanner(engine)?.let { text -> item(key = "engine") { InfoCard(text) } }

        item(key = "link") { LinkCard(vm) }

        when (val f = vm.fetch) {
            FetchState.Idle -> if (tasks.isEmpty()) item(key = "hint") { EmptyHint() }
            is FetchState.Loading -> item(key = "loading") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Fetching title and thumbnail…")
                }
            }
            is FetchState.Error -> item(key = "error") { ErrorCard(f.message, f.detail) }
            is FetchState.Loaded -> item(key = "preview") { PreviewCard(vm, f.info, onDownload) }
        }

        if (tasks.isNotEmpty()) {
            item(key = "queue-header") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Queue", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (tasks.any { it.state == TaskState.FAILED }) {
                        TextButton(onClick = vm::retryAllFailed) { Text("Retry failed") }
                    }
                    if (tasks.any { it.state == TaskState.FAILED || it.state == TaskState.CANCELLED }) {
                        TextButton(onClick = vm::clearFinished) { Text("Clear") }
                    }
                }
            }
            val waiting = tasks.any { it.state == TaskState.QUEUED }
            if (waiting && !network.connected) {
                item(key = "wait") { InfoCard("Waiting for an internet connection.") }
            } else if (waiting && settings.wifiOnly && !network.unmetered) {
                item(key = "wait") { InfoCard("Waiting for Wi-Fi. \"Wi-Fi only\" is on in Settings.") }
            }
            items(tasks, key = { it.id }) { task ->
                TaskRow(
                    task = task,
                    progress = progress[task.id],
                    onCancel = { vm.cancel(task.id) },
                    onRetry = { vm.retry(task.id) },
                    onRemove = { vm.remove(task.id) },
                )
            }
        }
    }
}

private fun engineBanner(state: EngineState): String? = when (state) {
    EngineState.Starting -> "Getting yt-dlp ready. The first launch unpacks Python and FFmpeg, which takes a few seconds."
    is EngineState.Updating -> "Updating yt-dlp…"
    is EngineState.Failed -> "yt-dlp couldn't start on this device: ${state.message}"
    is EngineState.Ready -> null
}

@Composable
private fun LinkCard(vm: MainViewModel) {
    val context = LocalContext.current
    val loading = vm.fetch is FetchState.Loading
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = vm.link,
            onValueChange = { vm.link = it },
            label = { Text("Video link") },
            placeholder = { Text("https://…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { vm.fetchInfo() }),
            trailingIcon = {
                if (vm.link.isNotEmpty()) {
                    IconButton(onClick = vm::clearDraft) { Icon(Icons.Default.Clear, contentDescription = "Clear") }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    clipboardText(context)?.let {
                        vm.link = it
                        vm.fetchInfo()
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text("Paste") }
            Button(
                onClick = vm::fetchInfo,
                enabled = vm.link.isNotBlank() && !loading,
                modifier = Modifier.weight(1f),
            ) { Text("Fetch") }
        }
    }
}

private fun clipboardText(context: Context): String? {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
    val item = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0) ?: return null
    return item.coerceToText(context)?.toString()?.takeIf { it.isNotBlank() }
}

@Composable
private fun PreviewCard(vm: MainViewModel, info: MediaInfo, onDownload: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        AsyncImage(
            model = info.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        )
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(info.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(info.uploader, info.durationSeconds?.let(Formatters::duration), info.site)
            if (meta.isNotEmpty()) {
                Text(
                    meta.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                MainViewModel.kindChoices.forEachIndexed { index, k ->
                    SegmentedButton(
                        selected = vm.kind == k,
                        onClick = { vm.kind = k },
                        shape = SegmentedButtonDefaults.itemShape(index, MainViewModel.kindChoices.size),
                    ) { Text(if (k == MediaKind.VIDEO) "Video" else "Audio") }
                }
            }
            if (vm.kind == MediaKind.VIDEO) {
                Text("Max quality", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MainViewModel.qualityChoices.forEach { q ->
                        FilterChip(selected = vm.quality == q, onClick = { vm.quality = q }, label = { Text(q.label) })
                    }
                }
                Text(
                    (info.maxHeight?.let { "Available up to ${it}p. " } ?: "") + "Saved as MP4.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("Format", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MainViewModel.audioChoices.forEach { a ->
                        FilterChip(selected = vm.audioFormat == a, onClick = { vm.audioFormat = a }, label = { Text(a.label) })
                    }
                }
                Text(
                    if (vm.audioFormat == AudioFormat.M4A) "M4A keeps the original AAC audio when the site offers it (no re-encoding)."
                    else "MP3 is converted from the original audio and plays everywhere.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onDownload, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Download") }
            Text(
                "Saved to Downloads/YTDL Manager",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun TaskRow(
    task: DownloadTask,
    progress: TaskProgress?,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    var showDetail by rememberSaveable(task.id) { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Thumbnail(task.thumbnailUrl, 96.dp, 54.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(task.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    task.options.describe(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when (task.state) {
                    TaskState.QUEUED -> Text("Queued", style = MaterialTheme.typography.bodySmall)
                    TaskState.RUNNING -> {
                        Text(statusLine(task, progress), style = MaterialTheme.typography.bodySmall)
                        val fraction = progress?.fraction
                        if (fraction != null && progress?.stage == Stage.DOWNLOADING) {
                            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(4.dp))
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp))
                        }
                    }
                    TaskState.FAILED -> {
                        Text(
                            task.error ?: "Failed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.clickable(enabled = task.errorDetail != null) { showDetail = !showDetail },
                        )
                        if (showDetail && task.errorDetail != null) {
                            Text(task.errorDetail, style = MaterialTheme.typography.labelSmall)
                        } else if (task.errorDetail != null) {
                            Text("Tap for details", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    TaskState.CANCELLED -> Text("Cancelled", style = MaterialTheme.typography.bodySmall)
                }
            }
            when (task.state) {
                TaskState.QUEUED, TaskState.RUNNING ->
                    IconButton(onClick = onCancel) { Icon(Icons.Default.Close, contentDescription = "Cancel") }
                TaskState.FAILED, TaskState.CANCELLED -> Column {
                    IconButton(onClick = onRetry) { Icon(Icons.Default.Refresh, contentDescription = "Retry") }
                    IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, contentDescription = "Remove") }
                }
            }
        }
    }
}

private fun statusLine(task: DownloadTask, p: TaskProgress?): String = if (p == null) "Starting…" else when (p.stage) {
    Stage.STARTING -> "Starting…"
    Stage.PROCESSING -> (p.label ?: "Processing") + "…"
    Stage.SAVING -> "Saving to Downloads…"
    Stage.DOWNLOADING -> buildList {
        add(
            when {
                task.options.kind == MediaKind.AUDIO -> "Downloading"
                p.audioPart -> "Downloading audio"
                else -> "Downloading video"
            },
        )
        p.fraction?.let { add("${(it * 100).toInt()}%") }
        val done = p.downloadedBytes
        val total = p.totalBytes
        if (done != null && total != null) add("${Formatters.bytes(done)} of ${Formatters.bytes(total)}")
        p.speedBytesPerSec?.let { add(Formatters.speed(it)) }
        p.etaSeconds?.let { add("${Formatters.duration(it)} left") }
    }.joinToString(" · ")
}

@Composable
private fun InfoCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun ErrorCard(message: String, detail: String?) {
    var showDetail by rememberSaveable(message) { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            if (detail != null) {
                Text(
                    if (showDetail) detail else "Show details",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.clickable { showDetail = !showDetail },
                )
            }
        }
    }
}

@Composable
private fun EmptyHint() {
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Paste a link above, or share one from YouTube or your browser: tap Share, then YTDL Manager.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Files are saved to Downloads/YTDL Manager. Works with YouTube, TikTok, Twitter/X, " +
                "Instagram, Facebook, Vimeo and the other sites yt-dlp supports.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
