package com.personal.audioapp.ui

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.personal.audioapp.R
import com.personal.audioapp.ui.theme.AudioAppTheme
import net.openid.appauth.AuthorizationService

/**
 * Single activity app.
 *
 * It hosts the Compose UI and, because the OAuth redirect
 * (`com.personal.audioapp:/oauth2callback`) is registered on this activity in
 * the manifest, it is also the place where AppAuth hands the authorization
 * response back to us.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    /** Kept alive for the duration of a sign-in so the redirect can be resolved. */
    private var authService: AuthorizationService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The app may have been cold-started directly by the OAuth redirect.
        viewModel.handleAuthRedirect(intent)

        setContent {
            AudioAppTheme {
                AppRoot(
                    viewModel = viewModel,
                    onConnectDrive = { startAuthorization() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // The activity is `singleTask`, so the browser redirect lands here.
        viewModel.handleAuthRedirect(intent)
    }

    override fun onResume() {
        super.onResume()
        // Picks up a recording started from the notification action.
        viewModel.onResume()
    }

    override fun onDestroy() {
        authService?.dispose()
        authService = null
        super.onDestroy()
    }

    // ------------------------------------------------------------------ OAuth

    private fun startAuthorization() {
        val request = viewModel.authorizationRequest() ?: return

        val service = authService ?: viewModel.authorizationService().also { authService = it }

        // Where AppAuth is told to deliver the response: back to this activity.
        val completionIntent = Intent(this, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        val completionPendingIntent = PendingIntent.getActivity(
            this,
            AUTH_REQUEST_CODE,
            completionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        service.performAuthorizationRequest(request, completionPendingIntent)
    }

    companion object {
        private const val AUTH_REQUEST_CODE = 0x4A21
    }
}

@Composable
private fun AppRoot(
    viewModel: AppViewModel,
    onConnectDrive: () -> Unit
) {
    val context = LocalContext.current

    val tasks by viewModel.tasks.collectAsState()
    val recording by viewModel.recording.collectAsState()
    val signedIn by viewModel.signedIn.collectAsState()
    val accountEmail by viewModel.accountEmail.collectAsState()
    val settingsState by viewModel.settingsState.collectAsState()
    val message by viewModel.message.collectAsState()

    var showSettings by rememberSaveable { mutableStateOf(false) }

    // SAF picker: opens the system file chooser filtered to audio files.
    val pickAudio = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importAudio(uri)
    }

    // Strings cannot be read inside the (non-composable) result callbacks,
    // so they are resolved here during composition.
    val microphoneDeniedMessage = stringResource(R.string.msg_permission_required)

    // RECORD_AUDIO (and, on Android 13+, POST_NOTIFICATIONS) before recording.
    val requestPermissions = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.RECORD_AUDIO]
            ?: ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.toggleRecording(context)
        } else {
            viewModel.showMessage(microphoneDeniedMessage)
        }
    }

    val onToggleRecording: () -> Unit = {
        val micGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (micGranted) viewModel.toggleRecording(context)
        else requestPermissions.launch(requiredPermissions())
    }

    if (showSettings) {
        SettingsScreen(
            state = settingsState,
            signedIn = signedIn,
            accountEmail = accountEmail,
            message = message,
            onMessageShown = viewModel::consumeMessage,
            onSave = viewModel::saveSettings,
            onConnectDrive = onConnectDrive,
            onSignOut = viewModel::signOut,
            onBack = { showSettings = false }
        )
    } else {
        HomeScreen(
            tasks = tasks,
            isRecording = recording,
            signedIn = signedIn,
            accountEmail = accountEmail,
            message = message,
            onMessageShown = viewModel::consumeMessage,
            onToggleRecording = onToggleRecording,
            onPickFile = { pickAudio.launch(arrayOf("audio/*")) },
            onOpenSettings = { showSettings = true },
            onConnectDrive = onConnectDrive,
            onRetry = viewModel::retry,
            onDelete = viewModel::delete,
            onClearCompleted = viewModel::clearCompleted
        )
    }
}

/**
 * POST_NOTIFICATIONS only exists from API 33 on, and the foreground service
 * needs it to show the "recording" notification.
 */
private fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 33) {
    arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
} else {
    arrayOf(Manifest.permission.RECORD_AUDIO)
}
