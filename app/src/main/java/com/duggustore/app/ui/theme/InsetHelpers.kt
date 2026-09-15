package com.duggustore.app.ui.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// ==========================================================================
// INSET HELPERS
// ==========================================================================
//
// v1 handled NO window insets anywhere. Searching the source for
// windowInsetsPadding / statusBarsPadding / navigationBarsPadding returned zero
// hits. The app was built before edge-to-edge enforcement and never updated, so:
//
//   * The status bar overlapped the top app bar's title on every Android 15
//     device (edge-to-edge is mandatory from API 35), and on API 30+ with
//     gesture navigation the first list row sat under the clock.
//   * The bottom bar's labels sat under the gesture pill (see Navigation.kt).
//   * Keyboard insets were never consumed on any form -- the checkout form's last
//     two fields (pincode, landmark) were covered by the IME with no scroll room.
//
// These helpers are thin wrappers so the intent is legible at the call site and
// so a reviewer can grep for "insets" and find every place that matters.

/** Top app bar: keep content clear of the status bar, never under it. */
fun Modifier.dugguStatusBarPadding(): Modifier =
    this.windowInsetsPadding(WindowInsets.statusBars)

/** Bottom bar / bottom action bar: grow AWAY from the gesture pill. */
fun Modifier.dugguNavigationBarPadding(): Modifier =
    this.windowInsetsPadding(WindowInsets.navigationBars)

/** Scrollable form: reserve room for the IME so the focused field stays visible. */
fun Modifier.dugguImePadding(): Modifier =
    this.windowInsetsPadding(WindowInsets.ime)

/**
 * Full-screen content that must not scroll under either system bar -- used by
 * ScreenScaffold for the fixed-header + scrolling-body layout.
 */
@Composable
fun Modifier.dugguScreenInsets(): Modifier =
    this
        .windowInsetsPadding(WindowInsets.statusBars)
        .windowInsetsPadding(WindowInsets.navigationBars)
