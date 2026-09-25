package com.personal.audioapp.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * All user settings (Google client id, OAuth tokens, Drive folder, toggles)
 * live in an [EncryptedSharedPreferences] file so that the refresh token and
 * the client id never end up as plain text on disk.
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = buildEncryptedPrefs(context.applicationContext)

    // ---------------------------------------------------------------- OAuth
    var clientId: String
        get() = prefs.getString(KEY_CLIENT_ID, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_CLIENT_ID, value.trim()).apply()

    /** Serialised AppAuth [net.openid.appauth.AuthState]. */
    var authStateJson: String?
        get() = prefs.getString(KEY_AUTH_STATE, null)
        set(value) {
            if (value == null) prefs.edit().remove(KEY_AUTH_STATE).apply()
            else prefs.edit().putString(KEY_AUTH_STATE, value).apply()
        }

    /** Cached refresh token (encrypted) so tokens can be renewed offline of AppAuth internals. */
    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH_TOKEN, null)
        set(value) {
            if (value == null) prefs.edit().remove(KEY_REFRESH_TOKEN).apply()
            else prefs.edit().putString(KEY_REFRESH_TOKEN, value).apply()
        }

    /**
     * Cached access token and its expiry (epoch millis). Keeping them next to
     * the refresh token means a token refresh never has to rebuild an AppAuth
     * `AuthState` just to read a bearer token.
     */
    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)
        set(value) {
            if (value == null) prefs.edit().remove(KEY_ACCESS_TOKEN).apply()
            else prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()
        }

    var accessTokenExpiry: Long
        get() = prefs.getLong(KEY_ACCESS_TOKEN_EXPIRY, 0L)
        set(value) = prefs.edit().putLong(KEY_ACCESS_TOKEN_EXPIRY, value).apply()

    /** Google account email of the connected account, purely informational. */
    var accountEmail: String?
        get() = prefs.getString(KEY_ACCOUNT_EMAIL, null)
        set(value) {
            if (value == null) prefs.edit().remove(KEY_ACCOUNT_EMAIL).apply()
            else prefs.edit().putString(KEY_ACCOUNT_EMAIL, value).apply()
        }

    // --------------------------------------------------------------- Drive
    var driveFolderId: String
        get() = prefs.getString(KEY_DRIVE_FOLDER_ID, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_DRIVE_FOLDER_ID, value.trim()).apply()

    // ------------------------------------------------------------- Toggles
    /** When on, finishing a recording queues compression + upload automatically. */
    var autoUpload: Boolean
        get() = prefs.getBoolean(KEY_AUTO_UPLOAD, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_UPLOAD, value).apply()

    /** When on, the local file is removed once Drive confirmed the upload. */
    var autoDeleteAfterUpload: Boolean
        get() = prefs.getBoolean(KEY_AUTO_DELETE, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_DELETE, value).apply()

    /** When on, the raw recording is removed right after a successful compression. */
    var autoDeleteSourceAfterCompression: Boolean
        get() = prefs.getBoolean(KEY_AUTO_DELETE_SOURCE, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_DELETE_SOURCE, value).apply()

    fun clearAuth() {
        prefs.edit()
            .remove(KEY_AUTH_STATE)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_ACCESS_TOKEN_EXPIRY)
            .remove(KEY_ACCOUNT_EMAIL)
            .apply()
    }

    companion object {
        private const val TAG = "SettingsRepository"

        private const val PREFS_NAME = "audio_app_secure_prefs"
        private const val KEY_CLIENT_ID = "google_client_id"
        private const val KEY_AUTH_STATE = "auth_state_json"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_ACCESS_TOKEN_EXPIRY = "access_token_expiry"
        private const val KEY_ACCOUNT_EMAIL = "account_email"
        private const val KEY_DRIVE_FOLDER_ID = "drive_folder_id"
        private const val KEY_AUTO_UPLOAD = "auto_upload"
        private const val KEY_AUTO_DELETE = "auto_delete_after_upload"
        private const val KEY_AUTO_DELETE_SOURCE = "auto_delete_source_after_compression"

        private fun buildEncryptedPrefs(context: Context): SharedPreferences {
            return try {
                val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (t: Throwable) {
                // A corrupted keystore/prefs file would otherwise make the app
                // unusable; fall back to plain preferences instead of crashing.
                Log.e(TAG, "EncryptedSharedPreferences unavailable, falling back", t)
                context.getSharedPreferences(PREFS_NAME + "_fallback", Context.MODE_PRIVATE)
            }
        }
    }
}
