/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.appBar

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp

/** The pill of the list screen's app bar that the buttons under it stand in, and null everywhere else. */
internal val LocalSearchPill = staticCompositionLocalOf<SearchPill?> { null }

/** One button standing on the [SearchPill] (a [PillIconButton]), whose presses the pill draws rather than the button. */
internal class SearchPillButton {

    /** What the button tells about its presses, hovers, and focus, which the pill's own indication listens to. */
    val interactions = MutableInteractionSource()

    /** The button's touch target in the bar's coordinates, centered on the container its presses are measured in. */
    var bounds by mutableStateOf(Rect.Zero)
}

/**
 * The part of the pill one button's presses fill, with the radii of its left and right corners: the pill's own at the
 * ends of the pill and none where it meets its neighbor.
 */
internal data class SearchPillSegment(
    val bounds: Rect,
    val leftRadius: Float,
    val rightRadius: Float,
)

/**
 * The share of the closed pill that the button at [index] of [centers] has: from the middle of the gap to the button
 * before it to the middle of the gap to the one after it, the first and the last reaching the pill's rounded ends, so
 * that the buttons' presses fill the pill edge to edge between them and never overlap. As the search opens
 * ([openProgress]) the pill leaves the buttons, and each share closes in on the button's own circle, [containerRadius]
 * around its middle, which is the ripple Material would have drawn without a pill under it.
 *
 * A divider standing between two buttons ([dividers]) takes the place of the middle of their gap: each share stops at
 * its side of the divider's room, so that neither press runs over the line.
 *
 * @param centers The middle of every button on the pill, in order from left to right.
 * @param dividers The room of every divider on the pill.
 */
internal fun searchPillSegment(
    closedPill: Rect,
    centers: List<Offset>,
    index: Int,
    containerRadius: Float,
    openProgress: Float,
    dividers: List<Rect> = emptyList(),
): SearchPillSegment {
    val center = centers[index]
    val isFirst = index == 0
    val isLast = index == centers.lastIndex
    val closed = Rect(
        left = if (isFirst) closedPill.left else boundary(centers[index - 1].x, center.x, dividers) { it.right },
        top = closedPill.top,
        right = if (isLast) closedPill.right else boundary(center.x, centers[index + 1].x, dividers) { it.left },
        bottom = closedPill.bottom,
    )
    val pillRadius = closedPill.height / 2
    return SearchPillSegment(
        bounds = lerp(closed, Rect(center = center, radius = containerRadius), openProgress),
        leftRadius = lerp(if (isFirst) pillRadius else 0f, containerRadius, openProgress),
        rightRadius = lerp(if (isLast) pillRadius else 0f, containerRadius, openProgress),
    )
}

/** Where the share of a button ends towards its neighbor: the [side] of a divider between them, or halfway. */
private inline fun boundary(left: Float, right: Float, dividers: List<Rect>, side: (Rect) -> Float) =
    dividers.firstOrNull { it.left >= left && it.right <= right }?.let(side) ?: ((left + right) / 2)

private fun lerp(start: Float, stop: Float, fraction: Float) = start + (stop - start) * fraction
