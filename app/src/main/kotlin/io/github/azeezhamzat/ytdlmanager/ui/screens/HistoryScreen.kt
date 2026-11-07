package io.github.azeezhamzat.ytdlmanager.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.azeezhamzat.ytdlmanager.core.Formatters
import io.github.azeezhamzat.ytdlmanager.core.HistoryEntry
import io.github.azeezhamzat.ytdlmanager.ui.MainViewModel
import io.github.azeezhamzat.ytdlmanager.ui.Thumbnail
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val history by vm.history.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var removing by remember { mutableStateOf<HistoryEntry?>(null) }

    if (history.isEmpty()) {
        Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "Finished downloads appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(history, key = { it.id }) { entry ->
            HistoryRow(
                entry = entry,
                onOpen = { if (checkExists(context, vm, entry)) open(context, entry) },
                onShare = { if (checkExists(context, vm, entry)) share(context, entry) },
                onRemove = { removing = entry },
            )
        }
    }

    removing?.let { entry ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove from history?") },
            text = { Text("Remove \"${entry.fileName}\" from the list only, or also delete the file from Downloads/YTDL Manager?") },
            confirmButton = {
                TextButton(onClick = { vm.removeHistory(entry); removing = null }) { Text("List only") }
            },
            dismissButton = {
                TextButton(onClick = { vm.deleteFile(entry); removing = null }) {
                    Text("Delete file", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onOpen: () -> Unit, onShare: () -> Unit, onRemove: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Thumbnail(entry.thumbnailUrl, 96.dp, 54.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(
                            entry.options.describe(),
                            Formatters.bytes(entry.sizeBytes),
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(entry.completedAt)),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        entry.fileName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.align(Alignment.End)) {
                TextButton(onClick = onOpen) { Text("Open") }
                TextButton(onClick = onShare) { Text("Share") }
                TextButton(onClick = onRemove) { Text("Remove") }
            }
        }
    }
}

private fun checkExists(context: Context, vm: MainViewModel, entry: HistoryEntry): Boolean {
    if (vm.fileExists(entry)) return true
    Toast.makeText(context, "This file was moved or deleted.", Toast.LENGTH_SHORT).show()
    return false
}

private fun open(context: Context, entry: HistoryEntry) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(Uri.parse(entry.contentUri), entry.mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No app on this phone can open ${entry.mimeType} files.", Toast.LENGTH_SHORT).show()
    }
}

private fun share(context: Context, entry: HistoryEntry) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType(entry.mimeType)
        .putExtra(Intent.EXTRA_STREAM, Uri.parse(entry.contentUri))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, entry.title))
}
