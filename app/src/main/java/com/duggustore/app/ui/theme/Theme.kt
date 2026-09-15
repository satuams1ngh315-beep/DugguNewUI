package com.duggustore.app.ui.theme

import android.app.Activity
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ==========================================================================
// DugguTheme -- the single entry point for colour, type, shape and motion
// ==========================================================================
//
// DROP-IN REPLACEMENT FOR THE EXISTING Theme.kt
// ---------------------------------------------
// The signature `DugguTheme(content = ...)` is unchanged, and every existing call
// site keeps compiling. What changed is everything behind it. v1:
//
//     @Composable fun DugguStoreTheme(content: @Composable () -> Unit) {
//         val colorScheme = LightColorScheme          // <- hard-coded
//         ...
//         MaterialTheme(colorScheme, Typography, content)
//     }
//
// Problems, in the order they hurt:
//
//  1. LIGHT ONLY. The color scheme was a single private val with no dark
//     counterpart, and the status-bar appearance was pinned to light. The app
//     ignored the OS dark-mode setting entirely. DugguStore is used heavily in
//     the evening (it is a 10-minute delivery app); a full-brightness white
//     canvas at 11pm is a real usability problem, not a nice-to-have.
//
//  2. NO SURFACE CHANNEL. The scheme carried Material's 30-odd slots, but the app's
//     own components bypass Material and read top-level vals (TextPrimary,
//     SurfaceWhite, Hairline...) directly. So there was no way to change scheme at
//     runtime -- every colour reference was a compile-time constant.
//
//  3. NO SHAPES, NO MOTION. `MaterialTheme(shapes = ...)` was never passed, so
//     Material's Dialog/BottomSheet/Card invented their own corner radii, which
//     is why the app had 2dp, 4dp, 6dp, 9dp, 10dp, 11dp, 14dp and 16dp corners.
//
//  4. THE STATUS-BAR WRITE CRASHES IN PREVIEW. `view.context as Activity` throws
//     inside @Preview and on any non-Activity context (which includes a
//     ContextThemeWrapper host). Guaranteed crash the first time someone opens a
//     preview of a themed screen.
//
// WHAT THIS FILE DOES
// -------------------
// Resolves light/dark from the system, provides the semantic colour set, shape
// set, type ramp and motion policy through CompositionLocals, and mirrors the
// important values into MaterialTheme so Material's own components match.

// -- Material3 mirror schemes --------------------------------------------
// Built FROM the semantic set, so there is exactly one place a brand value lives.
// If these two ever disagree with DugguColors, DugguColors wins and this is a bug.
private val MaterialLight = lightColorScheme(
    primary              = LightDugguColors.action.primary,
    onPrimary            = LightDugguColors.text.onBrand,
    primaryContainer     = LightDugguColors.surface.brandSubtle,
    onPrimaryContainer   = LightDugguColors.text.primary,
    secondary            = LightDugguColors.text.secondary,
    onSecondary          = LightDugguColors.surface.default,
    secondaryContainer   = LightDugguColors.surface.sunken,
    onSecondaryContainer = LightDugguColors.text.primary,
    tertiary             = LightDugguColors.text.info,
    background           = LightDugguColors.surface.canvas,
    onBackground         = LightDugguColors.text.primary,
    surface              = LightDugguColors.surface.default,
    onSurface            = LightDugguColors.text.primary,
    surfaceVariant       = LightDugguColors.surface.sunken,
    onSurfaceVariant     = LightDugguColors.text.secondary,
    outline              = LightDugguColors.border.default,
    outlineVariant       = LightDugguColors.border.subtle,
    error                = LightDugguColors.text.danger,
    onError              = LightDugguColors.text.onBrand,
    errorContainer       = LightDugguColors.surface.dangerSubtle,
    onErrorContainer     = LightDugguColors.text.primary,
    inverseSurface       = LightDugguColors.surface.inverse,
    inverseOnSurface     = LightDugguColors.text.onInverse
)

private val MaterialDark = darkColorScheme(
    primary              = DarkDugguColors.action.primary,
    onPrimary            = DarkDugguColors.text.onBrand,
    primaryContainer     = DarkDugguColors.surface.brandSubtle,
    onPrimaryContainer   = DarkDugguColors.text.primary,
    secondary            = DarkDugguColors.text.secondary,
    onSecondary          = DarkDugguColors.surface.default,
    secondaryContainer   = DarkDugguColors.surface.sunken,
    onSecondaryContainer = DarkDugguColors.text.primary,
    tertiary             = DarkDugguColors.text.info,
    background           = DarkDugguColors.surface.canvas,
    onBackground         = DarkDugguColors.text.primary,
    surface              = DarkDugguColors.surface.default,
    onSurface            = DarkDugguColors.text.primary,
    surfaceVariant       = DarkDugguColors.surface.sunken,
    onSurfaceVariant     = DarkDugguColors.text.secondary,
    outline              = DarkDugguColors.border.default,
    outlineVariant       = DarkDugguColors.border.subtle,
    error                = DarkDugguColors.text.danger,
    onError              = DarkDugguColors.text.onBrand,
    errorContainer       = DarkDugguColors.surface.dangerSubtle,
    onErrorContainer     = DarkDugguColors.text.primary,
    inverseSurface       = DarkDugguColors.surface.inverse,
    inverseOnSurface     = DarkDugguColors.text.onInverse
)

/**
 * Which brand skin to apply. Exists so a seasonal campaign or a regional
 * sub-brand is a one-line change at the root rather than a fork.
 */
enum class DugguBrand { DEFAULT, SAFFRON, INDIGO }

/** How the app should pick light vs dark. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * The app theme.
 *
 * @param themeMode      SYSTEM follows the OS (the default, and what v1 needed).
 *                       SettingsScreen can persist LIGHT/DARK here for a manual
 *                       override without fighting the system.
 * @param brand          Which brand skin to bind.
 * @param content        The app.
 */
@Composable
fun DugguTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    brand: DugguBrand = DugguBrand.DEFAULT,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT  -> false
        ThemeMode.DARK   -> true
    }

    val colors = remember(dark, brand) {
        when {
            dark && brand == DugguBrand.DEFAULT -> DarkDugguColors
            dark                                -> DarkDugguColors   // alt skins are light-only for now
            brand == DugguBrand.SAFFRON         -> SaffronOnLight
            brand == DugguBrand.INDIGO          -> IndigoOnLight
            else                                -> LightDugguColors
        }
    }

    val context = LocalContext.current

    // Read the OS animation scale ONCE per context. ANIMATOR_DURATION_SCALE == 0f
    // is the documented value of Android's "Remove animations" accessibility
    // setting; anything else means motion is fine. v1 never read this at all.
    val motion = remember(context) {
        val scale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE
            )
        }.getOrDefault(1f)
        DugguMotion(reduced = scale == 0f)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // v1 did `view.context as Activity` unconditionally -- an unchecked
            // cast that throws in @Preview and in any wrapped context. `as?` keeps
            // the preview alive and simply skips the chrome write.
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = colors.surface.default.toArgb()

            val controller = WindowCompat.getInsetsController(window, view)
            // Icon appearance must INVERT with the theme: dark icons on a light
            // bar, light icons on a dark one. v1 pinned `true`, so in dark mode the
            // status-bar icons would have been black-on-black.
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    CompositionLocalProvider(
        LocalDugguColors provides colors,
        LocalDugguMotion provides motion,
        LocalDugguTypography provides Typography,
        LocalDugguDimens provides Dimens
    ) {
        MaterialTheme(
            colorScheme = if (dark) MaterialDark else MaterialLight,
            typography  = Typography,
            shapes      = Shapes,          // v1 omitted this -- Material invented its own corners
            content     = content
        )
    }
}

/**
 * Accessor object. Components read `DugguTheme.colors.text.primary` -- short,
 * and it reads as intent rather than as a variable name.
 *
 *     val c = DugguTheme.colors
 *     val m = DugguTheme.motion
 */
object DugguTheme {
    val colors: DugguColors
        @Composable get() = LocalDugguColors.current
    val motion: DugguMotion
        @Composable get() = LocalDugguMotion.current
    val type: Typography
        @Composable get() = LocalDugguTypography.current
    val dimens: Dimens
        @Composable get() = LocalDugguDimens.current
    /** True when the OS prefers light -- handy for a one-off illustration tint. */
    val isLight: Boolean
        @Composable get() = LocalDugguColors.current.isLight
}

// -- Additional locals ----------------------------------------------------

/** Static: the type ramp never changes at runtime, so no invalidation cost. */
val LocalDugguTypography = staticCompositionLocalOf { Typography }

/** Dimens is an object of constants; a local keeps the read syntax uniform. */
val LocalDugguDimens = staticCompositionLocalOf { Dimens }

/** Motion DOES change (system setting), so this one is a real composition local. */
val LocalDugguMotion = androidx.compose.runtime.compositionLocalOf {
    DugguMotion(reduced = false)
}

/**
 * v1 entry point, kept so `MainActivity` keeps compiling.
 *
 * v1:
 *     @Composable fun DugguStoreTheme(content: @Composable () -> Unit)
 *
 * The two are not the same call. v1 hard-coded the light scheme and pinned the
 * status-bar icons to dark, so the app ignored the OS dark-mode setting. This
 * alias now forwards to [DugguTheme], which means simply swapping the name in
 * MainActivity immediately grants dark mode, the system-bar appearance fix and
 * reduce-motion support -- that is the whole P0 in one line.
 */
@Deprecated(
    message = "Renamed to DugguTheme, which adds dark mode, correct system bars " +
              "and reduce-motion. Change the call in MainActivity.kt.",
    replaceWith = ReplaceWith("DugguTheme(content = content)"),
    level = DeprecationLevel.WARNING
)
@Composable
fun DugguStoreTheme(content: @Composable () -> Unit) = DugguTheme(content = content)
