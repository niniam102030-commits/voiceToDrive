package com.personal.audioapp.util

import android.util.Base64

/**
 * Tiny id_token reader: the JWT is already validated by Google's token
 * endpoint, we only need the `email` claim for display purposes.
 */
object JwtUtils {

    private val EMAIL_REGEX = Regex("\"email\"\\s*:\\s*\"([^\"]+)\"")

    fun emailFromIdToken(idToken: String?): String? {
        val payload = idToken?.split('.')?.getOrNull(1) ?: return null
        return runCatching {
            val json = String(
                Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            )
            EMAIL_REGEX.find(json)?.groupValues?.getOrNull(1)
        }.getOrNull()
    }
}
