package com.personal.audioapp.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

object FileUtils {

    private val AUDIO_EXTENSIONS = setOf(
        "mp3", "m4a", "aac", "wav", "ogg", "oga", "opus", "flac", "amr", "3gp", "mp4", "wma"
    )

    /** Directory that holds the user's raw recordings. */
    fun recordingsDir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "recordings").apply { mkdirs() }

    /** Directory that holds the 64kbps MP3 output. */
    fun compressedDir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "compressed").apply { mkdirs() }

    /** Directory that holds files imported through the Storage Access Framework. */
    fun incomingDir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "incoming").apply { mkdirs() }

    fun newRecordingFile(context: Context): File {
        val stamp = System.currentTimeMillis()
        return File(recordingsDir(context), "rec_$stamp.m4a")
    }

    /** Best-effort query of the display name behind a SAF/content Uri. */
    fun displayName(context: Context, uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) {
                        val name = cursor.getString(index)
                        if (!name.isNullOrBlank()) return name
                    }
                }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "audio_${System.currentTimeMillis()}"
    }

    /** Copies a content Uri into the app's private storage and returns the new file. */
    fun copyToPrivateStorage(context: Context, uri: Uri, displayName: String): File {
        val safeName = sanitize(displayName)
        val stamp = System.currentTimeMillis()
        val destination = File(incomingDir(context), "${stamp}_$safeName")

        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open input stream for $uri" }
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        return destination
    }

    fun isAudioFile(name: String): Boolean {
        return name.substringAfterLast('.', "").lowercase(Locale.US) in AUDIO_EXTENSIONS
    }

    /** Replaces every character that could break an FFmpeg argument or a URL. */
    fun sanitize(name: String): String {
        val base = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "")
        val cleaned = base.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60).ifBlank { "audio" }
        return if (extension.isBlank()) cleaned else "$cleaned.$extension"
    }

    /** `rec_1720000000000.m4a` -> `rec_1720000000000` (no extension, FFmpeg-safe). */
    fun withoutExtension(name: String): String = sanitize(name).substringBeforeLast('.')

    /** Escapes a path so it can be embedded inside a double-quoted FFmpeg argument. */
    fun ffmpegEscape(path: String): String = path.replace("\\", "/").replace("\"", "\\\"")

    fun readableSize(bytes: Long): String = when {
        bytes <= 0L -> "0 KB"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
        else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
    }

    fun readableDuration(millis: Long): String {
        if (millis <= 0L) return "00:00"
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
