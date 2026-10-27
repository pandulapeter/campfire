/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.instrument

import androidx.compose.ui.util.lerp

/**
 * Whether a tool's instrument pill, [pillWidth] wide uncut, stands beside its trailing controls ([trailingWidth], parted
 * by [gap]) on a line [lineWidth] wide, rather than on a line of its own over them: only where it fits whole. Cut, it
 * would show too little of either name to read (one em of room past the padding is not one letter and an ellipsis), so
 * it takes a line of its own and the controls slide under it.
 */
internal fun pillSharesLine(lineWidth: Int, trailingWidth: Int, gap: Int, pillWidth: Int) =
    lineWidth - trailingWidth - gap >= pillWidth

/** Where a tool's instrument line puts its pill and its trailing controls, part of the way to the pill's own line. */
internal data class ToolLinePlaces(
    val pillMaxWidth: Int,
    val trailingY: Int,
    val height: Int,
)

/**
 * The line at [progress] (0 beside, 1 under) of the way from the trailing controls standing beside the pill, centred in
 * the [lineHeight] line, to standing under it, [gap] below the pill and end-aligned as before: the controls travel
 * straight down, the line grows with them by exactly as much, so that what stands under the line (the Tuner's mode
 * toggle under the play circle) keeps its distance to them throughout, and the pill widens into the room they leave only
 * once they have cleared it, so that it never reaches under them. Without [hasRoomUnder] the line ends where its pill
 * or its controls do, as a tool's line with nothing at its end does, since the room the bar height leaves under them
 * would part it from the next group by more than the page's other groups are parted.
 */
internal fun toolLinePlaces(
    progress: Float,
    lineWidth: Int,
    lineHeight: Int,
    gap: Int,
    pillHeight: Int,
    trailingWidth: Int,
    trailingHeight: Int,
    hasRoomUnder: Boolean,
): ToolLinePlaces {
    // The expressive spring overshoots both ends.
    val fraction = progress.coerceIn(0f, 1f)
    val pillBottom = (lineHeight - pillHeight) / 2 + pillHeight
    val besideY = (lineHeight - trailingHeight) / 2
    val trailingY = lerp(besideY, pillBottom + gap, fraction)
    val slack = if (hasRoomUnder) lineHeight - besideY - trailingHeight else 0
    val room = lineWidth - trailingWidth - gap
    val cleared = ((trailingY - pillBottom).toFloat() / gap.coerceAtLeast(1)).coerceIn(0f, 1f)
    return ToolLinePlaces(
        // A slide that starts beside the pill on a window narrower than the controls leaves a negative room.
        pillMaxWidth = lerp(room, lineWidth, cleared).coerceAtLeast(0),
        trailingY = trailingY,
        height = maxOf(if (hasRoomUnder) lineHeight else pillBottom, trailingY + trailingHeight + slack),
    )
}

/**
 * Where a tool's instrument line puts its trailing controls across, at [progress] (0 beside, 1 under) of their slide:
 * at the line's end both beside the pill and under it, or, [isAfterPill], a [gap] after the pill's [pillWidth] beside it
 * and at the line's start under it, travelling between the two as they go down.
 */
internal fun toolLineTrailingX(
    progress: Float,
    lineWidth: Int,
    pillWidth: Int,
    gap: Int,
    trailingWidth: Int,
    isAfterPill: Boolean,
): Int = if (isAfterPill) lerp(pillWidth + gap, 0, progress.coerceIn(0f, 1f)) else lineWidth - trailingWidth
