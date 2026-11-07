package io.github.azeezhamzat.ytdlmanager.data

import android.content.Context
import io.github.azeezhamzat.ytdlmanager.core.AudioFormat
import io.github.azeezhamzat.ytdlmanager.core.MediaKind
import io.github.azeezhamzat.ytdlmanager.core.VideoQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    val defaultKind: MediaKind = MediaKind.VIDEO,
    val videoQuality: VideoQuality = VideoQuality.P1080,
    val audioFormat: AudioFormat = AudioFormat.M4A,
    val wifiOnly: Boolean = false,
    /**
     * Downloads at once. Capped at 2: YouTube rate-limits networks that pull a
     * lot at the same time ("confirm you're not a bot").
     */
    val parallelDownloads: Int = 1,
    val embedMetadata: Boolean = true,
    val autoUpdateYtDlp: Boolean = true,
    val lastYtDlpCheck: Long = 0,
    val askedForNotifications: Boolean = false,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()
    val current: Settings get() = _settings.value

    @Synchronized
    fun update(transform: (Settings) -> Settings) {
        val next = transform(_settings.value)
        prefs.edit()
            .putString("defaultKind", next.defaultKind.name)
            .putString("videoQuality", next.videoQuality.name)
            .putString("audioFormat", next.audioFormat.name)
            .putBoolean("wifiOnly", next.wifiOnly)
            .putInt("parallelDownloads", next.parallelDownloads.coerceIn(1, MAX_PARALLEL))
            .putBoolean("embedMetadata", next.embedMetadata)
            .putBoolean("autoUpdateYtDlp", next.autoUpdateYtDlp)
            .putLong("lastYtDlpCheck", next.lastYtDlpCheck)
            .putBoolean("askedForNotifications", next.askedForNotifications)
            .apply()
        _settings.value = next
    }

    private fun read(): Settings {
        val d = Settings()
        return Settings(
            defaultKind = enumOr(prefs.getString("defaultKind", null), d.defaultKind),
            videoQuality = enumOr(prefs.getString("videoQuality", null), d.videoQuality),
            audioFormat = enumOr(prefs.getString("audioFormat", null), d.audioFormat),
            wifiOnly = prefs.getBoolean("wifiOnly", d.wifiOnly),
            parallelDownloads = prefs.getInt("parallelDownloads", d.parallelDownloads).coerceIn(1, MAX_PARALLEL),
            embedMetadata = prefs.getBoolean("embedMetadata", d.embedMetadata),
            autoUpdateYtDlp = prefs.getBoolean("autoUpdateYtDlp", d.autoUpdateYtDlp),
            lastYtDlpCheck = prefs.getLong("lastYtDlpCheck", d.lastYtDlpCheck),
            askedForNotifications = prefs.getBoolean("askedForNotifications", d.askedForNotifications),
        )
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

    companion object {
        const val MAX_PARALLEL = 2
    }
}
