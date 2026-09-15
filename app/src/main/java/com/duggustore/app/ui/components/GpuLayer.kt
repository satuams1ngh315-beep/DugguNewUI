package com.duggustore.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.duggustore.app.ui.theme.TextPrimary
import kotlin.math.abs

/** Side pages in a peeking pager, matching the Myntra offer-rail scale. */
internal const val PagerMinScale = 0.88f

/** How far a grid product card shrinks on press. */
private const val CardPressedScale = 0.97f
private const val CardPressAnimMs = 90

/**
 * 0 when [page] is a full page away, 1 when it is the current page —
 * interpolates through the in-flight swipe/auto-advance. Must be called
 * from a draw/layer block so the read stays off the composition path.
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun pagerPageFraction(pagerState: PagerState, page: Int): Float {
    val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
    return 1f - abs(offset).coerceIn(0f, 1f)
}

/**
 * GPU RenderNode transform for one pager page.
 *
 * Pager offset is read inside [graphicsLayer], which Compose records onto
 * a hardware layer and replays during draw. A state change there invalidates
 * **draw only** — not composition, not layout — so a surrounding LazyColumn
 * can fling at 60fps while this scale keeps running.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.gpuPagerScale(
    pagerState: PagerState,
    page: Int,
    minScale: Float = PagerMinScale,
    minAlpha: Float = 1f
): Modifier = graphicsLayer {
    val t = pagerPageFraction(pagerState, page)
    val scale = minScale + (1f - minScale) * t
    scaleX = scale
    scaleY = scale
    // Alpha is skipped at 1: a translucent graphicsLayer forces an extra
    // offscreen pass, and three of those on the home rail was enough to
    // hitch the parent LazyColumn's fling.
    if (minAlpha < 1f) {
        alpha = minAlpha + (1f - minAlpha) * t
    }
    clip = false
}

/**
 * Stretches the active pager dot from a circle toward a pill on the same
 * GPU layer, so the indicator tracks the swipe without recomposing.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.gpuPagerDot(pagerState: PagerState, index: Int): Modifier =
    graphicsLayer {
        val t = pagerPageFraction(pagerState, index)
        scaleX = 1f + 2f * t
        transformOrigin = TransformOrigin.Center
        alpha = 0.4f + 0.6f * t
        clip = false
    }

/**
 * Press-scale that does **not** attach a RenderNode while idle.
 *
 * A [graphicsLayer] on every grid card (even at scale 1) is an extra
 * offscreen buffer per cell — that is what made the feed hitch after GPU
 * scale was wired onto [StoreProductCard]. The layer is added only while
 * the 90ms press animation is actually away from 1, so a fling sees a
 * plain clickable card.
 */
@Composable
fun Modifier.gpuClickableScale(
    onClick: () -> Unit,
    pressedScale: Float = CardPressedScale,
    shape: Shape? = null
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pressed.value) {
        scale.animateTo(
            targetValue = if (pressed.value) pressedScale else 1f,
            animationSpec = tween(CardPressAnimMs)
        )
    }
    // Read in composition so the modifier node itself can come and go.
    // Only the one pressed card recomposes (~6 frames); idle cards keep
    // no layer at all.
    val s = scale.value
    return this
        .then(
            if (s != 1f) {
                Modifier.graphicsLayer {
                    scaleX = s
                    scaleY = s
                    clip = false
                }
            } else {
                Modifier
            }
        )
        .then(if (shape != null) Modifier.clip(shape) else Modifier)
        .clickable(
            interactionSource = interactionSource,
            indication = rememberRipple(color = TextPrimary),
            onClick = onClick
        )
}
