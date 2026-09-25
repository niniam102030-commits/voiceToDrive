package com.personal.audioapp.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.personal.audioapp.data.SettingsRepository
import com.personal.audioapp.util.JwtUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.GrantTypeValues
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import net.openid.appauth.AuthState

/**
 * OAuth 2.0 (Authorization Code + PKCE) against Google, implemented with AppAuth.
 *
 * The refresh_tokens are requested with `access_type=offline` so uploads keep
 * working without the user being present.
 */
class AuthRepository(
    private val context: Context,
    private val settings: SettingsRepository
) {

    private val serviceConfiguration = AuthorizationServiceConfiguration(
        Uri.parse(AUTHORIZATION_ENDPOINT),
        Uri.parse(TOKEN_ENDPOINT),
        null,
        null
    )

    fun authorizationService(): AuthorizationService = AuthorizationService(context)

    // ------------------------------------------------------------------ state

    fun currentState(): AuthState? {
        val json = settings.authStateJson ?: return null
        return runCatching { AuthState.jsonDeserialize(json) }
            .onFailure { Log.w(TAG, "Cannot deserialize AuthState", it) }
            .getOrNull()
    }

    fun isSignedIn(): Boolean =
        !settings.refreshToken.isNullOrBlank() || !settings.accessToken.isNullOrBlank()

    fun accountEmail(): String? = settings.accountEmail

    /**
     * Forgets the tokens. Any [AuthorizationService] owned by the UI keeps its
     * own lifetime; this repository never holds one across calls.
     */
    fun clear() {
        settings.clearAuth()
    }

    // -------------------------------------------------------------- request

    /**
     * @return null when the user has not filled the Google client id yet.
     */
    fun createAuthorizationRequest(): AuthorizationRequest? {
        val clientId = settings.clientId
        if (clientId.isBlank()) return null

        return AuthorizationRequest.Builder(
            serviceConfiguration,
            clientId,
            ResponseTypeValues.CODE,
            REDIRECT_URI
        )
            .setScopes(SCOPES)
            .setAdditionalParameters(
                mapOf(
                    "access_type" to "offline",
                    // force the consent screen once so Google returns a refresh token
                    "prompt" to "consent"
                )
            )
            .build()
    }

    /**
     * Reads the redirect intent and, on success, exchanges the code for tokens.
     * Must be called from the Activity that received the intent.
     */
    fun handleAuthorizationResponse(data: Intent): AuthorizationResponse? =
        AuthorizationResponse.fromIntent(data)

    fun authorizationException(data: Intent): AuthorizationException? =
        AuthorizationException.fromIntent(data)

    /** Non-blocking token exchange; AppAuth performs the network call for us. */
    fun exchangeCode(
        response: AuthorizationResponse,
        onResult: (AuthState?) -> Unit
    ) {
        val service = authorizationService()
        service.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, ex ->
            try {
                if (tokenResponse != null) {
                    val state = AuthState(response, tokenResponse, ex)
                    persist(
                        state = state,
                        newRefreshToken = tokenResponse.refreshToken,
                        email = JwtUtils.emailFromIdToken(tokenResponse.idToken)
                    )
                    if (ex != null) Log.w(TAG, "Token exchange completed with warning", ex)
                    onResult(state)
                } else {
                    Log.e(TAG, "Token exchange failed", ex)
                    onResult(null)
                }
            } finally {
                service.dispose()
            }
        }
    }

    // -------------------------------------------------------------- refresh

    /**
     * Returns a usable access token, refreshing it when it is about to expire.
     * Returns null when the user is not connected or the refresh token is dead.
     */
    suspend fun validAccessToken(): String? = withContext(Dispatchers.IO) {
        val cached = settings.accessToken
        val notExpired = !cached.isNullOrBlank() &&
            settings.accessTokenExpiry > System.currentTimeMillis() + EXPIRY_MARGIN_MS
        if (notExpired) return@withContext cached

        val refresh = settings.refreshToken ?: currentState()?.refreshToken
        if (refresh.isNullOrBlank()) return@withContext null

        val clientId = settings.clientId
        if (clientId.isBlank()) return@withContext null

        try {
            val request = TokenRequest.Builder(serviceConfiguration, clientId)
                .setGrantType(GrantTypeValues.REFRESH_TOKEN)
                .setRefreshToken(refresh)
                .build()

            val service = authorizationService()
            val tokenResponse = try {
                service.performTokenRequestSync(request)
            } finally {
                service.dispose()
            }

            // Store the fresh access token directly: building an AuthState here
            // would need a non-null AuthorizationResponse, which a pure token
            // refresh does not have.
            settings.accessToken = tokenResponse.accessToken
            settings.accessTokenExpiry = tokenResponse.accessTokenExpirationTime ?: 0L
            if (!tokenResponse.refreshToken.isNullOrBlank()) {
                settings.refreshToken = tokenResponse.refreshToken
            }
            tokenResponse.accessToken
        } catch (t: Throwable) {
            Log.e(TAG, "Refresh token failed - user has to sign in again", t)
            null
        }
    }

    // ------------------------------------------------------------- internals

    private fun persist(state: AuthState, newRefreshToken: String?, email: String? = null) {
        settings.authStateJson = runCatching { state.jsonSerializeString() }.getOrNull()
        if (!state.accessToken.isNullOrBlank()) {
            settings.accessToken = state.accessToken
            settings.accessTokenExpiry = state.accessTokenExpirationTime ?: 0L
        }
        if (!newRefreshToken.isNullOrBlank()) {
            settings.refreshToken = newRefreshToken
        }
        if (!email.isNullOrBlank()) {
            settings.accountEmail = email
        }
    }

    companion object {
        private const val TAG = "AuthRepository"

        private const val AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"

        /**
         * Must match [SettingsRepository] client id URL scheme and the manifest
         * `<data android:scheme="${appAuthRedirectScheme}" android:host="oauth2callback" />`.
         */
        val REDIRECT_URI: Uri = Uri.parse("com.personal.audioapp:/oauth2callback")

        /** Minimal scope: the app only ever touches files it created itself. */
        val SCOPES: List<String> = listOf(
            "openid",
            "email",
            "https://www.googleapis.com/auth/drive.file"
        )

        private const val EXPIRY_MARGIN_MS = 60_000L
    }
}
