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
internal fun Modifier.fadingTopEdge(scrollState: ScrollState, backgroundColor: Color) = fadingTopEdge(
    scrolled = { scrollState.value },
    backgroundColor = backgroundColor,
)

/**
 * [fadingTopEdge] over a known opaque background, as strong as [scrolled], read while drawing, says. The color has to
 * be the one directly behind the container - a screen's background, or `sheetContainerColor()` in a sheet - with no
 * card or surface of another color in between, since it is what the content is painted over with.
 */
internal fun Modifier.fadingTopEdge(scrolled: () -> Int, backgroundColor: Color) = fadingVerticalEdges(
    scrolledFromTop = scrolled,
    scrolledFromBottom = { 0 },
    backgroundColor = backgroundColor,
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
 */
internal fun Modifier.fadingVerticalEdges(
    scrolledFromTop: () -> Int,
    scrolledFromBottom: () -> Int,
    backgroundColor: Color,
) = drawWithCache {
    val height = EDGE_FADE_SIZE.toPx()
    val topGradient = Brush.verticalGradient(
        colors = listOf(backgroundColor, backgroundColor.copy(alpha = 0f)),
        startY = 0f,
        endY = height,
    )
    val bottomGradient = Brush.verticalGradient(
        colors = listOf(backgroundColor.copy(alpha = 0f), backgroundColor),
        startY = size.height - height,
        endY = size.height,
    )
    onDrawWithContent {
        drawContent()
        val topStrength = (scrolledFromTop() / height).coerceIn(0f, 1f)
        if (topStrength > 0f) {
            drawRect(brush = topGradient, size = Size(size.width, height), alpha = topStrength)
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

/** [fadingVerticalEdges] for a container scrolled by [scrollState]. */
internal fun Modifier.fadingVerticalEdges(scrollState: ScrollState) = fadingVerticalEdges(
    scrolledFromTop = { scrollState.value },
    scrolledFromBottom = { scrollState.maxValue - scrollState.value },
)

/**
 * [fadingVerticalEdges] sideways, for a row that scrolls: each end fades as far as there is content left past it, so a
 * row that fits, or rests at an end, is drawn whole there. The start is the right edge in a right to left layout.
 * Masked, so for a row on no one known color.
 */
internal fun Modifier.fadingHorizontalEdges(scrollState: ScrollState) = this
    .graphicsLayer {
        compositingStrategy = if (scrollState.value > 0 || scrollState.maxValue - scrollState.value > 0) CompositingStrategy.Offscreen else CompositingStrategy.Auto
    }
    .drawWithContent {
        drawContent()
        val width = EDGE_FADE_SIZE.toPx()
        val isRtl = layoutDirection == LayoutDirection.Rtl
        val startStrength = (scrollState.value / width).coerceIn(0f, 1f)
        val endStrength = ((scrollState.maxValue - scrollState.value) / width).coerceIn(0f, 1f)
        val leftStrength = if (isRtl) endStrength else startStrength
        val rightStrength = if (isRtl) startStrength else endStrength
        if (leftStrength > 0f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Black.copy(alpha = 1f - leftStrength), Color.Black),
                    startX = 0f,
                    endX = width,
                ),
                size = Size(width, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
        if (rightStrength > 0f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Black, Color.Black.copy(alpha = 1f - rightStrength)),
                    startX = size.width - width,
                    endX = size.width,
                ),
                topLeft = Offset(size.width - width, 0f),
                size = Size(width, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
    }

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
 * Fades what scrolls in this container out towards its left edge during horizontal slide animation, painted in the
 * [backgroundColor] it stands on, so a swipe draws a strip rather than the whole container into a layer of its own.
 *
 * @param alpha How strong the fade is, read while it is drawn, so that animating it draws again without composing.
 */
internal fun Modifier.fadingLeftEdge(
    alpha: () -> Float,
    backgroundColor: Color,
) = drawWithCache {
    val width = EDGE_FADE_SIZE.toPx()
    val gradient = Brush.horizontalGradient(
        colors = listOf(backgroundColor, backgroundColor.copy(alpha = 0f)),
        startX = 0f,
        endX = width,
    )
    onDrawWithContent {
        drawContent()
        val strength = alpha().coerceIn(0f, 1f)
        if (strength > 0f) {
            drawRect(brush = gradient, size = Size(width, size.height), alpha = strength)
        }
    }
}

/** How far content fades in over below whatever it scrolls under, and how far it is scrolled before it fades fully. */
internal val EDGE_FADE_SIZE = 24.dp
