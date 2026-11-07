@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.azeezhamzat.ytdlmanager.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.azeezhamzat.ytdlmanager.ui.screens.DownloadScreen
import io.github.azeezhamzat.ytdlmanager.ui.screens.HistoryScreen
import io.github.azeezhamzat.ytdlmanager.ui.screens.SettingsScreen

@Composable
fun AppUi(vm: MainViewModel) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val settings by vm.settings.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    var confirmClearHistory by rememberSaveable { mutableStateOf(false) }

    // Asked once, on the first download: the progress notification needs it
    // on Android 13+. Downloads work either way.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val onDownload: () -> Unit = {
        if (vm.download() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !settings.askedForNotifications) {
            vm.markAskedForNotifications()
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (vm.tab) {
                            Tab.DOWNLOAD -> "YTDL Manager"
                            Tab.HISTORY -> "History"
                            Tab.SETTINGS -> "Settings"
                        },
                    )
                },
                actions = {
                    if (vm.tab == Tab.HISTORY && history.isNotEmpty()) {
                        TextButton(onClick = { confirmClearHistory = true }) { Text("Clear") }
                    }
                },
            )
        },
        bottomBar = {
            Column {
                LegalNoticeBar()
                NavigationBar {
                    NavigationBarItem(
                        selected = vm.tab == Tab.DOWNLOAD,
                        onClick = { vm.tab = Tab.DOWNLOAD },
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        label = { Text("Download") },
                    )
                    NavigationBarItem(
                        selected = vm.tab == Tab.HISTORY,
                        onClick = { vm.tab = Tab.HISTORY },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                        label = { Text("History") },
                    )
                    NavigationBarItem(
                        selected = vm.tab == Tab.SETTINGS,
                        onClick = { vm.tab = Tab.SETTINGS },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("Settings") },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (vm.tab) {
            Tab.DOWNLOAD -> DownloadScreen(vm, onDownload, modifier)
            Tab.HISTORY -> HistoryScreen(vm, modifier)
            Tab.SETTINGS -> SettingsScreen(vm, modifier)
        }
    }

    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            title = { Text("Clear history?") },
            text = { Text("This only clears the list. The files stay in Downloads/YTDL Manager.") },
            confirmButton = {
                TextButton(onClick = { vm.clearHistory(); confirmClearHistory = false }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClearHistory = false }) { Text("Cancel") } },
        )
    }
}
