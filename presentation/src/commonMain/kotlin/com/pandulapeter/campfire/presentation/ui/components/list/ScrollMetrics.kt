/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.list

import androidx.compose.foundation.lazy.grid.LazyGridState
import kotlin.math.max

/**
 * Estimates the scroll position from the sizes of the visible items, as the total content size of a lazy grid is
 * unknown. Null if the whole list fits into the viewport.
 *
 * The distance scrolled from the top and the distance left to the bottom are estimated apart, each from the rows beyond
 * its own edge of the screen, and the position is the share of the first in their sum. Each is exact once its end of the
 * list is on screen, so the thumb reaches either end of the track exactly as the list does: estimated from the top alone,
 * the range of a short list was off by a good part of a screen, and the thumb jumped to the end, or the list to its last
 * row, wherever the list really ended.
 */
internal fun LazyGridState.scrollMetrics(): ScrollMetrics? {
    if (!canScrollForward && !canScrollBackward) return null
    val info = layoutInfo
    val firstIndex = firstVisibleItemIndex
    // A pinned sticky header is listed among the visible items but sits out of order, so it must not skew the estimate.
    val visibleItems = info.visibleItemsInfo.filter { it.index >= firstIndex }.distinctBy { it.index }
    if (visibleItems.isEmpty() || info.totalItemsCount == 0) return null
    val first = visibleItems.first()
    val last = visibleItems.maxBy { it.index }
    // Items of the same row share their offset, so this averages the height of a row over the items in it: the
    // per item slice of the content the index based math below works with.
    val averageItemSize = (last.offset.y + last.size.height - first.offset.y).toFloat() / visibleItems.size
    if (averageItemSize <= 0f) return null
    val scrollOffset = max(firstIndex * averageItemSize + firstVisibleItemScrollOffset, 0f)
    // Item offsets start below the leading content padding, and the last item rests above the trailing one at the end.
    val contentEnd = info.viewportEndOffset - info.afterContentPadding
    val remainingOffset = max((info.totalItemsCount - 1 - last.index) * averageItemSize + last.offset.y + last.size.height - contentEnd, 0f)
    val maxScrollOffset = max(scrollOffset + remainingOffset, 1f)
    return ScrollMetrics(
        scrollFraction = (scrollOffset / maxScrollOffset).coerceIn(0f, 1f),
        scrollOffset = scrollOffset,
        viewportHeight = info.viewportSize.height,
        viewportFraction = (info.viewportSize.height / (maxScrollOffset + info.viewportSize.height)).coerceIn(0f, 1f),
        averageItemSize = averageItemSize,
        totalItemsCount = info.totalItemsCount,
        maxScrollOffset = maxScrollOffset,
    )
}

internal class ScrollMetrics(
    val scrollFraction: Float,
    val scrollOffset: Float,
    val viewportHeight: Int,
    val viewportFraction: Float,
    val averageItemSize: Float,
    val totalItemsCount: Int,
    val maxScrollOffset: Float,
)
