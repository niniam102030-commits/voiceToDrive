package com.personal.audioapp.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.personal.audioapp.R
import com.personal.audioapp.auth.AuthRepository
import com.personal.audioapp.data.AppDatabase
import com.personal.audioapp.data.FileTask
import com.personal.audioapp.data.SettingsRepository
import com.personal.audioapp.data.TaskStatus
import com.personal.audioapp.network.CreateFileRequest
import com.personal.audioapp.network.CreateFolderRequest
import com.personal.audioapp.network.DriveClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException

/**
 * Uploads the compressed file to Google Drive through a **resumable upload
 * session**, so recordings of any length work (the old
 * `uploadType=multipart` endpoint refuses anything above 5 MB).
 *
 * The bearer token is refreshed on demand by [AuthRepository], which keeps the
 * worker alive long after the app was closed.
 */
class UploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val dao = AppDatabase.get(appContext).fileTaskDao()
    private val settings = SettingsRepository(appContext)
    private val auth = AuthRepository(appContext, settings)

    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(WorkScheduler.KEY_TASK_ID, -1L)
        if (taskId <= 0L) {
            Log.e(TAG, "Missing task id, nothing to upload")
            return Result.failure()
        }

        val task = dao.getById(taskId) ?: return Result.failure()

        if (task.status == TaskStatus.COMPLETED && task.remoteId != null) {
            return Result.success()
        }

        val file = File(task.localPath)
        if (!file.exists() || file.length() == 0L) {
            return fail(
                task,
                applicationContext.getString(R.string.error_upload_missing, task.localPath)
            )
        }

        val token = auth.validAccessToken()
        if (token.isNullOrBlank()) {
            // Not a transient error: the user must sign in again.
            dao.updateStatus(
                task.id,
                TaskStatus.PENDING,
                applicationContext.getString(R.string.error_no_token),
                System.currentTimeMillis()
            )
            return Result.failure()
        }

        dao.updateStatus(task.id, TaskStatus.UPLOADING, null, System.currentTimeMillis())

        val outcome = withContext(Dispatchers.IO) {
            runCatching { performUpload(token, file, task.displayName) }
        }

        val remoteId = outcome.getOrNull()
        if (remoteId != null) {
            dao.update(
                task.copy(
                    status = TaskStatus.COMPLETED,
                    remoteId = remoteId,
                    error = null,
                    updatedAt = System.currentTimeMillis()
                )
            )
            if (task.deleteLocalAfterUpload || settings.autoDeleteAfterUpload) {
                runCatching { file.delete() }
            }
            return Result.success()
        }

        val error = outcome.exceptionOrNull()
        Log.e(TAG, "Upload failed for task $taskId", error)
        val message = error?.message
            ?: applicationContext.getString(R.string.error_unknown_upload)

        return if (runAttemptCount < MAX_ATTEMPTS) {
            Result.retry()
        } else {
            dao.updateStatus(task.id, TaskStatus.FAILED, message, System.currentTimeMillis())
            Result.failure()
        }
    }

    /** @return the Drive file id. */
    private suspend fun performUpload(
        accessToken: String,
        file: File,
        displayName: String
    ): String {
        val bearer = DriveClient.bearer(accessToken)
        val folderId = resolveFolderId(bearer)
        val mimeType = MIME_MP3
        // The local file name carries a task-id prefix to stay unique on disk;
        // what lands on Drive gets the friendly name instead.
        val uploadName = displayName.ifBlank { file.name }

        // Step 1: open the session. The metadata lives in the request body and
        // the bytes follow through the URL Google hands back.
        val session = DriveClient.api.startResumableUpload(
            bearer = bearer,
            contentType = mimeType,
            contentLength = file.length(),
            metadata = CreateFileRequest(
                name = uploadName,
                mimeType = mimeType,
                parents = folderId?.let { listOf(it) }
            )
        )

        val sessionUrl = session.headers()["Location"]
        // Both bodies are empty; closing them releases the connection.
        session.body()?.close()
        session.errorBody()?.close()

        if (!session.isSuccessful) {
            // A 404 here normally means the remembered destination folder is
            // gone (deleted or moved in Drive). Forgetting it lets the next run
            // create a fresh "AudioApp" folder instead of failing forever.
            if (session.code() == HTTP_NOT_FOUND && settings.driveFolderId.isNotBlank()) {
                Log.w(TAG, "Destination folder ${settings.driveFolderId} is gone, forgetting it")
                settings.driveFolderId = ""
            }
            throw IOException(
                applicationContext.getString(R.string.error_upload_http, session.code())
            )
        }
        if (sessionUrl.isNullOrBlank()) {
            throw IOException(applicationContext.getString(R.string.error_no_session))
        }

        // Step 2: PUT the bytes. The upload id inside the URL authenticates the
        // request, so no Authorization header is needed here.
        val response = DriveClient.api.uploadToSession(
            sessionUrl = sessionUrl,
            file = file.asRequestBody(mimeType.toMediaType())
        )

        if (!response.isSuccessful) {
            throw IOException(
                applicationContext.getString(R.string.error_upload_http, response.code())
            )
        }

        return response.body()?.id
            ?: throw IOException(applicationContext.getString(R.string.error_drive_no_id))
    }

    private suspend fun resolveFolderId(bearer: String): String? {
        val cached = settings.driveFolderId
        if (cached.isNotBlank()) return cached

        return runCatching {
            val folder = DriveClient.api.createFolder(
                bearer,
                CreateFolderRequest(name = DEFAULT_FOLDER_NAME)
            )
            folder.id?.also { settings.driveFolderId = it }
        }.getOrNull()
    }

    private suspend fun fail(task: FileTask, reason: String): Result {
        dao.updateStatus(task.id, TaskStatus.FAILED, reason, System.currentTimeMillis())
        return Result.failure()
    }

    companion object {
        private const val TAG = "UploadWorker"
        private const val MAX_ATTEMPTS = 3
        private const val HTTP_NOT_FOUND = 404
        private const val DEFAULT_FOLDER_NAME = "AudioApp"
        private const val MIME_MP3 = "audio/mpeg"
    }
}
