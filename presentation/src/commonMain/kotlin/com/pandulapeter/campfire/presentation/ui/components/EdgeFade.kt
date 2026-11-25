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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Fades what scrolls in this container out towards its top edge, as far as it has been scrolled from its start: the
 * app's one way of showing that content goes on above, in place of an app bar that tints and lifts, or a divider. It
 * belongs before the scrolling modifier, so that it is drawn over the viewport rather than scrolled with the content.
 *
 * This one masks the content to transparent, which takes an offscreen layer of the whole viewport on every frame drawn
 * while it is scrolled at all, so it is only for a container that does not stand on one known opaque color (an alert
 * dialog's own container, the overscroll pull). Everything else paints the fade in its background color instead (the
 * overload that takes one), which looks the same there and costs a strip.
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
internal fun Modifier.fadingTopEdge(scrollState: ScrollState, backgroundColor: Color, height: Dp = EDGE_FADE_SIZE) = fadingTopEdge(
    scrolled = { scrollState.value },
    backgroundColor = backgroundColor,
    height = height,
)

/**
 * [fadingTopEdge] over a known opaque background, as strong as [scrolled], read while drawing, says. The color has to
 * be the one directly behind the container - a screen's background, or `sheetContainerColor()` in a sheet - with no
 * card or surface of another color in between, since it is what the content is painted over with.
 *
 * @param height How far down the fade reaches, [topEdgeFadeHeight] for a container that reaches under the status bar.
 */
internal fun Modifier.fadingTopEdge(scrolled: () -> Int, backgroundColor: Color, height: Dp = EDGE_FADE_SIZE) = fadingVerticalEdges(
    scrolledFromTop = scrolled,
    scrolledFromBottom = { 0 },
    backgroundColor = backgroundColor,
    topHeight = height,
)

/**
 * [fadingTopEdge] for a lazy list over a known opaque background, which only knows how far it is scrolled into its first
 * item.
 */
internal fun Modifier.fadingTopEdge(listState: LazyListState, backgroundColor: Color) = fadingTopEdge(
    scrolled = { listState.scrolledFromTop() },
    backgroundColor = backgroundColor,
)

/**
 * [fadingTopEdge] at both ends, for a container with nothing around it that would say there is more of it: a list in
 * the middle of a dialog, whose bottom edge is a row of buttons rather than the edge of the screen. Each edge fades as
 * far as there is content left past it, so a list that is not scrolled at all is drawn whole. Masked, like the plain
 * [fadingTopEdge], so only for a container on no one known color: the overload that takes one paints it instead.
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

/**
 * [fadingVerticalEdges] over a known opaque background (see the painted [fadingTopEdge]): the content stays where it is
 * drawn, and a strip of [backgroundColor] fading to transparent is painted over each edge, which shows the same pixels
 * the mask would there and needs no offscreen layer.
 *
 * @param topHeight How far down the top strip reaches, and so how far the content is scrolled before it is at full
 *   strength.
 * @param bottomHeight The same for the bottom strip.
 */
internal fun Modifier.fadingVerticalEdges(
    scrolledFromTop: () -> Int,
    scrolledFromBottom: () -> Int,
    backgroundColor: Color,
    topHeight: Dp = EDGE_FADE_SIZE,
    bottomHeight: Dp = EDGE_FADE_SIZE,
) = drawWithCache {
    val height = bottomHeight.toPx()
    val topHeightPx = topHeight.toPx()
    val topGradient = Brush.verticalGradient(
        colors = listOf(backgroundColor, backgroundColor.copy(alpha = 0f)),
        startY = 0f,
        endY = topHeightPx,
    )
    val bottomGradient = Brush.verticalGradient(
        colors = listOf(backgroundColor.copy(alpha = 0f), backgroundColor),
        startY = size.height - height,
        endY = size.height,
    )
    onDrawWithContent {
        drawContent()
        val topStrength = (scrolledFromTop() / topHeightPx).coerceIn(0f, 1f)
        if (topStrength > 0f) {
            drawRect(brush = topGradient, size = Size(size.width, topHeightPx), alpha = topStrength)
        }
        val bottomStrength = (scrolledFromBottom() / height).coerceIn(0f, 1f)
        if (bottomStrength > 0f) {
            drawRect(
                brush = bottomGradient,
                topLeft = Offset(0f, size.height - height),
                size = Size(size.width, height),
                alpha = bottomStrength,
            )
        }
    }
}

/**
 * The painted bottom fade of [fadingVerticalEdges] for a page that ends above a control pinned under it (the tools'
 * buttons) rather than one it scrolls behind, so that what reaches its bottom edge fades out as it would under a bar, as
 * strongly as [strength] says, read while drawing: 0 while there is no control, 1 once it is all the way in.
 */
internal fun Modifier.fadingBottomEdge(strength: () -> Float, backgroundColor: Color) = drawWithCache {
    val height = EDGE_FADE_SIZE.toPx()
    val gradient = Brush.verticalGradient(
        colors = listOf(backgroundColor.copy(alpha = 0f), backgroundColor),
        startY = size.height - height,
        endY = size.height,
    )
    onDrawWithContent {
        drawContent()
        val alpha = strength().coerceIn(0f, 1f)
        if (alpha > 0f) {
            drawRect(brush = gradient, topLeft = Offset(0f, size.height - height), size = Size(size.width, height), alpha = alpha)
        }
    }
}

/**
 * The painted [fadingTopEdge] of a container that runs on under the navigation bar, [bottomInset] tall, that also fades
 * out at its bottom over the bar and [EDGE_FADE_SIZE] above it, so the bar's buttons never stand on the content: as
 * strongly as there is content left below, so its end, which stops above the bar, is drawn whole. No bottom fade where
 * the inset is none (no bar, or one drawn over by something else), nor while [isBottomCovered], read while drawing: a
 * container ending at the keyboard fades at its top only.
 */
internal fun Modifier.fadingOverNavigationBar(
    scrollState: ScrollState,
    backgroundColor: Color,
    bottomInset: Dp,
    scrolledFromTop: () -> Int = { scrollState.value },
    isBottomCovered: () -> Boolean = { false },
) = fadingVerticalEdges(
    scrolledFromTop = scrolledFromTop,
    scrolledFromBottom = { if (bottomInset > 0.dp && !isBottomCovered()) scrollState.maxValue - scrollState.value else 0 },
    backgroundColor = backgroundColor,
    bottomHeight = bottomInset + EDGE_FADE_SIZE,
)

/** [fadingVerticalEdges] for a container scrolled by [scrollState]. */
internal fun Modifier.fadingVerticalEdges(scrollState: ScrollState) = fadingVerticalEdges(
    scrolledFromTop = { scrollState.value },
    scrolledFromBottom = { scrollState.maxValue - scrollState.value },
)

/** [fadingVerticalEdges] for a container scrolled by [scrollState] over a known opaque background. */
internal fun Modifier.fadingVerticalEdges(scrollState: ScrollState, backgroundColor: Color) = fadingVerticalEdges(
    scrolledFromTop = { scrollState.value },
    scrolledFromBottom = { scrollState.maxValue - scrollState.value },
    backgroundColor = backgroundColor,
)

/**
 * [fadingVerticalEdges] for a lazy list over a known opaque background, which only knows how far it is scrolled into
 * its first item, and how far its last one reaches past the viewport once that one has been laid out.
 */
internal fun Modifier.fadingVerticalEdges(listState: LazyListState, backgroundColor: Color) = fadingVerticalEdges(
    backgroundColor = backgroundColor,
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
 * [fadingVerticalEdges] for a lazy grid over a known opaque background, which knows as little as a lazy list does: how
 * far it is scrolled into its first item, and how far its last row — whichever of its items reaches lowest — reaches
 * past the viewport.
 */
internal fun Modifier.fadingVerticalEdges(gridState: LazyGridState, backgroundColor: Color) = fadingVerticalEdges(
    backgroundColor = backgroundColor,
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
 * Fades a sideways scrolling row out at its ends over a known opaque [backgroundColor], the way [fadingVerticalEdges]
 * paints it: at its start as far as it has been scrolled from it ([scrolledFromStart]) and at its end as far as it
 * still can be scrolled towards it ([scrolledFromEnd]), each null for an end that does not fade, the row running to
 * the screen's edge there rather than meeting something that does not scroll. The start is the right in a right to
 * left layout.
 */
internal fun Modifier.fadingHorizontalEdges(
    scrolledFromStart: (() -> Int)?,
    scrolledFromEnd: (() -> Int)?,
    backgroundColor: Color,
) = drawWithCache {
    val width = EDGE_FADE_SIZE.toPx()
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val leftGradient = Brush.horizontalGradient(listOf(backgroundColor, backgroundColor.copy(alpha = 0f)), startX = 0f, endX = width)
    val rightGradient = Brush.horizontalGradient(listOf(backgroundColor.copy(alpha = 0f), backgroundColor), startX = size.width - width, endX = size.width)
    onDrawWithContent {
        drawContent()
        val startStrength = ((scrolledFromStart?.invoke() ?: 0) / width).coerceIn(0f, 1f)
        val endStrength = ((scrolledFromEnd?.invoke() ?: 0) / width).coerceIn(0f, 1f)
        val (leftStrength, rightStrength) = if (isRtl) endStrength to startStrength else startStrength to endStrength
        if (leftStrength > 0f) drawRect(brush = leftGradient, size = Size(width, size.height), alpha = leftStrength)
        if (rightStrength > 0f) {
            drawRect(brush = rightGradient, topLeft = Offset(size.width - width, 0f), size = Size(width, size.height), alpha = rightStrength)
        }
    }
}

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

/**
 * Fades what scrolls under a pinned header out below it, the way it fades out under a bar: a gradient of
 * [backgroundColor] [fadeHeight] tall, drawn below the header over what passes under it, as strong as [strength] says
 * ([pinnedHeaderScrolledUnder]), read while it is drawn so that a scroll recomposes nothing.
 */
internal fun Modifier.fadingBelowPinnedHeader(backgroundColor: Color, fadeHeight: Float, strength: () -> Float) = drawWithContent {
    drawContent()
    val shown = strength()
    if (shown > 0f) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(backgroundColor, backgroundColor.copy(alpha = 0f)),
                startY = size.height,
                endY = size.height + fadeHeight,
            ),
            topLeft = Offset(0f, size.height),
            size = Size(size.width, fadeHeight),
            alpha = shown,
        )
    }
}

/**
 * How strongly what scrolls under a header pinned at [index] of a lazy list fades out below it, 0 to 1: not at all
 * while the header is not yet at the top, as far as its own item has scrolled under the top once it is
 * ([firstVisibleOffset] over [fadeHeight]), and fully once the items past it are what is first visible.
 */
internal fun pinnedHeaderScrolledUnder(firstVisibleIndex: Int, firstVisibleOffset: Int, index: Int, fadeHeight: Float): Float {
    val scrolledUnder = when {
        firstVisibleIndex > index -> fadeHeight
        firstVisibleIndex == index -> firstVisibleOffset.toFloat()
        else -> 0f
    }
    return (scrolledUnder / fadeHeight).coerceIn(0f, 1f)
}

private fun LazyListState.scrolledFromTop() = if (firstVisibleItemIndex > 0) Int.MAX_VALUE else firstVisibleItemScrollOffset

/** How far content fades in over below whatever it scrolls under, and how far it is scrolled before it fades fully. */
internal val EDGE_FADE_SIZE = 24.dp

/**
 * How far down the top fade of a container that reaches the window's top reaches, given the [topInset] its content
 * starts below: through the room under the status bar, so that what is scrolled up into it has faded out by the window's
 * edge and the clock is read over the background, while what is below the inset is drawn whole. At least
 * [EDGE_FADE_SIZE], which is all a window with no status bar (the desktop, the web) has to fade through.
 */
internal fun topEdgeFadeHeight(topInset: Dp) = maxOf(topInset, EDGE_FADE_SIZE)
