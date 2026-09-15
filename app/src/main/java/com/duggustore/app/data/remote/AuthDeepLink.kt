package com.duggustore.app.data.remote

import android.net.Uri

/**
 * The redirect Supabase sends the user back to after they open an auth email.
 * Must also be present on the Redirect URLs allow list in the dashboard.
 */
const val AUTH_REDIRECT_URL = "duggustore://auth-callback"

/**
 * What came back on the deep link. Supabase returns the session in the URL
 * *fragment* (`#access_token=...`), not the query string, so the fragment is
 * parsed as well — reading only the query would always come up empty.
 */
sealed interface AuthDeepLink {
    /** A signup/magic-link confirmation: the user is simply signed in. */
    data class SignedIn(val accessToken: String, val refreshToken: String) : AuthDeepLink

    /** A password reset: the session only exists so a new password can be set. */
    data class Recovery(val accessToken: String, val refreshToken: String) : AuthDeepLink

    data class Failed(val message: String) : AuthDeepLink
}

object AuthDeepLinkParser {

    fun parse(uri: Uri?): AuthDeepLink? {
        uri ?: return null
        if (!uri.toString().startsWith(AUTH_REDIRECT_URL)) return null

        val params = buildMap {
            putAll(splitPairs(uri.fragment))
            // Query values win: an error is reported there even when a fragment exists.
            putAll(splitPairs(uri.query))
        }

        params["error_description"]?.let { return AuthDeepLink.Failed(it.replace('+', ' ')) }
        params["error"]?.let { return AuthDeepLink.Failed(it.replace('+', ' ')) }

        val accessToken = params["access_token"]
        val refreshToken = params["refresh_token"].orEmpty()

        if (accessToken.isNullOrBlank()) {
            return AuthDeepLink.Failed("That link is missing its sign-in token. Request a new email.")
        }

        // `type` distinguishes the two links that land here. Treating a signup
        // confirmation as a recovery would put the user on "set a new password"
        // when all they did was confirm their address.
        return if (params["type"].equals("recovery", ignoreCase = true)) {
            AuthDeepLink.Recovery(accessToken, refreshToken)
        } else {
            AuthDeepLink.SignedIn(accessToken, refreshToken)
        }
    }

    private fun splitPairs(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split('&').mapNotNull { pair ->
            val i = pair.indexOf('=')
            if (i <= 0) return@mapNotNull null
            val key = pair.substring(0, i)
            val value = Uri.decode(pair.substring(i + 1))
            if (value.isBlank()) null else key to value
        }.toMap()
    }
}
