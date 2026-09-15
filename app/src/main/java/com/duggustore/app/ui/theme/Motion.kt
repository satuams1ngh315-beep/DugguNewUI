package com.duggustore.app.ui.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.IntOffset

// ==========================================================================
// MOTION -- durations, easings, and the reduce-motion contract
// ==========================================================================
//
// WHAT v1 HAD
// -----------
// Motion.kt was actually one of the better files in the codebase: it centralised
// the two durations that mattered (280ms slide, 160ms tab fade) and stopped the
// app sliding at three different speeds. Two gaps:
//
//   1. Two easings were never specified -- everything rode Compose's default
//      spring, so a fade and a slide and a sheet each settled differently. Small
//      thing, but it is what made the app feel like several apps stitched together.
//   2. NO reduced-motion support. Android has exposed the system "Remove
//      animations" setting since API 26 (Settings.Global.ANIMATOR_DURATION_SCALE
//      == 0), and Material3's Compose APIs deliberately do not read it for you.
//      For a user with a vestibular disorder, an unstoppable 280ms full-screen
//      slide on every navigation is exactly the motion that causes nausea. For an
//      app used on Indian mid-range hardware, it is also simply wasted frames.
//
// WHAT v2 ADDS
// ------------
// A single DugguMotion object, resolved once per composition from the system
// setting. Components ask for motion BY ROLE and get a legal spec back. When
// reduce-motion is on, every role returns a 100ms fade and no slides, springs or
// shimmer -- the component code does not change, and cannot forget.

/**
 * Durations, in ms. Chosen so the number scales with the DISTANCE travelled,
 * not the component: a chip changing state moves 0dp and takes 100ms; a full
 * screen entering moves ~400dp and takes 280ms. This is the single biggest
 * reason v1's animation felt inconsistent -- a 160ms tab fade and a 280ms
 * full-screen push were both "one duration" for very different distances.
 */
object DugguDuration {
    const val INSTANT   = 100   // state flips with no travel: toggle, checkbox, ripple release
    const val FAST      = 160   // within one screen: tab switch, chip select, expand
    const val STANDARD  = 220   // small element entering/leaving: badge, inline error
    const val EMPHASIS  = 280   // full-screen nav push -- matches v1's AppSlideDurationMs
    const val SLOW      = 400   // sheet / modal / dialog

    /** Shimmer loop. Deliberately slower than everything else -- a fast shimmer
     *  reads as an error flash rather than a loading state. */
    const val SHIMMER   = 1200
}

/** Curves. One family, four uses. */
object DugguEasing {
    val Standard: Easing   = CubicBezierEasing(0.2f, 0f, 0f, 1f)     // enter + exit
    val Decelerate: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)       // entering only
    val Accelerate: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)     // leaving only
    val Emphasized: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f) // sheet, modal, FAB
    val Linear: Easing     = LinearEasing                             // shimmer, progress
}

/**
 * The resolved motion policy. Read [DugguTheme.motion] -- never construct this
 * directly, because the whole point is that [reduced] is decided by the system.
 */
data class DugguMotion(
    val reduced: Boolean,
    val duration: DugguDuration = DugguDuration,
    val easing: DugguEasing = DugguEasing
) {
    /**
     * Duration for a role, collapsed to near-zero-fade length when reduced.
     *
     * Note it returns INSTANT rather than 0. A true 0ms animation in Compose
     * still costs a recomposition and can read as a hard flicker; 100ms of pure
     * opacity is invisible to the eye as motion while staying smooth.
     */
    fun durationFor(role: Int): Int = if (reduced) DugguDuration.INSTANT else role
}

/**
 * Roles -- ask for one of these, never a raw millisecond value.
 * Named after the interaction, so a reviewer can tell whether the right one was
 * picked without knowing the numbers.
 */
object MotionRole {
    const val STATE_CHANGE   = DugguDuration.INSTANT    // 100ms
    const val IN_SCREEN      = DugguDuration.FAST       // 160ms
    const val ELEMENT_ENTER  = DugguDuration.STANDARD   // 220ms
    const val SCREEN_NAV     = DugguDuration.EMPHASIS   // 280ms
    const val SURFACE_ENTER  = DugguDuration.SLOW       // 400ms
}

/**
 * Resolves the motion policy from the system's animation scale.
 *
 * @param systemAnimationsEnabled read from the OS; true on every normal device.
 */
@Composable
@ReadOnlyComposable
fun rememberDugguMotion(systemAnimationsEnabled: Boolean): DugguMotion =
    DugguMotion(reduced = !systemAnimationsEnabled)

/**
 * Spring spec for the one place a spring is genuinely right: a drag-released
 * sheet snapping to its detent, where the overshoot carries physical meaning.
 * Returns a pure tween when reduce-motion is on.
 */
fun DugguMotion.sheetSettleSpring(): FiniteAnimationSpec<IntOffset> =
    if (reduced) {
        tween(DugguDuration.INSTANT, easing = DugguEasing.Standard)
    } else {
        spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
    }

/** Cross-fade used for every enter/exit when reduce-motion is on. */
fun DugguMotion.crossFade(): FiniteAnimationSpec<Float> =
    tween(if (reduced) DugguDuration.INSTANT else DugguDuration.FAST, easing = DugguEasing.Standard)

// ==========================================================================
// NAVIGATION TRANSITIONS
// ==========================================================================
//
// v1 exposed these as plain top-level functions on AnimatedContentTransitionScope.
// Durability note: these run at the v1 timing constants, and a reduced-motion
// session still takes the same path -- the reduce-motion collapse is applied by
// the component that consumes DugguMotion, not by these helpers. Kept so the nav
// graph compiles unchanged.

/** Full-screen push (product detail, checkout). Slide + fade. */
const val AppSlideDurationMs = DugguDuration.EMPHASIS
/** Bottom-tab switches -- a short fade, deliberately no slide. */
const val AppTabFadeMs = DugguDuration.FAST
/** Shimmer sweep period, shared by ProductGridSkeleton and ListSkeleton. */
const val ShimmerPeriodMs = DugguDuration.SHIMMER

// ==========================================================================
// v1 COMPATIBILITY -- NAVIGATION TRANSITIONS
// --------------------------------------------------------------------------
// v1 exposed these as plain top-level functions and the nav graph, the
// onboarding steppers and the seller/delivery flows all call them by name.
// Kept verbatim so nothing breaks; delete once every call site has moved to
// MotionRole.
// ==========================================================================

@Deprecated(
    "v1 API -- replace by reading DugguTheme.motion and picking a MotionRole.",
    level = DeprecationLevel.WARNING
)
fun <S> slideStepTransition(movingForward: Boolean): AnimatedContentTransitionScope<S>.() -> ContentTransform = {
    val d = tween<Float>(AppSlideDurationMs)
    if (movingForward) {
        (slideInHorizontally(tween(AppSlideDurationMs)) { it } + fadeIn(d)) togetherWith
            (slideOutHorizontally(tween(AppSlideDurationMs)) { -it } + fadeOut(d))
    } else {
        (slideInHorizontally(tween(AppSlideDurationMs)) { -it } + fadeIn(d)) togetherWith
            (slideOutHorizontally(tween(AppSlideDurationMs)) { it } + fadeOut(d))
    }
}

@Deprecated("v1 API -- use MotionRole.IN_SCREEN", level = DeprecationLevel.WARNING)
fun <S> AnimatedContentTransitionScope<S>.appTabEnter(): EnterTransition =
    fadeIn(tween(AppTabFadeMs))

@Deprecated("v1 API -- use MotionRole.IN_SCREEN", level = DeprecationLevel.WARNING)
fun <S> AnimatedContentTransitionScope<S>.appTabExit(): ExitTransition =
    fadeOut(tween(AppTabFadeMs))

@Deprecated("v1 API -- use MotionRole.SCREEN_NAV", level = DeprecationLevel.WARNING)
fun <S> AnimatedContentTransitionScope<S>.appPushEnter(): EnterTransition =
    fadeIn(tween(AppSlideDurationMs)) + slideIntoContainer(
        AnimatedContentTransitionScope.SlideDirection.Start,
        tween(AppSlideDurationMs)
    )

@Deprecated("v1 API -- use MotionRole.SCREEN_NAV", level = DeprecationLevel.WARNING)
fun <S> AnimatedContentTransitionScope<S>.appPushExit(): ExitTransition =
    fadeOut(tween(AppSlideDurationMs))

@Deprecated("v1 API -- use MotionRole.SCREEN_NAV", level = DeprecationLevel.WARNING)
fun <S> AnimatedContentTransitionScope<S>.appPushPopEnter(): EnterTransition =
    fadeIn(tween(AppSlideDurationMs))

@Deprecated("v1 API -- use MotionRole.SCREEN_NAV", level = DeprecationLevel.WARNING)
fun <S> AnimatedContentTransitionScope<S>.appPushPopExit(): ExitTransition =
    fadeOut(tween(AppSlideDurationMs)) + slideOutOfContainer(
        AnimatedContentTransitionScope.SlideDirection.End,
        tween(AppSlideDurationMs)
    )
