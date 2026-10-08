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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp

/**
 * What a child of [FilterGroupsLayout] is to the way the height is shared out. A child with no id is laid out at its
 * own height and counted against the room the chips have.
 */
internal enum class FilterSlot {

    /** A [CollapsibleChipFlow] that shows as much of itself as its share of the room holds. */
    CHIPS,

    /** A [CollapsibleChipFlow] the user asked to see whole: it takes what it needs and the content scrolls. */
    EXPANDED_CHIPS,

    /**
     * Laid out at its own height but left out of the sharing: what comes and goes with the selection (the "any /
     * every" choice) would otherwise take its height out of the chips, hiding some of them under the finger that
     * just selected one.
     */
    TRANSIENT,
}

/**
 * The filter groups of the song list, one under the other, sharing [availableHeight] between their chips so that
 * every group shows as much of itself as fits on screen at once rather than the first one pushing the next out of
 * sight. Everything that is not a [FilterSlot.CHIPS] child is measured first; the chips get the rest, split by
 * [chipBudgets] from what each would take whole and what it takes collapsed as far as it goes. Those two are asked
 * of the chips as their intrinsic heights, which is what lets one measure pass decide it: the children's sizes are
 * known before any of them is measured.
 *
 * Each [FilterSlot.EXPANDED_CHIPS] child is shared out as if it were collapsed, so that opening one group does not
 * close the others, and then measured whole.
 *
 * The room is only shared out in the lookahead pass, which is where each group decides which of its chips it shows: it
 * is meant to be laid out inside the [LookaheadScope] its [CollapsibleChipFlow]s animate in. In the approach pass a
 * group reports the height it is animating through, and the layout is as tall as their sum, so the groups under one
 * that opens or closes, and the sheet around them, follow it.
 *
 * @param availableHeight The height of what the groups are shown in, without its padding; [Dp.Infinity] where it is
 *   unbounded, in which case every group is shown whole.
 */
@Composable
internal fun FilterGroupsLayout(
    modifier: Modifier = Modifier,
    availableHeight: Dp,
    content: @Composable () -> Unit,
) = Layout(
    modifier = modifier,
    content = content,
) { measurables, constraints ->
    val width = constraints.maxWidth
    val fixedConstraints = Constraints(maxWidth = width)
    val placeables = arrayOfNulls<Placeable>(measurables.size)
    var fixedHeight = 0
    val chipSlots = mutableListOf<Int>()
    measurables.forEachIndexed { index, measurable ->
        when (measurable.layoutId) {
            FilterSlot.CHIPS, FilterSlot.EXPANDED_CHIPS -> chipSlots += index
            else -> measurable.measure(fixedConstraints).also { placeable ->
                placeables[index] = placeable
                if (measurable.layoutId != FilterSlot.TRANSIENT) {
                    fixedHeight += placeable.height
                }
            }
        }
    }
    // The lookahead pass is where each group decides which chips it shows; in the approach pass a group reports the
    // height it is animating through, and a budget there would cut it off - its animateBounds constrains the animated
    // size to what it is measured with, so a group closing would lose its height in the first frame.
    val budgets = if (availableHeight == Dp.Infinity || !isLookingAhead) {
        null
    } else {
        chipBudgets(
            available = availableHeight.roundToPx() - fixedHeight,
            natural = chipSlots.map { measurables[it].maxIntrinsicHeight(width) },
            minimum = chipSlots.map { measurables[it].minIntrinsicHeight(width) },
        )
    }
    chipSlots.forEachIndexed { slot, index ->
        val measurable = measurables[index]
        val maxHeight = if (isLookingAhead && measurable.layoutId != FilterSlot.EXPANDED_CHIPS) budgets?.get(slot) else null
        placeables[index] = measurable.measure(Constraints(maxWidth = width, maxHeight = maxHeight ?: Constraints.Infinity))
    }
    layout(width, placeables.sumOf { it?.height ?: 0 }) {
        var y = 0
        placeables.forEach { placeable ->
            placeable?.placeRelative(0, y)
            y += placeable?.height ?: 0
        }
    }
}

/**
 * How much height each of a set of chip groups gets out of [available], given how much each would take whole
 * ([natural]) and collapsed as far as it goes ([minimum]): null for every group where they all fit whole. Otherwise
 * the room is shared the way water fills vessels: each group gets its minimum, and what is left is spread evenly over
 * the groups that still want more, a group that wants less than its share leaving the rest to the others. A short
 * group is shown whole next to a long one, and two long ones are cut down to about the same height. Where even the
 * minimums do not fit, every group gets its minimum and the content scrolls.
 */
internal fun chipBudgets(available: Int, natural: List<Int>, minimum: List<Int>): List<Int?> {
    if (natural.sum() <= available) return natural.map { null }
    val wanted = natural.indices.map { (natural[it] - minimum[it]).coerceAtLeast(0) }
    val granted = IntArray(wanted.size)
    var remaining = (available - minimum.sum()).coerceAtLeast(0)
    var left = wanted.size
    wanted.indices.sortedBy { wanted[it] }.forEach { index ->
        granted[index] = minOf(wanted[index], remaining / left)
        remaining -= granted[index]
        left--
    }
    return natural.indices.map { minimum[it] + granted[it] }
}
