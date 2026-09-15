package com.duggustore.app.data.repository

import com.duggustore.app.data.model.UserProfile
import com.duggustore.app.data.remote.SessionManager
import com.duggustore.app.data.remote.SessionRefresher
import com.duggustore.app.data.remote.SupabaseException
import com.duggustore.app.data.remote.SupabaseService
import com.duggustore.app.platform.PushNotifications
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

data class SignUpResult(
    val profile: UserProfile?,
    val requiresVerification: Boolean = false,
    val email: String = ""
)

class AuthRepository {

    // Safe casts throughout: the shape of an auth response varies by endpoint and by
    // whether email confirmation is on, and `.jsonObject` / `.jsonPrimitive` throw on a
    // mismatch, which would turn a perfectly good login into a failure.
    private fun obj(element: JsonElement?): JsonObject? = element as? JsonObject

    private fun str(element: JsonElement?): String? =
        (element as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }

    private fun extractToken(resp: JsonObject): String? =
        str(resp["access_token"]) ?: str(obj(resp["session"])?.get("access_token"))

    private fun extractRefreshToken(resp: JsonObject): String =
        str(resp["refresh_token"]) ?: str(obj(resp["session"])?.get("refresh_token")) ?: ""

    private fun extractUserId(resp: JsonObject): String? =
        str(resp["id"]) ?: str(obj(resp["user"])?.get("id"))

    /** The signup metadata Supabase echoes back, used as a fallback when the profile row is unreadable. */
    private fun extractUserMetadata(resp: JsonObject): JsonObject? {
        val user = obj(resp["user"]) ?: obj(obj(resp["session"])?.get("user")) ?: resp
        return obj(user["user_metadata"])
    }

    private fun metadataString(metadata: JsonObject?, key: String, default: String): String =
        str(metadata?.get(key)) ?: default

    private fun normalizeEmail(email: String): String = email.trim().lowercase()

    /**
     * Reads the caller's own profile row. The token must be passed through: with only the
     * anon key the "Users can view own profile" policy never matches and the query comes
     * back empty, which used to silently downgrade every seller/admin to "customer".
     */
    private suspend fun fetchProfile(userId: String, token: String): UserProfile? {
        val profiles = SupabaseService.select("profiles", token, mapOf("id" to userId))
        return profiles.firstOrNull()?.let {
            json.decodeFromString(UserProfile.serializer(), it.toString())
        }
    }

    /**
     * Returns the profile row for a freshly authenticated user. The `on_auth_user_created`
     * trigger normally creates it; if that trigger is missing we create it here, and if the
     * row is unreadable we fall back to the signup metadata so the role is still correct.
     */
    private suspend fun resolveProfile(
        userId: String,
        token: String,
        fallback: UserProfile
    ): UserProfile {
        val existing = try {
            fetchProfile(userId, token)
        } catch (e: Exception) {
            return fallback
        }
        if (existing != null) return existing

        return try {
            val body = buildJsonObject {
                put("id", userId)
                put("full_name", fallback.fullName)
                put("phone", fallback.phone)
                put("role", fallback.role)
            }.toString()
            SupabaseService.insert("profiles", body, token)
            fallback
        } catch (e: Exception) {
            fallback
        }
    }

    suspend fun signUp(
        email: String,
        password: String,
        fullName: String,
        phone: String,
        role: String,
        referralCode: String = ""
    ): Result<SignUpResult> {
        val cleanEmail = normalizeEmail(email)
        return try {
            val resp = SupabaseService.signUp(cleanEmail, password, buildJsonObject {
                put("full_name", fullName)
                put("phone", phone)
                put("role", role)
                // Carried in the signup metadata rather than applied here so it
                // survives the gap when email confirmation is required — it's
                // read back and applied in verifyEmailCode once a session exists.
                put("referral_code", referralCode)
            })

            val token = extractToken(resp)

            // No session in the response means email confirmation is enabled on the project:
            // the account exists but cannot be used until the link is clicked.
            if (token == null) {
                return Result.success(
                    SignUpResult(profile = null, requiresVerification = true, email = cleanEmail)
                )
            }

            val userId = extractUserId(resp)
                ?: return Result.failure(Exception("Signup succeeded but no user ID was returned."))

            SessionManager.saveSession(token, extractRefreshToken(resp), userId, cleanEmail)
            applyReferralIfPresent(userId, token, referralCode)

            val profile = resolveProfile(
                userId,
                token,
                UserProfile(id = userId, fullName = fullName, phone = phone, role = role)
            )
            Result.success(SignUpResult(profile = profile))
        } catch (e: Exception) {
            Result.failure(Exception(signUpErrorMessage(e)))
        }
    }

    /**
     * A bad, expired, or already-used code should never block account
     * creation, so this swallows its own failures rather than surfacing
     * them — apply_referral() is itself a no-op for an unknown code or a
     * user who already redeemed one.
     */
    private suspend fun applyReferralIfPresent(userId: String, token: String, referralCode: String) {
        val code = referralCode.trim()
        if (code.isBlank()) return
        try {
            val body = buildJsonObject {
                put("p_new_user_id", userId)
                put("p_referral_code", code)
            }.toString()
            SupabaseService.rpc("apply_referral", body, token)
        } catch (_: Exception) {
        }
    }

    private fun signUpErrorMessage(e: Exception): String {
        val code = (e as? SupabaseException)?.errorCode ?: ""
        val msg = e.message ?: ""
        return when {
            code == "not_configured" || code == "network_error" -> msg
            code == "user_already_exists" || code == "email_exists" ||
                msg.contains("already registered", ignoreCase = true) ->
                "This email is already registered. Try signing in."
            code == "weak_password" || msg.contains("Password should be", ignoreCase = true) ->
                "Password must be at least 6 characters."
            code == "email_address_invalid" ||
                (msg.contains("invalid", ignoreCase = true) && msg.contains("email", ignoreCase = true)) ->
                "Please enter a valid email address."
            code == "signup_disabled" -> "Sign ups are currently disabled for this app."
            code == "over_email_send_rate_limit" || code == "429" ->
                "Too many attempts. Please wait a moment and try again."
            // An unmapped code/message could be a raw Postgrest or validation
            // string not meant for a user to read — a generic message beats
            // leaking it verbatim.
            else -> "Signup failed. Please try again."
        }
    }

    suspend fun signIn(email: String, password: String): Result<UserProfile> {
        val cleanEmail = normalizeEmail(email)
        return try {
            val resp = SupabaseService.signIn(cleanEmail, password)
            val token = extractToken(resp)
                ?: return Result.failure(Exception("Login failed: no access token received."))

            val userId = extractUserId(resp)
                ?: return Result.failure(Exception("Login failed: no user ID received."))

            SessionManager.saveSession(token, extractRefreshToken(resp), userId, cleanEmail)

            val metadata = extractUserMetadata(resp)
            val fallback = UserProfile(
                id = userId,
                fullName = metadataString(metadata, "full_name", ""),
                phone = metadataString(metadata, "phone", ""),
                role = metadataString(metadata, "role", "customer")
            )

            Result.success(resolveProfile(userId, token, fallback))
        } catch (e: Exception) {
            Result.failure(Exception(signInErrorMessage(e)))
        }
    }

    private fun signInErrorMessage(e: Exception): String {
        val code = (e as? SupabaseException)?.errorCode ?: ""
        val msg = e.message ?: ""
        return when {
            code == "not_configured" || code == "network_error" -> msg
            code == "email_not_confirmed" || msg.contains("not confirmed", ignoreCase = true) ->
                "Please verify your email first. Check your inbox for the verification link."
            code == "invalid_credentials" || code == "invalid_grant" ||
                msg.contains("Invalid login", ignoreCase = true) ->
                "Invalid email or password."
            code == "over_request_rate_limit" || code == "429" ->
                "Too many attempts. Please wait a moment and try again."
            else -> "Login failed. Please try again."
        }
    }

    /**
     * Verifies the 6-digit code from the signup mail and signs the user in.
     * Supabase returns a full session here, so there is no second login step.
     */
    suspend fun verifyEmailCode(email: String, code: String): Result<UserProfile> {
        val cleanEmail = normalizeEmail(email)
        val cleanCode = code.trim()
        return try {
            val resp = SupabaseService.verifyOtp(cleanEmail, cleanCode)
            val token = extractToken(resp)
                ?: return Result.failure(Exception("Verification failed: no session was returned."))
            val userId = extractUserId(resp)
                ?: return Result.failure(Exception("Verification failed: no user ID was returned."))

            SessionManager.saveSession(token, extractRefreshToken(resp), userId, cleanEmail)

            val metadata = extractUserMetadata(resp)
            applyReferralIfPresent(userId, token, metadataString(metadata, "referral_code", ""))
            val fallback = UserProfile(
                id = userId,
                fullName = metadataString(metadata, "full_name", ""),
                phone = metadataString(metadata, "phone", ""),
                role = metadataString(metadata, "role", "customer")
            )
            Result.success(resolveProfile(userId, token, fallback))
        } catch (e: Exception) {
            Result.failure(Exception(verifyErrorMessage(e)))
        }
    }

    private fun verifyErrorMessage(e: Exception): String {
        val code = (e as? SupabaseException)?.errorCode ?: ""
        val msg = e.message ?: ""
        return when {
            code == "not_configured" || code == "network_error" -> msg
            code == "otp_expired" || msg.contains("expired", ignoreCase = true) ->
                "That code has expired. Tap resend to get a new one."
            code == "otp_disabled" ->
                "Email codes are not enabled for this project yet."
            msg.contains("Token has expired or is invalid", ignoreCase = true) ||
                msg.contains("invalid", ignoreCase = true) ->
                "That code is not correct. Please check and try again."
            code == "over_email_send_rate_limit" || code == "429" ->
                "Too many attempts. Please wait a moment and try again."
            else -> "Verification failed. Please try again."
        }
    }

    suspend fun resendVerificationEmail(email: String): Result<Unit> {
        return try {
            SupabaseService.resendVerification(normalizeEmail(email))
            Result.success(Unit)
        } catch (e: Exception) {
            val code = (e as? SupabaseException)?.errorCode ?: ""
            val msg = e.message ?: ""
            when {
                msg.contains("already confirmed", ignoreCase = true) ->
                    Result.failure(Exception("This email is already verified. Try signing in."))
                code == "over_email_send_rate_limit" || code == "429" ->
                    Result.failure(Exception("Too many requests. Please wait a moment and try again."))
                else -> Result.failure(Exception("Failed to resend verification email."))
            }
        }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> {
        return try {
            SupabaseService.sendPasswordReset(normalizeEmail(email))
            Result.success(Unit)
        } catch (e: Exception) {
            val code = (e as? SupabaseException)?.errorCode ?: ""
            val msg = e.message ?: ""
            when {
                code == "not_configured" || code == "network_error" -> Result.failure(Exception(msg))
                code == "over_email_send_rate_limit" || code == "429" ->
                    Result.failure(Exception("Too many requests. Please wait a moment and try again."))
                else -> Result.failure(Exception("Could not send the reset email."))
            }
        }
    }

    /**
     * Accepts the session handed back by a password-reset deep link. The user is not
     * treated as signed in yet — the session only exists so the new password can be
     * submitted; the caller sends them to the reset screen.
     */
    fun beginPasswordRecovery(accessToken: String, refreshToken: String) {
        SessionManager.saveSession(
            accessToken,
            refreshToken,
            SessionManager.getUserId().orEmpty(),
            SessionManager.getEmail().orEmpty()
        )
    }

    /**
     * Completes sign-in from a confirmation link. The redirect already carries a full
     * session, so the profile is fetched with it and the user goes straight in.
     */
    suspend fun completeSignInFromLink(accessToken: String, refreshToken: String): Result<UserProfile?> {
        return try {
            val user = SupabaseService.getUser(accessToken)
            val userId = extractUserId(user)
                ?: return Result.failure(Exception("That link did not identify an account."))
            val email = str(user["email"]).orEmpty()

            SessionManager.saveSession(accessToken, refreshToken, userId, email)

            val metadata = extractUserMetadata(user)
            val fallback = UserProfile(
                id = userId,
                fullName = metadataString(metadata, "full_name", ""),
                phone = metadataString(metadata, "phone", ""),
                role = metadataString(metadata, "role", "customer")
            )
            Result.success(resolveProfile(userId, accessToken, fallback))
        } catch (e: Exception) {
            val code = (e as? SupabaseException)?.errorCode ?: ""
            val msg = e.message ?: ""
            Result.failure(
                Exception(
                    when {
                        code == "not_configured" || code == "network_error" -> msg
                        msg.contains("expired", ignoreCase = true) ->
                            "That link has expired. Sign in, or request a new email."
                        else -> "Could not finish signing in from that link."
                    }
                )
            )
        }
    }

    /** Sets a new password using the recovery session, then loads the profile. */
    suspend fun updatePassword(newPassword: String): Result<UserProfile?> {
        return try {
            val token = SessionManager.getAccessToken()
                ?: return Result.failure(Exception("That reset link has expired. Request a new email."))

            val resp = SupabaseService.updatePassword(token, newPassword)
            val userId = extractUserId(resp) ?: SessionManager.getUserId()
            val email = str(resp["email"]) ?: SessionManager.getEmail().orEmpty()

            if (userId.isNullOrBlank()) return Result.success(null)
            SessionManager.saveSession(token, SessionManager.getRefreshToken().orEmpty(), userId, email)

            val metadata = extractUserMetadata(resp)
            val fallback = UserProfile(
                id = userId,
                fullName = metadataString(metadata, "full_name", ""),
                phone = metadataString(metadata, "phone", ""),
                role = metadataString(metadata, "role", "customer")
            )
            Result.success(resolveProfile(userId, token, fallback))
        } catch (e: Exception) {
            val code = (e as? SupabaseException)?.errorCode ?: ""
            val status = (e as? SupabaseException)?.statusCode ?: 0
            val msg = e.message ?: ""
            val friendly = when {
                code == "not_configured" || code == "network_error" -> msg
                status == 401 || code == "invalid_token" || msg.contains("expired", ignoreCase = true) ->
                    "That reset link has expired. Request a new email."
                code == "weak_password" || msg.contains("Password should be", ignoreCase = true) ->
                    "Password must be at least 6 characters."
                code == "same_password" ->
                    "That is already your current password. Choose a different one."
                else -> "Could not update the password."
            }
            Result.failure(Exception(friendly))
        }
    }

    suspend fun signOut(): Result<Unit> {
        return try {
            SessionManager.getAccessToken()?.let {
                try {
                    // Before the session is cleared, so this device's token is
                    // deleted while the request can still authenticate as the
                    // user it belongs to — otherwise the next person signing
                    // in on this device would keep receiving this one's pushes.
                    PushNotifications.currentFcmToken()?.let { fcmToken ->
                        DeviceTokenRepository().deleteToken(fcmToken)
                    }
                } catch (_: Exception) {
                    // Best effort — a stale token is a minor annoyance, not worth failing sign-out over.
                }
                try {
                    SupabaseService.signOut(it)
                } catch (_: Exception) {
                    // Revoking server side is best effort; the local session is cleared either way.
                }
            }
            SessionManager.clearSession()
            Result.success(Unit)
        } catch (e: Exception) {
            SessionManager.clearSession()
            Result.success(Unit)
        }
    }

    /**
     * Restores the profile for a stored session, refreshing the access token first when the
     * stored one has expired (Supabase access tokens last about an hour).
     */
    suspend fun getCurrentUserProfile(): Result<UserProfile?> {
        return try {
            val token = SessionManager.getAccessToken() ?: return Result.success(null)
            val userId = SessionManager.getUserId() ?: return Result.success(null)

            try {
                Result.success(fetchProfile(userId, token))
            } catch (e: SupabaseException) {
                if (e.statusCode != 401) throw e
                val refreshed = refreshAccessToken() ?: return Result.success(null)
                Result.success(fetchProfile(userId, refreshed))
            }
        } catch (e: Exception) {
            // A network blip is not "not logged in". The caller keeps the
            // splash up and offers retry instead of dumping them on login.
            Result.failure(e)
        }
    }

    /** Swaps the stored refresh token for a new access token, returning null if it is no longer valid. */
    private suspend fun refreshAccessToken(): String? =
        SessionRefresher.refresh(SessionManager.getAccessToken())

    suspend fun updateProfile(profile: UserProfile): Result<UserProfile> {
        return try {
            val token = SessionManager.getAccessToken()
            val body = buildJsonObject {
                put("full_name", profile.fullName)
                put("phone", profile.phone)
                profile.avatarUrl?.let { put("avatar_url", it) }
                profile.storeAddress?.let { put("store_address", it) }
                profile.storeLatitude?.let { put("store_latitude", it) }
                profile.storeLongitude?.let { put("store_longitude", it) }
            }.toString()
            SupabaseService.update("profiles", profile.id, body, token)
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Sets where a seller's store is, so a rider can navigate to pick up their orders. */
    suspend fun updateStoreLocation(userId: String, address: String, latitude: Double, longitude: Double): Result<Unit> {
        return try {
            val token = SessionManager.getAccessToken()
            val body = buildJsonObject {
                put("store_address", address)
                put("store_latitude", latitude)
                put("store_longitude", longitude)
            }.toString()
            SupabaseService.update("profiles", userId, body, token)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** A rider's own switch for whether they want new pool orders reaching them right now. */
    suspend fun setOnlineStatus(userId: String, online: Boolean): Result<Unit> {
        return try {
            val token = SessionManager.getAccessToken()
            val body = buildJsonObject { put("is_online", online) }.toString()
            SupabaseService.update("profiles", userId, body, token)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllUsers(): Result<List<UserProfile>> {
        return try {
            val token = SessionManager.getAccessToken()
            val list = SupabaseService.selectAll("profiles", token)
            Result.success(list.map { json.decodeFromString(UserProfile.serializer(), it.toString()) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUserRole(userId: String, role: String): Result<Unit> {
        return try {
            val token = SessionManager.getAccessToken()
            val body = buildJsonObject { put("role", role) }.toString()
            SupabaseService.update("profiles", userId, body, token)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Uploads a profile photo to the `avatars` storage bucket (public) and
     * updates the profile row's avatar_url with the resulting public URL.
     * Returns the updated [UserProfile] so the caller can refresh state.
     */
    suspend fun uploadAvatar(userId: String, bytes: ByteArray, mimeType: String): Result<UserProfile> {
        return try {
            val token = SessionManager.getAccessToken()
            val extension = when (mimeType) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            val path = "$userId/avatar.$extension"
            val url = SupabaseService.uploadFile("avatars", path, bytes, mimeType, token)

            val body = buildJsonObject { put("avatar_url", url) }.toString()
            SupabaseService.update("profiles", userId, body, token)

            // Fetch fresh profile so the returned object has all current fields.
            val profile = fetchProfile(userId, token ?: "") ?: return Result.failure(
                Exception("Avatar uploaded but profile could not be refreshed.")
            )
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
