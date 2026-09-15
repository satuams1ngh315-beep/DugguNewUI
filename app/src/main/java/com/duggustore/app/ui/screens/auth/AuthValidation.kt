package com.duggustore.app.ui.screens.auth

import android.util.Patterns

/**
 * Shared across every auth screen so "what counts as a valid email" is one
 * definition instead of each screen's own ad-hoc contains("@") check (which
 * happily accepted things like "a@" or "a@b").
 */
fun isValidEmail(email: String): Boolean =
    Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
