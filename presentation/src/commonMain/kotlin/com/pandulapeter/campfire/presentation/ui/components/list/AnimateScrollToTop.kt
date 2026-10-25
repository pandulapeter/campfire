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

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.grid.LazyGridState
import kotlin.math.sqrt

/**
 * Scrolls the grid to its very top in one eased movement.
 *
 * `animateScrollToItem(0)` aims at a distance guessed from the rows it can see, and corrects the guess each time a new
 * row comes into view; with sticky headers the guess is off at every section, and the correction shows as the scroll
 * slowing down at a header and starting again. Here the remaining distance is estimated afresh on every frame instead,
 * and each frame covers the share of it the easing curve gives that frame, so a wrong estimate changes how far a frame
 * goes but never the shape of the movement, and the last frame, by when the first item is on screen and the estimate
 * is exact, lands on the top.
 */
internal suspend fun LazyGridState.animateScrollToTop() {
    if (!canScrollBackward) return
    val screens = distanceToTop() / layoutInfo.viewportSize.height.coerceAtLeast(1)
    val durationMillis = (MIN_DURATION_MILLIS + DURATION_MILLIS_PER_SCREEN * sqrt(screens)).toInt()
    var previousRemainingFraction = 1f
    scroll {
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = durationMillis.coerceAtMost(MAX_DURATION_MILLIS),
                easing = FastOutSlowInEasing,
            ),
        ) { fraction, _ ->
            val remainingFraction = 1f - fraction
            if (previousRemainingFraction > 0f) {
                scrollBy(-distanceToTop() * (1f - remainingFraction / previousRemainingFraction))
            }
            previousRemainingFraction = remainingFraction
        }
    }
    // Only where the last estimate fell short, which a list of rows of very different heights could make it.
    if (canScrollBackward) scrollToItem(0)
}

/**
 * How far the grid is from its top: exact once the first item is on screen, and before that the items above the first
 * visible one at the average height per item of those on screen. The pinned sticky header is left out of the average,
 * since it sits on top of the rows rather than among them.
 */
private fun LazyGridState.distanceToTop(): Float {
    val firstIndex = firstVisibleItemIndex
    if (firstIndex == 0) return firstVisibleItemScrollOffset.toFloat()
    val scrolledItems = layoutInfo.visibleItemsInfo.filter { it.index >= firstIndex }
    if (scrolledItems.isEmpty()) return firstVisibleItemScrollOffset.toFloat()
    val top = scrolledItems.minOf { it.offset.y }
    val bottom = scrolledItems.maxOf { it.offset.y + it.size.height }
    val itemCount = scrolledItems.maxOf { it.index } - firstIndex + 1
    return firstIndex * (bottom - top).toFloat() / itemCount + firstVisibleItemScrollOffset
}

private const val MIN_DURATION_MILLIS = 250
private const val DURATION_MILLIS_PER_SCREEN = 150
private const val MAX_DURATION_MILLIS = 800
