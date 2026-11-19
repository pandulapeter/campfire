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

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset

/**
 * The top of a tool page that reaches the window's top with a [pinnedSearchField] in it (the Chords and the Scales
 * tabs), drawn over the page's grid in place of its [fadingTopEdge]. What is scrolled up into the room under the status
 * bar fades out there ([topEdgeFadeHeight]) as on every page; once the field is pinned below that room, the room is the
 * field's background instead, so that what passes under the field does not show again above it. The one hands over to
 * the other as the field arrives, over the last [EDGE_FADE_SIZE] before it pins, rather than at once.
 */
internal fun Modifier.pinnedSearchPageTopEdge(gridState: LazyGridState, backgroundColor: Color) = drawWithCache {
    val arrivalHeight = EDGE_FADE_SIZE.toPx()
    // The inset is asked of the grid while drawing, never here: the layout info changes on every frame of a scroll, and
    // reading it here would build the cache again on each of them. The gradient is built again only for a new height.
    var gradientHeight = 0f
    var gradient: Brush? = null
    onDrawWithContent {
        drawContent()
        val layoutInfo = gridState.layoutInfo
        val inset = layoutInfo.beforeContentPadding.toFloat()
        val fadeHeight = maxOf(inset, arrivalHeight)
        val fadeGradient = gradient.takeIf { gradientHeight == fadeHeight } ?: Brush.verticalGradient(
            colors = listOf(backgroundColor, backgroundColor.copy(alpha = 0f)),
            startY = 0f,
            endY = fadeHeight,
        ).also {
            gradient = it
            gradientHeight = fadeHeight
        }
        val edge = pinnedSearchTopEdge(
            firstVisibleItemIndex = gridState.firstVisibleItemIndex,
            scrollOffset = gridState.firstVisibleItemScrollOffset,
            fieldTop = layoutInfo.visibleItemsInfo.firstOrNull { it.index == PINNED_SEARCH_INDEX }?.offset?.y,
            fadeHeight = fadeHeight,
            arrivalHeight = arrivalHeight,
        )
        if (edge.fade > 0f) {
            drawRect(brush = fadeGradient, size = Size(size.width, fadeHeight), alpha = edge.fade)
        }
        if (edge.cover > 0f && inset > 0f) {
            drawRect(color = backgroundColor, size = Size(size.width, inset), alpha = edge.cover)
        }
    }
}

/**
 * How strongly [pinnedSearchPageTopEdge] fades the page out through the room under the status bar ([fade]), and how
 * much of the field's background it already lays over that room ([cover]).
 */
internal data class PinnedSearchTopEdge(val fade: Float, val cover: Float)

/**
 * [PinnedSearchTopEdge] for a page whose first visible item is [firstVisibleItemIndex], scrolled [scrollOffset] into it,
 * with the search field laid out [fieldTop] below the start of its content while it is on screen and not yet pinned.
 * The fade is as strong as the page has been scrolled into [fadeHeight], as every top fade is, and gives way to the
 * cover as the field comes within [arrivalHeight] of its pinned place: a field arriving at the top meets a room already
 * filled in, and the fade never reaches over the field where the room is shallower than the fade (the desktop's).
 */
internal fun pinnedSearchTopEdge(
    firstVisibleItemIndex: Int,
    scrollOffset: Int,
    fieldTop: Int?,
    fadeHeight: Float,
    arrivalHeight: Float,
): PinnedSearchTopEdge {
    if (firstVisibleItemIndex >= PINNED_SEARCH_INDEX) return PinnedSearchTopEdge(fade = 0f, cover = 1f)
    val arrival = fieldTop?.let { (1f - it / arrivalHeight).coerceIn(0f, 1f) } ?: 0f
    val scrolled = if (firstVisibleItemIndex > 0) Float.MAX_VALUE else scrollOffset.toFloat()
    return PinnedSearchTopEdge(fade = (scrolled / fadeHeight).coerceIn(0f, 1f) * (1f - arrival), cover = arrival)
}

/**
 * Keeps a pinned [pinnedSearchField] below the top content padding of [gridState] (the room under the status bar)
 * rather than where Foundation pins a sticky item, at the top of the viewport whatever the padding: its pinned place is
 * where a list resting at its start begins, as it is for one that keeps its content below the status bar. Read while
 * placing, so that a scroll moves it without recomposing it.
 */
internal fun Modifier.stickingBelowContentPadding(gridState: LazyGridState, index: Int) = offset {
    val layoutInfo = gridState.layoutInfo
    val top = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.offset?.y ?: 0
    IntOffset(x = 0, y = (-top).coerceIn(0, layoutInfo.beforeContentPadding))
}
