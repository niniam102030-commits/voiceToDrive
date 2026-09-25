package com.personal.audioapp.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

/**
 * Single place where the compression -> upload chain is enqueued.
 *
 * Each task id gets its own unique work name, so pressing "retry" replaces the
 * previous attempt instead of stacking duplicates.
 */
object WorkScheduler {

    const val KEY_TASK_ID = "task_id"
    const val TAG_AUDIO_PIPELINE = "audio_pipeline"

    fun enqueueCompression(context: Context, taskId: Long) {
        val request = OneTimeWorkRequestBuilder<CompressionWorker>()
            .setInputData(workDataOf(KEY_TASK_ID to taskId))
            .addTag(TAG_AUDIO_PIPELINE)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "compress-$taskId",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun enqueueUpload(context: Context, taskId: Long) {
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(workDataOf(KEY_TASK_ID to taskId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag(TAG_AUDIO_PIPELINE)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "upload-$taskId",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /** Compress and, when it succeeds, upload (chained without polling). */
    fun enqueueFullPipeline(context: Context, taskId: Long) {
        enqueueCompression(context, taskId)
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG_AUDIO_PIPELINE)
    }
}
