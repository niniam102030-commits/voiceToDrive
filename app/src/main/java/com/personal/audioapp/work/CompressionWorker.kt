package com.personal.audioapp.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.personal.audioapp.R
import com.personal.audioapp.data.AppDatabase
import com.personal.audioapp.data.FileTask
import com.personal.audioapp.data.SettingsRepository
import com.personal.audioapp.data.TaskStatus
import com.personal.audioapp.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Converts the raw recording (or any imported audio file) into a small
 * 64 kbps MP3 and hands the task over to [UploadWorker].
 *
 * FFmpeg runs through FFmpegKit; the maintained fork
 * `dev.ffmpegkit-maintained:ffmpeg-kit-audio:8.1.7` keeps the original
 * `com.arthenica.ffmpegkit` package namespace.
 */
class CompressionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val dao = AppDatabase.get(appContext).fileTaskDao()
    private val settings = SettingsRepository(appContext)

    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(WorkScheduler.KEY_TASK_ID, -1L)
        if (taskId <= 0L) {
            Log.e(TAG, "Missing task id, nothing to compress")
            return Result.failure()
        }

        val task = dao.getById(taskId) ?: run {
            Log.e(TAG, "Task $taskId no longer exists")
            return Result.failure()
        }

        val source = File(task.localPath)
        if (!source.exists() || source.length() == 0L) {
            return fail(
                task,
                applicationContext.getString(R.string.error_source_missing, task.localPath)
            )
        }

        dao.updateStatus(task.id, TaskStatus.COMPRESSING, null, System.currentTimeMillis())

        val outputName = FileUtils.withoutExtension(task.displayName) + ".mp3"
        // The file itself is prefixed with the task id: two tasks whose display
        // name sanitises to the same string would otherwise share one path, and
        // a re-compression would overwrite an artefact that is still queued for
        // upload. The prefix is file-only; the task keeps the friendly name.
        val outputFile = File(
            FileUtils.compressedDir(applicationContext),
            "${task.id}_$outputName"
        )

        val command = buildCommand(source.absolutePath, outputFile.absolutePath)
        Log.i(TAG, "FFmpeg command: $command")

        val success = withContext(Dispatchers.IO) {
            try {
                val session = FFmpegKit.execute(command)
                val returnCode = session.returnCode
                if (!ReturnCode.isSuccess(returnCode)) {
                    Log.e(TAG, "FFmpeg failed: $returnCode")
                    Log.e(TAG, "FFmpeg log:\n${session.output}")
                    Log.e(TAG, "FFmpeg stack:\n${session.failStackTrace}")
                }
                ReturnCode.isSuccess(returnCode) && outputFile.exists() && outputFile.length() > 0L
            } catch (t: Throwable) {
                Log.e(TAG, "FFmpeg threw", t)
                false
            }
        }

        if (!success) {
            outputFile.delete()
            return fail(task, applicationContext.getString(R.string.error_compression_failed))
        }

        // Point the task at the compressed artefact.
        dao.update(
            task.copy(
                localPath = outputFile.absolutePath,
                displayName = outputName,
                mimeType = MIME_MP3,
                sizeBytes = outputFile.length(),
                status = TaskStatus.UPLOADING,
                error = null,
                updatedAt = System.currentTimeMillis()
            )
        )

        if (settings.autoDeleteSourceAfterCompression && source.absolutePath != outputFile.absolutePath) {
            runCatching { source.delete() }
        }

        // Hand over to the uploader.
        WorkScheduler.enqueueUpload(applicationContext, task.id)
        return Result.success()
    }

    private suspend fun fail(task: FileTask, reason: String): Result {
        dao.updateStatus(task.id, TaskStatus.FAILED, reason, System.currentTimeMillis())
        return Result.failure()
    }

    private fun buildCommand(input: String, output: String): String {
        val quotedInput = FileUtils.ffmpegEscape(input)
        val quotedOutput = FileUtils.ffmpegEscape(output)
        return listOf(
            "-y",
            "-hide_banner",
            "-i \"$quotedInput\"",
            "-vn",
            "-map_metadata", "-1",
            "-ac", "2",
            "-ar", "44100",
            "-c:a", "libmp3lame",
            "-b:a", "${TARGET_BITRATE_KBPS}k",
            "-f", "mp3",
            "\"$quotedOutput\""
        ).joinToString(" ")
    }

    companion object {
        private const val TAG = "CompressionWorker"
        const val TARGET_BITRATE_KBPS = 64
        const val MIME_MP3 = "audio/mpeg"
    }
}
