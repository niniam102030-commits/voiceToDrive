package com.personal.audioapp.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.audioapp.R
import com.personal.audioapp.auth.AuthRepository
import com.personal.audioapp.data.AppDatabase
import com.personal.audioapp.data.FileTask
import com.personal.audioapp.data.SettingsRepository
import com.personal.audioapp.data.TaskStatus
import com.personal.audioapp.service.AudioRecordService
import com.personal.audioapp.util.FileUtils
import com.personal.audioapp.work.WorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthorizationRequest
import java.io.File

/** Everything the Settings screen needs to render. */
data class SettingsUiState(
    val clientId: String = "",
    val driveFolderId: String = "",
    val autoUpload: Boolean = true,
    val autoDeleteAfterUpload: Boolean = false,
    val autoDeleteSourceAfterCompression: Boolean = false
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val app: Application = application
    private val dao = AppDatabase.get(application).fileTaskDao()
    private val settings = SettingsRepository(application)
    private val auth = AuthRepository(application, settings)

    val tasks: StateFlow<List<FileTask>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording

    private val _signedIn = MutableStateFlow(auth.isSignedIn())
    val signedIn: StateFlow<Boolean> = _signedIn

    private val _accountEmail = MutableStateFlow(auth.accountEmail())
    val accountEmail: StateFlow<String?> = _accountEmail

    private val _settingsState = MutableStateFlow(readSettings())
    val settingsState: StateFlow<SettingsUiState> = _settingsState

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    // -------------------------------------------------------------- lifecycle

    fun onResume() {
        _recording.value = AudioRecordService.isRecording
        _signedIn.value = auth.isSignedIn()
        _accountEmail.value = auth.accountEmail()
    }

    // -------------------------------------------------------------- recording

    fun toggleRecording(context: Context) {
        if (AudioRecordService.isRecording || _recording.value) stopRecording(context)
        else startRecording(context)
    }

    fun startRecording(context: Context) {
        val target = FileUtils.newRecordingFile(context)
        _recording.value = true
        AudioRecordService.start(context, target.absolutePath)

        // MediaRecorder is created and prepared inside the service. When that
        // fails (microphone busy, no free storage…) the service flag goes back
        // to false; re-reading it here keeps the UI from being stuck on
        // "stop recording" and tells the user what happened.
        viewModelScope.launch {
            delay(RECORDING_CONFIRMATION_DELAY_MS)
            val started = AudioRecordService.isRecording
            _recording.value = started
            if (!started) _message.value = app.getString(R.string.msg_record_failed)
        }
    }

    fun stopRecording(context: Context) {
        _recording.value = false
        AudioRecordService.stop(context)
    }

    // ------------------------------------------------------------ import file

    fun importAudio(uri: Uri) {
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val name = FileUtils.displayName(app, uri)
                    val copied = FileUtils.copyToPrivateStorage(app, uri, name)
                    // Keep whatever the picker reported, so the stored metadata
                    // is not a lie for wav/flac/ogg imports.
                    val mimeType = app.contentResolver.getType(uri) ?: DEFAULT_MIME_TYPE
                    val id = dao.insert(
                        FileTask(
                            displayName = copied.name,
                            localPath = copied.absolutePath,
                            mimeType = mimeType,
                            sizeBytes = copied.length(),
                            status = TaskStatus.PENDING,
                            deleteLocalAfterUpload = settings.autoDeleteAfterUpload
                        )
                    )
                    id
                }
            }

            outcome.onSuccess { id ->
                _message.value = app.getString(R.string.msg_file_added)
                if (settings.autoUpload) WorkScheduler.enqueueCompression(app, id)
            }.onFailure { error ->
                Log.e(TAG, "Import failed", error)
                _message.value = app.getString(
                    R.string.msg_import_failed,
                    error.message ?: app.getString(R.string.msg_unknown_error)
                )
            }
        }
    }

    // ---------------------------------------------------------- task actions

    fun retry(task: FileTask) {
        viewModelScope.launch {
            dao.updateStatus(task.id, TaskStatus.PENDING, null, System.currentTimeMillis())
            if (task.localPath.endsWith(".mp3", ignoreCase = true)) {
                WorkScheduler.enqueueUpload(app, task.id)
            } else {
                WorkScheduler.enqueueCompression(app, task.id)
            }
            _message.value = app.getString(R.string.msg_requeued, task.displayName)
        }
    }

    fun delete(task: FileTask) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { File(task.localPath).delete() } }
            dao.delete(task)
        }
    }

    fun clearCompleted() {
        viewModelScope.launch {
            // Drop the uploaded files as well, otherwise the private storage
            // keeps growing with audio nobody can reach from the UI any more.
            val finished = dao.getByStatus(TaskStatus.COMPLETED)
            if (finished.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    finished.forEach { task ->
                        runCatching { File(task.localPath).delete() }
                    }
                }
            }
            dao.deleteAllCompleted()
        }
    }

    // ------------------------------------------------------------------ auth

    /** @return true when the intent really was the OAuth redirect. */
    fun handleAuthRedirect(data: Intent): Boolean {
        val scheme = data.data?.scheme ?: return false
        if (!scheme.equals(AuthRepository.REDIRECT_URI.scheme, ignoreCase = true)) return false

        val response = auth.handleAuthorizationResponse(data)
        if (response == null) {
            val ex = auth.authorizationException(data)
            _message.value = app.getString(
                R.string.msg_auth_failed,
                ex?.errorDescription ?: ex?.error ?: app.getString(R.string.msg_unknown_error)
            )
            return true
        }

        auth.exchangeCode(response) { state ->
            if (state != null) {
                _signedIn.value = true
                _accountEmail.value = auth.accountEmail()
                _message.value = app.getString(R.string.msg_drive_connected)
            } else {
                _message.value = app.getString(R.string.msg_token_failed)
            }
        }
        return true
    }

    fun authorizationRequest(): AuthorizationRequest? {
        val request = auth.createAuthorizationRequest()
        if (request == null) _message.value = app.getString(R.string.msg_need_client_id)
        return request
    }

    fun authorizationService() = auth.authorizationService()

    fun signOut() {
        auth.clear()
        _signedIn.value = false
        _accountEmail.value = null
        _message.value = app.getString(R.string.msg_signed_out)
    }

    // -------------------------------------------------------------- settings

    fun saveSettings(state: SettingsUiState) {
        settings.clientId = state.clientId
        settings.driveFolderId = state.driveFolderId
        settings.autoUpload = state.autoUpload
        settings.autoDeleteAfterUpload = state.autoDeleteAfterUpload
        settings.autoDeleteSourceAfterCompression = state.autoDeleteSourceAfterCompression
        _settingsState.value = readSettings()
        _message.value = app.getString(R.string.msg_settings_saved)
    }

    private fun readSettings(): SettingsUiState = SettingsUiState(
        clientId = settings.clientId,
        driveFolderId = settings.driveFolderId,
        autoUpload = settings.autoUpload,
        autoDeleteAfterUpload = settings.autoDeleteAfterUpload,
        autoDeleteSourceAfterCompression = settings.autoDeleteSourceAfterCompression
    )

    // --------------------------------------------------------------- message

    fun consumeMessage() {
        _message.value = null
    }

    fun showMessage(text: String) {
        _message.value = text
    }

    companion object {
        private const val TAG = "AppViewModel"

        /** How long the service is given to report a successful recording start. */
        private const val RECORDING_CONFIRMATION_DELAY_MS = 700L

        /** Used only when the picker cannot report a mime type for the import. */
        private const val DEFAULT_MIME_TYPE = "audio/mp4"
    }
}
