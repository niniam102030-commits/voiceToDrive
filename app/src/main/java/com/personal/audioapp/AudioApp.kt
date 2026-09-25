package com.personal.audioapp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

/**
 * Application entry point.
 *
 * Keeps WorkManager on its default (lazy) initialiser so that no custom
 * Configuration.Provider is required, and creates the notification channel
 * used by the recording foreground service.
 */
class AudioApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return

        val recording = NotificationChannel(
            CHANNEL_RECORDING,
            getString(R.string.notification_channel_recording),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_recording_desc)
            setShowBadge(false)
        }

        manager.createNotificationChannel(recording)
    }

    companion object {
        const val CHANNEL_RECORDING = "audio_recording"
    }
}
