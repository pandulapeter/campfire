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

import androidx.compose.animation.animateContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.MultiContentMeasurePolicy
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
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
    val budgets = if (availableHeight == Dp.Infinity) {
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
        val maxHeight = if (measurable.layoutId == FilterSlot.EXPANDED_CHIPS) null else budgets?.get(slot)
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
 * A group of filter chips flowed onto as many lines as the height it is given holds, with [toggle] under them once
 * they do not all fit: the lines are counted from the room [FilterGroupsLayout] hands out rather than from a fixed
 * number of chips, so a wide panel shows a whole library of short tags and a phone shows fewer long ones.
 *
 * Collapsed, the chips shown are the first ones in their order and every one [isPinned] marks: a selected value has
 * to be visible to be turned off, wherever it is in the order. The rest are composed but not placed, which keeps them
 * off the screen, out of the semantics and out of the focus order.
 *
 * Its intrinsic heights are what the sharing is decided by, and they are the collapsed group's whatever [isExpanded]
 * says: the max is every chip, the min [MIN_COLLAPSED_LINES] lines, the pinned chips and the toggle.
 *
 * @param isExpanded Every chip and the toggle, however much room there is: the user asked to see them all.
 * @param isPinned One value per chip, in the order the chips are composed in.
 * @param horizontalPadding Between the chips and the edges; the toggle is placed at the start edge and pads itself.
 */
@Composable
internal fun CollapsibleChipFlow(
    modifier: Modifier = Modifier,
    isExpanded: Boolean,
    isPinned: List<Boolean>,
    horizontalPadding: Dp,
    gap: Dp,
    toggle: @Composable () -> Unit,
    chips: @Composable () -> Unit,
) {
    val measurePolicy = remember(isExpanded, isPinned, horizontalPadding, gap) {
        CollapsibleChipFlowMeasurePolicy(
            isExpanded = isExpanded,
            isPinned = isPinned,
            horizontalPadding = horizontalPadding,
            gap = gap,
        )
    }
    Layout(
        // The chips past the fold arrive and leave in one step, and so does the toggle, so it is the height of the
        // group that moves rather than everything under it jumping by several lines.
        modifier = modifier.animateContentSize(),
        contents = listOf(chips, toggle),
        measurePolicy = measurePolicy,
    )
}

private class CollapsibleChipFlowMeasurePolicy(
    private val isExpanded: Boolean,
    private val isPinned: List<Boolean>,
    private val horizontalPadding: Dp,
    private val gap: Dp,
) : MultiContentMeasurePolicy {

    override fun MeasureScope.measure(measurables: List<List<Measurable>>, constraints: Constraints): MeasureResult {
        val (chipMeasurables, toggleMeasurables) = measurables
        val padding = horizontalPadding.roundToPx()
        val gap = gap.roundToPx()
        val width = constraints.maxWidth
        val lineWidth = (width - 2 * padding).coerceAtLeast(0)
        val chips = chipMeasurables.map { it.measure(Constraints(maxWidth = lineWidth)) }
        val toggle = toggleMeasurables.firstOrNull()?.measure(Constraints(maxWidth = width))
        val widths = chips.map { it.width }
        val lineHeight = chips.maxOfOrNull { it.height } ?: 0
        val toggleHeight = toggle?.height ?: 0
        fun heightOf(lineCount: Int) = flowHeight(lineCount = lineCount, lineHeight = lineHeight, gap = gap)
        val shown = when {
            isExpanded -> chips.indices.toList()
            heightOf(chipLineCount(widths, lineWidth, gap)) <= constraints.maxHeight -> chips.indices.toList()
            else -> collapsedChips(
                widths = widths,
                isPinned = isPinned,
                lineWidth = lineWidth,
                gap = gap,
                maxLines = ((constraints.maxHeight - toggleHeight) / (lineHeight + gap).coerceAtLeast(1)).coerceAtLeast(1),
            )
        }
        val isToggleShown = isExpanded || shown.size < chips.size
        val lines = chipLines(shown.map { widths[it] }, lineWidth, gap)
        val chipsHeight = heightOf(lines.lastOrNull()?.plus(1) ?: 0)
        val height = chipsHeight + if (isToggleShown) toggleHeight else 0
        return layout(width, constraints.constrainHeight(height)) {
            var line = -1
            var x = 0
            shown.forEachIndexed { position, index ->
                if (lines[position] != line) {
                    line = lines[position]
                    x = 0
                }
                chips[index].placeRelative(padding + x, line * (lineHeight + gap))
                x += chips[index].width + gap
            }
            if (isToggleShown) {
                toggle?.placeRelative(0, chipsHeight)
            }
        }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<List<IntrinsicMeasurable>>, width: Int): Int {
        val (chips, _) = measurables
        val gap = gap.roundToPx()
        val lineWidth = (width - 2 * horizontalPadding.roundToPx()).coerceAtLeast(0)
        val widths = chips.map { it.maxIntrinsicWidth(Constraints.Infinity).coerceAtMost(lineWidth) }
        return flowHeight(
            lineCount = chipLineCount(widths, lineWidth, gap),
            lineHeight = chips.maxOfOrNull { it.maxIntrinsicHeight(lineWidth) } ?: 0,
            gap = gap,
        )
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<List<IntrinsicMeasurable>>, width: Int): Int {
        val (chips, toggle) = measurables
        val gap = gap.roundToPx()
        val lineWidth = (width - 2 * horizontalPadding.roundToPx()).coerceAtLeast(0)
        val widths = chips.map { it.maxIntrinsicWidth(Constraints.Infinity).coerceAtMost(lineWidth) }
        val lineHeight = chips.maxOfOrNull { it.maxIntrinsicHeight(lineWidth) } ?: 0
        val lineCount = chipLineCount(widths, lineWidth, gap)
        val pinnedLineCount = chipLineCount(widths.filterIndexed { index, _ -> isPinned.getOrElse(index) { false } }, lineWidth, gap)
        val collapsedLineCount = maxOf(MIN_COLLAPSED_LINES, pinnedLineCount)
        val natural = flowHeight(lineCount = lineCount, lineHeight = lineHeight, gap = gap)
        return if (lineCount <= collapsedLineCount) {
            natural
        } else {
            val collapsed = flowHeight(lineCount = collapsedLineCount, lineHeight = lineHeight, gap = gap) +
                (toggle.firstOrNull()?.maxIntrinsicHeight(width) ?: 0)
            minOf(collapsed, natural)
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<List<IntrinsicMeasurable>>, height: Int) =
        2 * horizontalPadding.roundToPx() + (measurables.first().maxOfOrNull { it.minIntrinsicWidth(Constraints.Infinity) } ?: 0)

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<List<IntrinsicMeasurable>>, height: Int): Int {
        val chips = measurables.first()
        return 2 * horizontalPadding.roundToPx() + chips.sumOf { it.maxIntrinsicWidth(Constraints.Infinity) } +
            (chips.size - 1).coerceAtLeast(0) * gap.roundToPx()
    }
}

/**
 * The height of [lineCount] lines of chips, with a [gap] between the lines and one more under the last: the room
 * between the chips and whatever comes after them is the same as between two of their lines.
 */
private fun flowHeight(lineCount: Int, lineHeight: Int, gap: Int) =
    if (lineCount == 0) 0 else lineCount * lineHeight + lineCount * gap

/**
 * The line each chip of the given [widths] is laid out on when they are flowed into lines of [lineWidth], the way
 * `FlowRow` does it: a chip goes on the current line if it fits there after a [gap], and starts the next one if it
 * does not. A chip wider than a whole line still gets one of its own.
 */
internal fun chipLines(widths: List<Int>, lineWidth: Int, gap: Int): List<Int> {
    var line = -1
    var used = 0
    return widths.map { width ->
        if (line < 0 || used + gap + width > lineWidth) {
            line++
            used = width
        } else {
            used += gap + width
        }
        line
    }
}

/** How many lines [chipLines] lays the given [widths] out on. */
internal fun chipLineCount(widths: List<Int>, lineWidth: Int, gap: Int) = chipLines(widths, lineWidth, gap).lastOrNull()?.plus(1) ?: 0

/**
 * The chips a collapsed group shows on at most [maxLines] lines: the longest run from the start of the order that
 * fits there together with every pinned chip, in their order. The pinned ones are shown even where they alone take
 * more lines than that, since a selected filter has to be visible to be turned off; [CollapsibleChipFlow] reports
 * enough height for them as its minimum, so the room it is given holds them.
 */
internal fun collapsedChips(
    widths: List<Int>,
    isPinned: List<Boolean>,
    lineWidth: Int,
    gap: Int,
    maxLines: Int,
): List<Int> {
    fun shown(prefix: Int) = widths.indices.filter { it < prefix || isPinned.getOrElse(it) { false } }
    // Adding a chip to a flow never takes a line away from it, so the first run that does not fit ends the search.
    var prefix = 0
    while (prefix < widths.size && chipLineCount(shown(prefix + 1).map { widths[it] }, lineWidth, gap) <= maxLines) {
        prefix++
    }
    return shown(prefix)
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

/** The fewest lines a collapsed group is cut down to, however little room there is: one line reads as a stray row. */
private const val MIN_COLLAPSED_LINES = 2
