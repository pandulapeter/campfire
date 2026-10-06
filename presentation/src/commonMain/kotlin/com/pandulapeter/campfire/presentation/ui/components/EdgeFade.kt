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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.platform.OverscrollPull

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
 *
 * A list too short to scroll is still carried up under the header by the overscroll effect when it is pulled
 * ([overscrollPull], handed to the grid's `bounceScrollableContent`), so the fade follows that pull too: as strong as
 * the list has been pulled, and moved down the cards by as much, since the stretch carries the cards' own drawing up
 * with them.
 *
 * A list whose sections each start with a header of [sectionHeaderContentType] measures the fade from the start of the
 * section being read instead (see [sectionStartDistance]), so a list brought to one of its headers - which is where a
 * tap on a header takes it - shows that section's first card whole, as it shows the first section's at the top.
 */
@Stable
internal class ListTopFade(
    private val listState: LazyGridState,
    private val coveredHeight: () -> Float,
    val fadeHeightPx: Float,
    private val firstCardIndex: Int,
    private val sectionHeaderContentType: Any?,
) {

    val overscrollPull = OverscrollPull()

    /**
     * How far the cards are drawn above where they are laid out. Only a list that cannot scroll is pulled, and a pull
     * left over by a list that has grown scrollable meanwhile, whose release was cut short with it, counts for
     * nothing. Capped at the fade's height, which is as far below it as a card's position is followed.
     */
    val pullPx: Float
        get() = if (listState.canScrollBackward || listState.canScrollForward) 0f else overscrollPull.towardsStart.coerceAtMost(heightPx)

    /** How far down from the top of the list the fade reaches. */
    val coveredHeightPx: Float
        get() = coveredHeight()

    val heightPx: Float
        get() = coveredHeightPx + fadeHeightPx

    /** Where the top of the list is in the window, which the cards place themselves against. */
    var viewportTop by mutableFloatStateOf(0f)

    /** How strong the fade is, which grows with how far the list has been scrolled from its top. */
    val strength: Float
        get() {
            val strength = listTopFadeStrength(
                canScrollBackward = listState.canScrollBackward,
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                scrollOffset = listState.firstVisibleItemScrollOffset,
                firstCardIndex = firstCardIndex,
                coveredHeightPx = coveredHeightPx,
                fadeHeightPx = fadeHeightPx,
                overscrollPullPx = pullPx,
            )
            if (sectionHeaderContentType == null || strength == 0f) return strength
            val distance = sectionStartDistance(
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                scrollOffset = listState.firstVisibleItemScrollOffset,
                headerPositions = listState.layoutInfo.visibleItemsInfo.mapNotNull { item ->
                    if (item.contentType == sectionHeaderContentType) item.index to item.offset.y else null
                },
            ) ?: return strength
            return minOf(strength, (distance / fadeHeightPx).coerceIn(0f, 1f))
        }
}

/** The covered row recedes as the app bar takes its place; headerless lists fade directly below the bar. */
@Composable
internal fun rememberListTopFade(
    listState: LazyGridState,
    coveredHeightFraction: () -> Float = { 1f },
    firstCardIndex: Int = 0,
    sectionHeaderContentType: Any? = null,
): ListTopFade {
    val density = LocalDensity.current
    return remember(listState, density, coveredHeightFraction, firstCardIndex, sectionHeaderContentType) {
        with(density) {
            ListTopFade(
                listState = listState,
                coveredHeight = { LIST_APP_BAR_HEIGHT.toPx() * coveredHeightFraction().coerceIn(0f, 1f) },
                fadeHeightPx = EDGE_FADE_SIZE.toPx(),
                firstCardIndex = firstCardIndex,
                sectionHeaderContentType = sectionHeaderContentType,
            )
        }
    }
}

/**
 * Measures distance from the real start, including an expanded header but excluding collapsed header slots, or, for a
 * list at its start, how far it has been pulled up past its end.
 */
internal fun listTopFadeStrength(
    canScrollBackward: Boolean,
    firstVisibleItemIndex: Int,
    scrollOffset: Int,
    firstCardIndex: Int,
    coveredHeightPx: Float,
    fadeHeightPx: Float,
    overscrollPullPx: Float = 0f,
): Float {
    if (!canScrollBackward) return (overscrollPullPx / fadeHeightPx).coerceIn(0f, 1f)
    if (firstVisibleItemIndex > firstCardIndex) return 1f
    val passedHeader = if (firstCardIndex > 0 && firstVisibleItemIndex == firstCardIndex) coveredHeightPx else 0f
    return ((passedHeader + scrollOffset) / fadeHeightPx).coerceIn(0f, 1f)
}

/**
 * How far the list is from the start of a section, in pixels: scrolled into the header at its top, or short of the
 * next header coming up from below - whichever is nearer, so the fade thins out as the next header reaches the top
 * and comes back as it is scrolled past, rather than dropping away in the frame that header becomes the first item.
 * Null where no section header is on screen but a pinned one, which is a section being read well past its start.
 *
 * @param headerPositions The index and the laid out top of every section header on screen, the pinned one included.
 */
internal fun sectionStartDistance(
    firstVisibleItemIndex: Int,
    scrollOffset: Int,
    headerPositions: List<Pair<Int, Int>>,
): Int? = headerPositions.mapNotNull { (index, top) ->
    when {
        // A pinned header is drawn at the top whatever its place, so only how far the list is scrolled into it counts.
        index == firstVisibleItemIndex -> scrollOffset
        index > firstVisibleItemIndex -> top.coerceAtLeast(0)
        else -> null
    }
}.minOrNull()

/** Marks the list whose top [fade] is measured from. */
internal fun Modifier.listTopFadeViewport(fade: ListTopFade) = onPlaced { fade.viewportTop = it.positionInWindow().y }

/** Fades a card of the list out as it passes under the top of it, and keeps presses there from reaching it. */
@Composable
internal fun Modifier.fadingUnderListTop(fade: ListTopFade): Modifier {
    val position = remember { CardPosition() }
    return this
        // Clamped to where the fade ends once moved down by the most a pull moves it, so that a card below it writes the
        // same value on every frame of a scroll and its drawing is left alone: only the one or two cards under the fade
        // are drawn again as the list moves.
        .onPlaced { position.top = minOf(it.positionInWindow().y - fade.viewportTop, 2 * fade.heightPx) }
        // The card's own position is read first, so that only a card under the fade reads the scroll offset at all.
        .graphicsLayer {
            compositingStrategy = if (position.top - fade.pullPx < fade.heightPx && fade.strength > 0f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
        }
        .drawWithCache {
            // Keep the shader in viewport coordinates and move the canvas beneath it. Rebuilding the gradient
            // with the card's new offset allocated a brush and shader on every frame of a scroll.
            val mask = Brush.verticalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startY = fade.coveredHeightPx,
                endY = fade.heightPx,
            )
            onDrawWithContent {
                drawContent()
                // A pulled list draws its cards higher than they are laid out, so the gradient is drawn as much lower.
                val top = position.top - fade.pullPx
                if (top < fade.heightPx) {
                    val strength = fade.strength
                    if (strength > 0f) {
                        translate(top = -top) {
                            drawRect(
                                brush = mask,
                                topLeft = Offset(0f, top),
                                size = size,
                                alpha = strength,
                                blendMode = BlendMode.DstOut,
                            )
                        }
                    }
                }
            }
        }
        .pointerInput(fade) {
            awaitEachGesture {
                // Only the press is taken, before the card sees it, so that the card never starts one there - the
                // list's own scrolling does not ask whether the press was consumed, so a drag started there still
                // scrolls it.
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                // A card below the fade holds twice the fade's own height as its top, which no press inside it adds up
                // to less than the fade's height, so the clamp leaves the answer as it would be with the real position.
                if (fade.strength > 0f && position.top + down.position.y < fade.heightPx) {
                    down.consume()
                }
            }
        }
}

/**
 * Where one card is relative to the top of its list, written as it is placed and read as it is drawn. It is no further
 * down than a pulled fade can reach, since below that the exact position makes no difference to how the card is drawn.
 */
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
internal fun Modifier.fadingTopEdge(scrolled: () -> Int) = fadingVerticalEdges(
    scrolledFromTop = scrolled,
    scrolledFromBottom = { 0 },
)

/** [fadingTopEdge] for a container scrolled by [scrollState]. */
internal fun Modifier.fadingTopEdge(scrollState: ScrollState) = fadingTopEdge { scrollState.value }

/**
 * The same fade over a known opaque background, without rasterizing the entire viewport into an offscreen buffer.
 * Only the edge strip is painted. Keep the masking overload for dialogs and other containers whose backing differs.
 */
internal fun Modifier.fadingTopEdge(scrollState: ScrollState, backgroundColor: Color) = fadingTopEdge(
    scrolled = { scrollState.value },
    backgroundColor = backgroundColor,
)

/** [fadingTopEdge] over a known opaque background, as strong as [scrolled], read while drawing, says. */
internal fun Modifier.fadingTopEdge(scrolled: () -> Int, backgroundColor: Color) = drawWithCache {
    val height = EDGE_FADE_SIZE.toPx()
    val gradient = Brush.verticalGradient(
        colors = listOf(backgroundColor, backgroundColor.copy(alpha = 0f)),
        startY = 0f,
        endY = height,
    )
    onDrawWithContent {
        drawContent()
        val strength = (scrolled() / height).coerceIn(0f, 1f)
        if (strength > 0f) {
            drawRect(brush = gradient, size = Size(size.width, height), alpha = strength)
        }
    }
}

/** [fadingTopEdge] for a lazy list, which only knows how far it is scrolled into its first item. */
internal fun Modifier.fadingTopEdge(listState: LazyListState) = fadingTopEdge { listState.scrolledFromTop() }

/**
 * [fadingTopEdge] at both ends, for a container with nothing around it that would say there is more of it: a list in
 * the middle of a dialog, whose bottom edge is a row of buttons rather than the edge of the screen. Each edge fades as
 * far as there is content left past it, so a list that is not scrolled at all is drawn whole.
 *
 * @param scrolledFromTop How far the content has been scrolled from its start, read while it is drawn.
 * @param scrolledFromBottom How far it still can be scrolled towards its end, read while it is drawn.
 */
internal fun Modifier.fadingVerticalEdges(
    scrolledFromTop: () -> Int,
    scrolledFromBottom: () -> Int,
) = this
    .graphicsLayer {
        compositingStrategy = if (scrolledFromTop() > 0 || scrolledFromBottom() > 0) CompositingStrategy.Offscreen else CompositingStrategy.Auto
    }
    .drawWithContent {
        drawContent()
        val height = EDGE_FADE_SIZE.toPx()
        val topStrength = (scrolledFromTop() / height).coerceIn(0f, 1f)
        if (topStrength > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 1f - topStrength), Color.Black),
                    startY = 0f,
                    endY = height,
                ),
                size = Size(size.width, height),
                blendMode = BlendMode.DstIn,
            )
        }
        val bottomStrength = (scrolledFromBottom() / height).coerceIn(0f, 1f)
        if (bottomStrength > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black, Color.Black.copy(alpha = 1f - bottomStrength)),
                    startY = size.height - height,
                    endY = size.height,
                ),
                topLeft = Offset(0f, size.height - height),
                size = Size(size.width, height),
                blendMode = BlendMode.DstIn,
            )
        }
    }

/** [fadingVerticalEdges] for a container scrolled by [scrollState]. */
internal fun Modifier.fadingVerticalEdges(scrollState: ScrollState) = fadingVerticalEdges(
    scrolledFromTop = { scrollState.value },
    scrolledFromBottom = { scrollState.maxValue - scrollState.value },
)

/**
 * [fadingVerticalEdges] for a lazy list, which only knows how far it is scrolled into its first item, and how far its
 * last one reaches past the viewport once that one has been laid out.
 */
internal fun Modifier.fadingVerticalEdges(listState: LazyListState) = fadingVerticalEdges(
    scrolledFromTop = { listState.scrolledFromTop() },
    scrolledFromBottom = {
        val layoutInfo = listState.layoutInfo
        val lastItem = layoutInfo.visibleItemsInfo.lastOrNull()
        when {
            lastItem == null -> 0
            lastItem.index < layoutInfo.totalItemsCount - 1 -> Int.MAX_VALUE
            else -> (lastItem.offset + lastItem.size + layoutInfo.afterContentPadding - layoutInfo.viewportEndOffset).coerceAtLeast(0)
        }
    },
)

/**
 * [fadingVerticalEdges] for a lazy grid, which knows as little as a lazy list does: how far it is scrolled into its
 * first item, and how far its last row — whichever of its items reaches lowest — reaches past the viewport.
 */
internal fun Modifier.fadingVerticalEdges(gridState: LazyGridState) = fadingVerticalEdges(
    scrolledFromTop = { if (gridState.firstVisibleItemIndex > 0) Int.MAX_VALUE else gridState.firstVisibleItemScrollOffset },
    scrolledFromBottom = {
        val layoutInfo = gridState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        when {
            visibleItems.isEmpty() -> 0
            visibleItems.last().index < layoutInfo.totalItemsCount - 1 -> Int.MAX_VALUE
            else -> (visibleItems.maxOf { it.offset.y + it.size.height } + layoutInfo.afterContentPadding - layoutInfo.viewportEndOffset).coerceAtLeast(0)
        }
    },
)

/**
 * Fades a sideways scrolling row out under something pinned over its start, a control the row's items scroll behind:
 * they are gone entirely under its [overlayWidth] and fade back in over [EDGE_FADE_SIZE] past it, so the pinned control
 * is read against the background whatever is scrolled under it. As strong as the row is scrolled, like
 * [fadingTopEdge], so a row resting at its start - whose first item starts past the control - is drawn whole.
 *
 * @param scrolledFromStart How far the row has been scrolled from its start, read while it is drawn.
 */
internal fun Modifier.fadingUnderStartOverlay(scrolledFromStart: () -> Int, overlayWidth: Dp) = this
    .graphicsLayer {
        compositingStrategy = if (scrolledFromStart() > 0) CompositingStrategy.Offscreen else CompositingStrategy.Auto
    }
    .drawWithContent {
        drawContent()
        val fadeWidth = EDGE_FADE_SIZE.toPx()
        val strength = (scrolledFromStart() / fadeWidth).coerceIn(0f, 1f)
        if (strength > 0f) {
            val hidden = Color.Black.copy(alpha = 1f - strength)
            val overlay = overlayWidth.toPx()
            // The start of the row is its right edge in a right to left layout.
            val isRtl = layoutDirection == LayoutDirection.Rtl
            val maskWidth = overlay + fadeWidth
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to hidden,
                    overlay / maskWidth to hidden,
                    1f to Color.Black,
                    startX = if (isRtl) size.width else 0f,
                    endX = if (isRtl) size.width - maskWidth else maskWidth,
                ),
                topLeft = Offset(if (isRtl) size.width - maskWidth else 0f, 0f),
                size = Size(maskWidth, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
    }

private fun LazyListState.scrolledFromTop() = if (firstVisibleItemIndex > 0) Int.MAX_VALUE else firstVisibleItemScrollOffset

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
internal val EDGE_FADE_SIZE = 24.dp
