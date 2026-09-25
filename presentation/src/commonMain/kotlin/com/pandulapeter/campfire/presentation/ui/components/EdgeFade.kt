/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * The fade the two list screens' cards go out in towards the top of the list, where the pinned section header and the
 * app bar's buttons float over them with nothing behind them: the cards are gone entirely under the header's row, so
 * its text is read against the screen's background whatever is scrolled under it, and fade back in below the row.
 *
 * It is drawn by each card rather than over the list, because the sticky headers are items of the same list and an
 * overlay would fade them out with the cards. Each card masks itself with the part of the gradient it overlaps, from
 * where it is laid out relative to the top of the list ([listTopFadeViewport]), and only while it overlaps it, since the
 * mask needs a layer of its own. It is as strong as the list is scrolled, up to the height it fades over, so a list
 * resting at its top shows its first cards whole - they start right under the first header's row, where the fade
 * would otherwise begin - and the fade comes in as the list is moved rather than all at once. It is at full strength
 * before a card has come up far enough to reach the header's text.
 *
 * A card under the gradient takes no presses there either (see [fadingUnderListTop]): what is faded out is not what a
 * tap near the header or the buttons is meant for, and a card that can barely be seen should not open on a slip of
 * the finger aimed at a pill.
 */
@Stable
internal class ListTopFade(
    private val listState: LazyGridState,
    val coveredHeightPx: Float,
    val fadeHeightPx: Float,
) {

    /** How far down from the top of the list the fade reaches. */
    val heightPx = coveredHeightPx + fadeHeightPx

    /** Where the top of the list is in the window, which the cards place themselves against. */
    var viewportTop by mutableFloatStateOf(0f)

    /** How strong the fade is, which grows with how far the list has been scrolled from its top. */
    val strength: Float
        get() = if (listState.firstVisibleItemIndex > 0) 1f else (listState.firstVisibleItemScrollOffset / fadeHeightPx).coerceIn(0f, 1f)
}

@Composable
internal fun rememberListTopFade(listState: LazyGridState): ListTopFade {
    val density = LocalDensity.current
    return remember(listState, density) {
        with(density) {
            ListTopFade(
                listState = listState,
                coveredHeightPx = LIST_APP_BAR_HEIGHT.toPx(),
                fadeHeightPx = EDGE_FADE_SIZE.toPx(),
            )
        }
    }
}

/** Marks the list whose top [fade] is measured from. */
internal fun Modifier.listTopFadeViewport(fade: ListTopFade) = onPlaced { fade.viewportTop = it.positionInWindow().y }

/** Fades a card of the list out as it passes under the top of it, and keeps presses there from reaching it. */
@Composable
internal fun Modifier.fadingUnderListTop(fade: ListTopFade): Modifier {
    val position = remember { CardPosition() }
    return this
        .onPlaced { position.top = it.positionInWindow().y - fade.viewportTop }
        .graphicsLayer {
            compositingStrategy = if (fade.strength > 0f && position.top < fade.heightPx) CompositingStrategy.Offscreen else CompositingStrategy.Auto
        }
        .drawWithContent {
            drawContent()
            val strength = fade.strength
            if (strength > 0f && position.top < fade.heightPx) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 1f - strength), Color.Black),
                        startY = fade.coveredHeightPx - position.top,
                        endY = fade.heightPx - position.top,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
        .pointerInput(fade) {
            awaitEachGesture {
                // Only the press is taken, before the card sees it, so that the card never starts one there - the
                // list's own scrolling does not ask whether the press was consumed, so a drag started there still
                // scrolls it.
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (fade.strength > 0f && position.top + down.position.y < fade.heightPx) {
                    down.consume()
                }
            }
        }
}

/** Where one card is relative to the top of its list, written as it is placed and read as it is drawn. */
private class CardPosition {

    var top by mutableFloatStateOf(0f)
}

/**
 * Fades what scrolls in this container out towards its top edge, as far as it has been scrolled from its start: the
 * app's one way of showing that content goes on above, in place of an app bar that tints and lifts, or a divider. It
 * belongs before the scrolling modifier, so that it is drawn over the viewport rather than scrolled with the content.
 *
 * @param scrolled How far the content has been scrolled from its start, read while it is drawn.
 */
internal fun Modifier.fadingTopEdge(scrolled: () -> Int) = this
    .graphicsLayer {
        compositingStrategy = if (scrolled() > 0) CompositingStrategy.Offscreen else CompositingStrategy.Auto
    }
    .drawWithContent {
        drawContent()
        val height = EDGE_FADE_SIZE.toPx()
        val strength = (scrolled() / height).coerceIn(0f, 1f)
        if (strength > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 1f - strength), Color.Black),
                    startY = 0f,
                    endY = height,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
    }

/** [fadingTopEdge] for a container scrolled by [scrollState]. */
internal fun Modifier.fadingTopEdge(scrollState: ScrollState) = fadingTopEdge { scrollState.value }

/** [fadingTopEdge] for a lazy list, which only knows how far it is scrolled into its first item. */
internal fun Modifier.fadingTopEdge(listState: LazyListState) = fadingTopEdge {
    if (listState.firstVisibleItemIndex > 0) Int.MAX_VALUE else listState.firstVisibleItemScrollOffset
}

/**
 * Fades what scrolls in this container out towards its left edge during horizontal slide animation.
 */
internal fun Modifier.fadingLeftEdge(
    alpha: Float,
) = this
    .graphicsLayer {
        compositingStrategy = if (alpha > 0) CompositingStrategy.Offscreen else CompositingStrategy.Auto
    }
    .drawWithContent {
        drawContent()
        val width = EDGE_FADE_SIZE.toPx()
        if (alpha > 0) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Black.copy(alpha = 1f - alpha), Color.Black),
                    startX = 0f,
                    endX = width,
                ),
                size = Size(width, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
    }

/** How far content fades in over below whatever it scrolls under, and how far it is scrolled before it fades fully. */
private val EDGE_FADE_SIZE = 24.dp
