/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import kotlin.math.roundToInt

/**
 * [chips] around the [frame] on a canvas as large as this layout, never past its edges, less [inset]. Where the canvas
 * leaves room beside the frame for a chip on one line, it stands there, at the height of the frame's top or
 * bottom, covering nothing. Elsewhere it sits across the frame's top or bottom edge, half outside it, its outer end as
 * near the canvas's side as the inset allows, so that it covers as little of the screen as it can. A chip alone on its
 * edge may take nearly all of it; two on one edge keep their natural widths where both fit, and where they do not, the
 * shorter keeps its own up to half the edge and the longer wraps in what is left.
 */
@Composable
internal fun ListingChips(
    modifier: Modifier = Modifier,
    chips: List<ListingChip>,
    fontSize: Dp,
    frame: DpRect,
    inset: Dp,
    isLight: Boolean,
) = Layout(
    modifier = modifier,
    content = { chips.forEach { ListingChipPill(chip = it, fontSize = fontSize, isLight = isLight) } },
) { measurables, constraints ->
    val insetPx = inset.roundToPx()
    val left = frame.left.roundToPx()
    val right = frame.right.roundToPx()
    val top = frame.top.roundToPx()
    val bottom = frame.bottom.roundToPx()
    val beside = left - insetPx * 2
    val isBeside = BooleanArray(chips.size) { measurables[it].maxIntrinsicWidth(Int.MAX_VALUE) <= beside }
    val widths = IntArray(chips.size) { if (isBeside[it]) beside else 0 }
    val edge = constraints.maxWidth - insetPx * 2
    listOf(true, false).forEach { isTop ->
        val onEdge = chips.indices.filter { chips[it].corner.isTop == isTop && !isBeside[it] }
        when (onEdge.size) {
            1 -> widths[onEdge[0]] = (edge * ALONE_SHARE).roundToInt()
            2 -> sharedWidths(chips = chips, onEdge = onEdge, measurables = measurables, available = edge - (edge * CHIP_GAP).roundToInt(), widths = widths)
        }
    }
    // A chip is never narrower than its longest word, even where that takes it past its share of an edge.
    val placeables = measurables.mapIndexed { index, measurable ->
        val width = maxOf(widths[index], measurable.minIntrinsicWidth(Int.MAX_VALUE))
        measurable.measure(Constraints(maxWidth = balancedWidth(measurable, width)))
    }
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeables.forEachIndexed { index, placeable ->
            val corner = chips[index].corner
            val x = when {
                isBeside[index] && corner.isStart -> left - insetPx - placeable.width
                isBeside[index] -> right + insetPx
                corner.isStart -> insetPx
                else -> constraints.maxWidth - insetPx - placeable.width
            }
            val y = when {
                isBeside[index] && corner.isTop -> top
                isBeside[index] -> bottom - placeable.height
                corner.isTop -> top - placeable.height / 2
                else -> bottom - placeable.height / 2
            }
            placeable.place(
                x = x.coerceIn(insetPx, constraints.maxWidth - insetPx - placeable.width),
                y = y.coerceIn(insetPx, constraints.maxHeight - insetPx - placeable.height),
            )
        }
    }
}

/**
 * Splits [available] between the two of [chips] whose indices are [onEdge]: the shorter keeps its natural width up to
 * half of it and the longer wraps in the rest, but neither is narrower than its longest word, so a chip of one long word
 * takes the room it needs and its neighbour wraps instead.
 */
private fun sharedWidths(chips: List<ListingChip>, onEdge: List<Int>, measurables: List<Measurable>, available: Int, widths: IntArray) {
    val (short, long) = onEdge.sortedBy { measurables[it].maxIntrinsicWidth(Int.MAX_VALUE) }
    val longest = { index: Int -> measurables[index].minIntrinsicWidth(Int.MAX_VALUE) }
    widths[short] = minOf(measurables[short].maxIntrinsicWidth(Int.MAX_VALUE), maxOf(available / 2, longest(short)))
    // Where the longer one's longest word does not fit beside it, the shorter one wraps too, down to its own longest word.
    if (widths[short] + longest(long) > available) widths[short] = maxOf(longest(short), available - longest(long))
    widths[long] = maxOf(available - widths[short], longest(long))
    if (widths[short] + widths[long] > available) {
        // The two cannot help overlapping: a word of one of them is too long to share the edge, which only shorter words fix.
        println("warning: \"${chips[short].text}\" and \"${chips[long].text}\" overlap, a word of theirs being too long for the edge")
    }
}

/**
 * The narrowest width up to [width], but no narrower than its longest word, at which [measurable] is as short as at
 * [width] itself, found by halving: where its text wraps, that evens out its lines ("Counts / down to / the gig" rather
 * than "Counts / down to the / gig"), since Compose's balanced line breaking does nothing on the desktop.
 */
private fun balancedWidth(measurable: Measurable, width: Int): Int {
    val height = measurable.minIntrinsicHeight(width)
    var narrowest = width
    // Below its longest word a chip would break that word in two, so its minimum intrinsic width is as narrow as it goes.
    var tooNarrow = (measurable.minIntrinsicWidth(Int.MAX_VALUE) - 1).coerceIn(0, width)
    while (narrowest - tooNarrow > 1) {
        val middle = (narrowest + tooNarrow) / 2
        if (measurable.minIntrinsicHeight(middle) <= height) narrowest = middle else tooNarrow = middle
    }
    return narrowest
}

/** How much of the canvas's width a chip alone on its edge may take. */
private const val ALONE_SHARE = 0.9f

/** The room left between two chips on one edge, as a share of the canvas's width. */
private const val CHIP_GAP = 0.03f
