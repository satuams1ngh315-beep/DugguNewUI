package com.duggustore.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duggustore.app.data.model.UserProfile
import com.duggustore.app.data.remote.AuthDeepLink
import com.duggustore.app.data.remote.SupabaseService
import com.duggustore.app.data.remote.SessionManager
import com.duggustore.app.data.repository.AuthRepository
import com.duggustore.app.platform.CrashReporting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

data class AuthState(
    val isLoading: Boolean = false,
    /** True until the stored session has been checked, so the UI can hold on a splash. */
    val isRestoringSession: Boolean = true,
    val isLoggedIn: Boolean = false,
    val user: UserProfile? = null,
    val error: String? = null,
    val successMessage: String? = null,
    val requiresEmailVerification: Boolean = false,
    val pendingVerificationEmail: String = "",
    val verificationResent: Boolean = false,
    val passwordResetSent: Boolean = false,
    /** True once a reset deep link handed us a session and the new password is due. */
    val awaitingNewPassword: Boolean = false,
    val passwordUpdated: Boolean = false
)

const val VERIFICATION_CODE_LENGTH = 6

class AuthViewModel : ViewModel() {
    private val repository = AuthRepository()

    // --- helpers cloned from AuthRepository (they are private there) so the
    //     deep-link / restore flows can resolve a profile locally ---
    private fun extractUserMetadata(resp: kotlinx.serialization.json.JsonObject): kotlinx.serialization.json.JsonObject? {
        val user = resp["user"] as? kotlinx.serialization.json.JsonObject
            ?: (resp["session"] as? kotlinx.serialization.json.JsonObject)?.get("user") as? kotlinx.serialization.json.JsonObject
            ?: return null
        return user.get("user_metadata") as? kotlinx.serialization.json.JsonObject
    }

    private fun metadataString(metadata: kotlinx.serialization.json.JsonObject?, key: String, default: String): String =
        metadata?.get(key)?.jsonPrimitive?.content?.takeIf { it.isNotBlank() && it != "null" } ?: default

    private suspend fun resolveProfile(userId: String, token: String, fallback: UserProfile): UserProfile {
        return try {
            val existing = com.duggustore.app.data.remote.SupabaseService.select(
                "profiles", token, mapOf("id" to userId)
            ).firstOrNull()?.let {
                kotlinx.serialization.json.Json.decodeFromString(UserProfile.serializer(), it.toString())
            }
            if (existing != null) existing else fallback
        } catch (e: Exception) {
            fallback
        }
    }

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state

    init {
        checkCurrentUser()
    }

    private fun checkCurrentUser() {
        viewModelScope.launch {
            _state.value = _state.value.copy(error = null)
            if (!SessionManager.isLoggedIn()) {
                _state.value = _state.value.copy(isRestoringSession = false)
                return@launch
            }
            _state.value = _state.value.copy(isLoading = true)
            val result = repository.getCurrentUserProfile()
            result.onSuccess { profile ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    isRestoringSession = false,
                    isLoggedIn = profile != null,
                    user = profile,
                    error = null
                )
            }
            result.onFailure {
                _state.value = _state.value.copy(
                    isLoading = false,
                    // Stay on splash so a flaky network does not look like a logout.
                    isRestoringSession = true,
                    error = it.message ?: "Couldn't reach the store. Check your connection."
                )
            }
        }
    }

    fun signUp(email: String, password: String, fullName: String, phone: String, role: String, referralCode: String = "") {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val result = repository.signUp(email, password, fullName, phone, role, referralCode)
            result.onSuccess { signUpResult ->
                if (signUpResult.requiresVerification) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        requiresEmailVerification = true,
                        pendingVerificationEmail = signUpResult.email
                    )
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        user = signUpResult.profile,
                        successMessage = "Account created successfully!"
                    )
                    CrashReporting.setUserId(signUpResult.profile?.id.orEmpty())
                }
            }
            result.onFailure { e ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Sign up failed"
                )
            }
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val result = repository.signIn(email, password)
            result.onSuccess { profile ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    isLoggedIn = true,
                    user = profile
                )
                CrashReporting.setUserId(profile.id)
            }
            result.onFailure { e ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Login failed"
                )
            }
        }
    }

    /**
     * [email] comes from the verify route, so the flow still works if the process was
     * killed and restored while the user was in their mail app and the in-memory
     * pendingVerificationEmail is gone.
     */
    fun verifyEmailCode(email: String, code: String) {
        val target = email.ifBlank { _state.value.pendingVerificationEmail }
        if (target.isBlank()) {
            _state.value = _state.value.copy(error = "No email to verify. Please sign up again.")
            return
        }
        if (code.length != VERIFICATION_CODE_LENGTH) {
            _state.value = _state.value.copy(error = "Enter the $VERIFICATION_CODE_LENGTH-digit code from your email.")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val result = repository.verifyEmailCode(target, code)
            result.onSuccess { profile ->
                // Clearing requiresEmailVerification alongside isLoggedIn keeps the
                // verify screen from re-triggering once navigation moves on.
                _state.value = _state.value.copy(
                    isLoading = false,
                    isLoggedIn = true,
                    user = profile,
                    requiresEmailVerification = false,
                    pendingVerificationEmail = "",
                    verificationResent = false,
                    successMessage = "Email verified!"
                )
                CrashReporting.setUserId(profile.id)
            }
            result.onFailure { e ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Verification failed"
                )
            }
        }
    }

    fun resendVerificationEmail(email: String = "") {
        val target = email.ifBlank { _state.value.pendingVerificationEmail }
        if (target.isBlank()) return

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val result = repository.resendVerificationEmail(target)
            result.onSuccess {
                _state.value = _state.value.copy(
                    isLoading = false,
                    verificationResent = true
                )
            }
            result.onFailure { e ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to resend email"
                )
            }
        }
    }

    fun sendPasswordReset(email: String) {
        if (email.isBlank()) {
            _state.value = _state.value.copy(error = "Enter your email address.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            repository.sendPasswordReset(email)
                .onSuccess {
                    _state.value = _state.value.copy(isLoading = false, passwordResetSent = true)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = e.message ?: "Could not send the reset email"
                    )
                }
        }
    }

    /** Exchanges tokens from a deep link for a full profile. */
    suspend fun completeSignInFromLink(accessToken: String, refreshToken: String): UserProfile? {
        val email = SessionManager.getEmail() ?: ""
        SessionManager.saveSession(accessToken, refreshToken, SessionManager.getUserId() ?: "", email)
        return try {
            val userId = SessionManager.getUserId() ?: return null
            val token = SessionManager.getAccessToken() ?: return null
            val resp = SupabaseService.getUser(token)
            val metadata = extractUserMetadata(resp)
            val fallback = UserProfile(
                id = userId,
                fullName = metadataString(metadata, "full_name", ""),
                phone = metadataString(metadata, "phone", ""),
                role = metadataString(metadata, "role", "customer")
            )
            resolveProfile(userId, token, fallback)
        } catch (e: Exception) {
            null
        }
    }

    /** Re-prompts for reauthentication. Supabase returns a confirmed session here,
     *  so there is nothing to reprompt — the flow just continues. */
    suspend fun repromptConsent(): Boolean = true

    /** Called when the activity receives the password-reset deep link. */
    fun onAuthDeepLink(link: AuthDeepLink) {
        when (link) {
            is AuthDeepLink.SignedIn -> {
                _state.value = _state.value.copy(isLoading = true, isRestoringSession = false, error = null)
                viewModelScope.launch {
                    repository.completeSignInFromLink(link.accessToken, link.refreshToken)
                        .onSuccess { profile ->
                            _state.value = _state.value.copy(
                                isLoading = false,
                                isLoggedIn = profile != null,
                                user = profile,
                                requiresEmailVerification = false,
                                pendingVerificationEmail = "",
                                successMessage = "Email verified!"
                            )
                        }
                        .onFailure { e ->
                            _state.value = _state.value.copy(
                                isLoading = false,
                                error = e.message ?: "Could not finish signing in from that link"
                            )
                        }
                }
            }
            is AuthDeepLink.Recovery -> {
                repository.beginPasswordRecovery(link.accessToken, link.refreshToken)
                _state.value = _state.value.copy(
                    isRestoringSession = false,
                    awaitingNewPassword = true,
                    passwordResetSent = false,
                    error = null
                )
            }
            is AuthDeepLink.Failed -> {
                _state.value = _state.value.copy(
                    isRestoringSession = false,
                    awaitingNewPassword = false,
                    error = link.message
                )
            }
        }
    }

    fun updatePassword(newPassword: String, confirmPassword: String) {
        when {
            newPassword.length < 6 -> {
                _state.value = _state.value.copy(error = "Password must be at least 6 characters.")
                return
            }
            newPassword != confirmPassword -> {
                _state.value = _state.value.copy(error = "Passwords don't match.")
                return
            }
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            repository.updatePassword(newPassword)
                .onSuccess { profile ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        awaitingNewPassword = false,
                        passwordUpdated = true,
                        passwordResetSent = false,
                        // A recovery session is a real session, so finish the sign-in
                        // rather than making them log in again with the new password.
                        isLoggedIn = profile != null,
                        user = profile
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = e.message ?: "Could not update the password"
                    )
                }
        }
    }

    fun clearPasswordUpdated() {
        _state.value = _state.value.copy(passwordUpdated = false)
    }

    /** Abandons a reset the user no longer wants to finish. */
    fun cancelPasswordRecovery() {
        SessionManager.clearSession()
        _state.value = AuthState(isRestoringSession = false)
    }

    fun resetPasswordResetState() {
        _state.value = _state.value.copy(passwordResetSent = false, error = null)
    }

    fun signOut() {
        viewModelScope.launch {
            repository.signOut()
            _state.value = AuthState(isRestoringSession = false)
            CrashReporting.clearUser()
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun clearSuccess() {
        _state.value = _state.value.copy(successMessage = null)
    }

    fun resetVerificationState() {
        _state.value = _state.value.copy(
            requiresEmailVerification = false,
            pendingVerificationEmail = "",
            verificationResent = false
        )
    }

    fun refreshProfile() {
        checkCurrentUser()
    }

    /**
     * A seller sets their store's pickup point once; every rider who claims
     * one of their orders reads it back through the order's embedded seller
     * info to navigate there.
     */
    fun updateStoreLocation(address: String, latitude: Double, longitude: Double) {
        val user = _state.value.user ?: return
        viewModelScope.launch {
            val result = repository.updateStoreLocation(user.id, address, latitude, longitude)
            result.onSuccess {
                _state.value = _state.value.copy(
                    user = user.copy(
                        storeAddress = address,
                        storeLatitude = latitude,
                        storeLongitude = longitude
                    )
                )
            }
            result.onFailure {
                _state.value = _state.value.copy(error = it.message)
            }
        }
    }

    /** A rider going online or offline for new pool orders. */
    fun setOnline(online: Boolean) {
        val user = _state.value.user ?: return
        viewModelScope.launch {
            val result = repository.setOnlineStatus(user.id, online)
            result.onSuccess {
                _state.value = _state.value.copy(user = user.copy(isOnline = online))
            }
            result.onFailure {
                _state.value = _state.value.copy(error = it.message)
            }
        }
    }

    /** Name/phone edited from Settings. */
    fun updateProfile(fullName: String, phone: String, onDone: () -> Unit = {}) {
        val user = _state.value.user ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val result = repository.updateProfile(user.copy(fullName = fullName, phone = phone))
            result.onSuccess { updated ->
                _state.value = _state.value.copy(isLoading = false, user = updated)
                onDone()
            }
            result.onFailure {
                _state.value = _state.value.copy(isLoading = false, error = it.message)
            }
        }
    }

    /**
     * Picks up the image bytes from the UI, uploads to the `avatars` bucket,
     * then refreshes the in-memory user profile with the new avatar_url so
     * every composable that reads [AuthState.user.avatarUrl] recomposes
     * immediately — no manual reload required.
     */
    fun uploadAvatar(bytes: ByteArray, mimeType: String) {
        val userId = _state.value.user?.id ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            repository.uploadAvatar(userId, bytes, mimeType)
                .onSuccess { updated ->
                    _state.value = _state.value.copy(isLoading = false, user = updated)
                }
                .onFailure {
                    _state.value = _state.value.copy(isLoading = false, error = it.message)
                }
        }
    }
}
