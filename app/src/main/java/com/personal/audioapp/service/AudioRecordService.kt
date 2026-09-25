package com.personal.audioapp.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.personal.audioapp.data.AppDatabase
import com.personal.audioapp.data.FileTask
import com.personal.audioapp.data.SettingsRepository
import com.personal.audioapp.data.TaskStatus
import com.personal.audioapp.util.Notifications
import com.personal.audioapp.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Foreground service that owns the [MediaRecorder] session.
 *
 * Recording must survive the Activity being backgrounded, so the recorder lives
 * here and the ongoing notification carries a "Stop" action. When recording
 * ends, the finished file is inserted into Room as PENDING and (optionally)
 * handed to [WorkScheduler] for compression + upload.
 */
class AudioRecordService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val path = intent.getStringExtra(EXTRA_OUTPUT_PATH)
                if (path.isNullOrBlank()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                promoteToForeground()
                val started = startRecorder(path)
                if (!started) {
                    stopForegroundCompat()
                    stopSelf()
                }
            }

            ACTION_STOP -> {
                stopRecorder()
                stopForegroundCompat()
                stopSelf()
            }

            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopRecorder()
        super.onDestroy()
    }

    // ------------------------------------------------------------- recording

    private fun startRecorder(path: String): Boolean {
        if (isRecording) return true

        val file = File(path)
        file.parentFile?.mkdirs()

        // Kept outside the try block so that a recorder which was created but
        // never reached a usable state can still be released in the catch.
        var created: MediaRecorder? = null

        return try {
            val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            created = newRecorder

            @Suppress("DEPRECATION")
            newRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44_100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            recorder = newRecorder
            outputFile = file
            isRecording = true
            Log.i(TAG, "Recording started -> ${file.absolutePath}")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Cannot start MediaRecorder", t)
            // `recorder` is still null here, so the half-initialised object has
            // to be released through `created`: otherwise the microphone stays
            // locked and every later recording fails too.
            runCatching { created?.release() }
            created = null
            recorder = null
            isRecording = false
            file.delete()
            false
        }
    }

    private fun stopRecorder() {
        val active = recorder ?: return
        recorder = null
        val file = outputFile
        outputFile = null

        try {
            active.stop()
        } catch (t: Throwable) {
            // Thrown when the recording was shorter than ~1s: keep the file only
            // if something usable was written.
            Log.w(TAG, "MediaRecorder.stop() failed", t)
        } finally {
            runCatching { active.release() }
            isRecording = false
        }

        if (file == null) return

        if (!file.exists() || file.length() == 0L) {
            file.delete()
            return
        }

        val context = applicationContext
        scope.launch {
            try {
                val settings = SettingsRepository(context)
                val dao = AppDatabase.get(context).fileTaskDao()
                val id = dao.insert(
                    FileTask(
                        displayName = file.name,
                        localPath = file.absolutePath,
                        mimeType = MIME_M4A,
                        sizeBytes = file.length(),
                        status = TaskStatus.PENDING,
                        deleteLocalAfterUpload = settings.autoDeleteAfterUpload
                    )
                )
                if (settings.autoUpload) {
                    WorkScheduler.enqueueCompression(context, id)
                }
                Log.i(TAG, "Recording finished, task $id queued (autoUpload=${settings.autoUpload})")
            } catch (t: Throwable) {
                Log.e(TAG, "Cannot persist the finished recording", t)
            }
        }
    }

    // ----------------------------------------------------------- foreground

    private fun promoteToForeground() {
        val notification = Notifications.recording(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                Notifications.RECORDING_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(Notifications.RECORDING_NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        NotificationManagerCompat.from(this).cancel(Notifications.RECORDING_NOTIFICATION_ID)
    }

    companion object {
        private const val TAG = "AudioRecordService"

        const val ACTION_START = "com.personal.audioapp.action.START_RECORDING"
        const val ACTION_STOP = "com.personal.audioapp.action.STOP_RECORDING"
        const val EXTRA_OUTPUT_PATH = "extra_output_path"

        private const val MIME_M4A = "audio/mp4"

        @Volatile
        var isRecording: Boolean = false
            private set

        fun start(context: Context, outputPath: String) {
            val intent = Intent(context, AudioRecordService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_OUTPUT_PATH, outputPath)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AudioRecordService::class.java).apply {
                action = ACTION_STOP
            }
            // `startService` instead of `startForegroundService` on purpose: the
            // STOP command never calls startForeground(), and the foreground
            // service contract would then crash the app on Android 8+. While a
            // recording is running the app owns a foreground service, so the
            // command is accepted even when it comes from the notification.
            runCatching { context.startService(intent) }
                .onFailure { context.stopService(Intent(context, AudioRecordService::class.java)) }
        }

        fun stopPendingIntent(context: Context, requestCode: Int): PendingIntent {
            val intent = Intent(context, AudioRecordService::class.java).apply {
                action = ACTION_STOP
            }
            return PendingIntent.getService(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
