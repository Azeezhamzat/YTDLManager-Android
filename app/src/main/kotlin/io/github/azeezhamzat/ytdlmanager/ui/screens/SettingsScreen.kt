@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.azeezhamzat.ytdlmanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.azeezhamzat.ytdlmanager.BuildConfig
import io.github.azeezhamzat.ytdlmanager.core.LegalNotice
import io.github.azeezhamzat.ytdlmanager.core.MediaKind
import io.github.azeezhamzat.ytdlmanager.data.SettingsStore
import io.github.azeezhamzat.ytdlmanager.engine.EngineState
import io.github.azeezhamzat.ytdlmanager.ui.MainViewModel
import io.github.azeezhamzat.ytdlmanager.ui.SectionTitle

@Composable
fun SettingsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val engine by vm.engineState.collectAsStateWithLifecycle()

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        SectionTitle("Downloads")
        Label("Default type")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            MainViewModel.kindChoices.forEachIndexed { i, k ->
                SegmentedButton(
                    selected = s.defaultKind == k,
                    onClick = { vm.updateSettings { it.copy(defaultKind = k) } },
                    shape = SegmentedButtonDefaults.itemShape(i, MainViewModel.kindChoices.size),
                ) { Text(if (k == MediaKind.VIDEO) "Video" else "Audio") }
            }
        }
        Label("Default max video quality")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MainViewModel.qualityChoices.forEach { q ->
                FilterChip(
                    selected = s.videoQuality == q,
                    onClick = { vm.updateSettings { it.copy(videoQuality = q) } },
                    label = { Text(q.label) },
                )
            }
        }
        Label("Default audio format")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MainViewModel.audioChoices.forEach { a ->
                FilterChip(
                    selected = s.audioFormat == a,
                    onClick = { vm.updateSettings { it.copy(audioFormat = a) } },
                    label = { Text(a.label) },
                )
            }
        }
        SwitchRow(
            title = "Wi-Fi only",
            body = "Queued downloads wait until you're on Wi-Fi (or another unmetered network).",
            checked = s.wifiOnly,
            onChange = { v -> vm.updateSettings { it.copy(wifiOnly = v) } },
        )
        Label("Downloads at once")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            (1..SettingsStore.MAX_PARALLEL).forEach { n ->
                SegmentedButton(
                    selected = s.parallelDownloads == n,
                    onClick = { vm.updateSettings { it.copy(parallelDownloads = n) } },
                    shape = SegmentedButtonDefaults.itemShape(n - 1, SettingsStore.MAX_PARALLEL),
                ) { Text("$n") }
            }
        }
        Hint(
            "YouTube may block a network that downloads a lot at once (\"Sign in to confirm " +
                "you're not a bot\"). One at a time is the safest. Takes effect from the next queue run.",
        )
        SwitchRow(
            title = "Tags and cover art",
            body = "Write title and artist into files, and add the thumbnail as cover art to audio.",
            checked = s.embedMetadata,
            onChange = { v -> vm.updateSettings { it.copy(embedMetadata = v) } },
        )

        SectionTitle("yt-dlp")
        Text(
            when (val e = engine) {
                EngineState.Starting -> "Starting…"
                is EngineState.Ready -> "Version ${e.ytDlpVersion ?: "unknown"}"
                is EngineState.Updating -> "Version ${e.ytDlpVersion ?: "unknown"} · updating…"
                is EngineState.Failed -> "Couldn't start: ${e.message}"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        SwitchRow(
            title = "Update automatically",
            body = "At most once a day, fetch the latest stable yt-dlp from its GitHub releases. " +
                "YouTube changes often, and an old yt-dlp is the most common reason downloads fail.",
            checked = s.autoUpdateYtDlp,
            onChange = { v -> vm.updateSettings { it.copy(autoUpdateYtDlp = v) } },
        )
        OutlinedButton(
            onClick = vm::updateYtDlp,
            enabled = !vm.updating && engine is EngineState.Ready,
            modifier = Modifier.padding(top = 4.dp),
        ) { Text("Check for update now") }
        vm.updateMessage?.let { Hint(it) }

        SectionTitle("Storage")
        Hint(
            "Files go to Downloads/YTDL Manager on this phone, through Android's MediaStore, so " +
                "the app needs no storage permission. Removing an item from History keeps the file " +
                "unless you choose to delete it.",
        )

        SectionTitle("About")
        Text("YTDL Manager for Android ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
        Hint(
            "Companion to the YTDL Manager desktop app. Built on yt-dlp, FFmpeg, Python and " +
                "QuickJS via the youtubedl-android library. No accounts, logins, cookies, " +
                "analytics or ads. Licensed under the GPL-3.0.",
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier.padding(vertical = 12.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("Legal notice", style = MaterialTheme.typography.titleSmall)
                Text(LegalNotice.TEXT, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun SwitchRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
