package io.github.azeezhamzat.ytdlmanager.storage

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import io.github.azeezhamzat.ytdlmanager.core.FileNaming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

data class SavedFile(val uri: Uri, val displayName: String, val mimeType: String, val sizeBytes: Long)

/**
 * Publishes a finished download to Downloads/YTDL Manager through
 * MediaStore.Downloads (Android 10+), so no storage permission is needed and
 * the file shows up in Files, galleries and music players.
 */
class MediaStoreSaver(private val resolver: ContentResolver) {

    suspend fun save(source: File): SavedFile = withContext(Dispatchers.IO) {
        val name = FileNaming.sanitize(source.name)
        val mime = FileNaming.mimeTypeFor(source.extension)
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
            // Hidden from other apps until the copy is complete.
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
            ?: throw IOException("Couldn't create a file in Downloads")
        try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("Couldn't open $name for writing")
            out.use { output ->
                source.inputStream().use { input ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                    }
                }
            }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            // MediaStore adds " (1)" etc. when the name is taken.
            SavedFile(uri, displayName(uri) ?: name, mime, source.length())
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
    }

    /** False once the user has deleted the file (e.g. in the Files app). */
    fun exists(uri: Uri): Boolean = runCatching {
        resolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { it.moveToFirst() } ?: false
    }.getOrDefault(false)

    fun delete(uri: Uri): Boolean = runCatching { resolver.delete(uri, null, null) > 0 }.getOrDefault(false)

    private fun displayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    companion object {
        const val FOLDER = "YTDL Manager"
    }
}
