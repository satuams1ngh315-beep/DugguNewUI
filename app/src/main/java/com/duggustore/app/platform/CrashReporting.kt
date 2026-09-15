package com.duggustore.app.platform

import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Thin, never-throwing wrapper over Firebase Crashlytics.
 *
 * Every call is wrapped in try/catch on purpose: reporting must never crash
 * the app it is trying to observe (e.g. a debug build without google-services
 * wired, or Firebase failing to init on a device without Play Services).
 * Fatal crashes additionally keep the local file log in DugguStoreApp, so a
 * crash is never silent even where Crashlytics can't upload.
 */
object CrashReporting {

    /** Tags subsequent reports with the signed-in user (cleared on sign-out). */
    fun setUserId(userId: String) {
        try {
            if (userId.isNotBlank()) FirebaseCrashlytics.getInstance().setUserId(userId)
        } catch (_: Exception) {
            // Reporting is best-effort by design.
        }
    }

    fun clearUser() {
        try {
            FirebaseCrashlytics.getInstance().setUserId("")
        } catch (_: Exception) {
            // Reporting is best-effort by design.
        }
    }

    /** Breadcrumb for the next report — where the user was when it happened. */
    fun log(message: String) {
        try {
            FirebaseCrashlytics.getInstance().log(message)
        } catch (_: Exception) {
            // Reporting is best-effort by design.
        }
    }

    /** A caught error worth seeing in the console (network failures, bad payloads). */
    fun recordException(throwable: Throwable) {
        try {
            FirebaseCrashlytics.getInstance().recordException(throwable)
        } catch (_: Exception) {
            // Reporting is best-effort by design.
        }
    }
}
