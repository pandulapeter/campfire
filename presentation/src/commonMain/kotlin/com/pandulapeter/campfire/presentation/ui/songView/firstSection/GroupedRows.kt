/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView.firstSection

import com.pandulapeter.campfire.presentation.ui.songView.PlacementMotion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.MultiContentMeasurePolicy
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset

/**
 * The items of [groups] in lines, a group kept whole on one line wherever it fits on one: the instrument and tuning pill,
 * the key and the capo, the tempo and the time signature. A group goes on after the one before it where the line has
 * room for all of it, [groupGap] apart, and starts a line of its own where it does not; one too wide for any line has its
 * items flowed over as many lines as it takes, [gap] apart, and the next group starts under it rather than among its
 * items, so that a line never holds the end of one group and the start of another. A group with nothing in it takes no
 * room. Every line is at least [minRowHeight] tall, a pill's height, so that an item with no pill (the time signature,
 * which is only read) takes as much room on a line of its own as the controls do; a line holding a taller item (a
 * control whose label stands above it) grows for it alone. Items are centered vertically in their lines.
 *
 * Every item is measured once, at the whole width, and drawn at its natural width.
 *
 * Where [isAnimated], an item the lines move — a stepper that widened, the text grown a step, the lines rearranged —
 * springs from where it was to its new place, on the spring the song's sections move on, so the controls travel together
 * with the section that holds them. An item is followed by its `layoutId` where it has one, so that one appearing or
 * going does not send the others flying from each other's places; one that appears is simply placed.
 */
@Composable
internal fun GroupedRows(
    modifier: Modifier = Modifier,
    gap: Dp,
    groupGap: Dp,
    minRowHeight: Dp,
    isAnimated: Boolean,
    groups: List<@Composable () -> Unit>,
) {
    val coroutineScope = rememberCoroutineScope()
    val motion = remember(coroutineScope) { PlacementMotion(coroutineScope) }
    Layout(
        contents = groups,
        modifier = modifier,
        measurePolicy = GroupedRowsMeasurePolicy(gap = gap, groupGap = groupGap, minRowHeight = minRowHeight, motion = motion, isAnimated = isAnimated),
    )
}

/**
 * Draws an item [GroupedRows] lays out [distance] further towards the start than it is placed, taking that much less of
 * the line: the instrument pill, which reaches out of the controls' keyline as far as the section pills do, so that its
 * edge lines up with theirs, while what follows it on its line keeps the usual gap.
 */
internal fun Modifier.reachingOutBy(distance: Dp) = layout { measurable, constraints ->
    val distancePx = distance.roundToPx()
    val placeable = measurable.measure(
        if (constraints.hasBoundedWidth) constraints.copy(maxWidth = constraints.maxWidth + distancePx) else constraints,
    )
    layout((placeable.width - distancePx).coerceAtLeast(0), placeable.height) {
        placeable.placeRelative(-distancePx, 0)
    }
}

private class GroupedRowsMeasurePolicy(
    private val gap: Dp,
    private val groupGap: Dp,
    private val minRowHeight: Dp,
    private val motion: PlacementMotion,
    private val isAnimated: Boolean,
) : MultiContentMeasurePolicy {

    override fun MeasureScope.measure(measurables: List<List<Measurable>>, constraints: Constraints): MeasureResult {
        val gapPx = gap.roundToPx()
        val groupGapPx = groupGap.roundToPx()
        val placeables = measurables.map { group -> group.map { it.measure(Constraints(maxWidth = constraints.maxWidth)) } }
        val lines = groupedLines(
            groupWidths = placeables.map { group -> group.map { it.width } },
            maxWidth = constraints.maxWidth,
            gap = gapPx,
            groupGap = groupGapPx,
        )
        val lineHeights = lines.map { line -> maxOf(line.maxOf { placeables[it.group][it.index].height }, minRowHeight.roundToPx()) }
        val lineWidths = lines.map { line -> lineWidth(line, placeables.map { group -> group.map { it.width } }, gapPx, groupGapPx) }
        val width = (lineWidths.maxOrNull() ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (lineHeights.sum() + gapPx * (lines.size - 1).coerceAtLeast(0)).coerceAtLeast(constraints.minHeight)
        // The lookahead pass of the song's sections is where they are headed, so the items are placed there at once.
        val animatedMotion = motion.takeIf { isAnimated && !isLookingAhead }
        val keys = measurables.mapIndexed { group, items -> items.mapIndexed { index, item -> item.layoutId ?: GroupedItem(group, index) } }
        if (!isAnimated) motion.clear() else if (!isLookingAhead) motion.retain(keys.flatten().toSet())
        return layout(width, height) {
            var y = 0
            lines.forEachIndexed { lineIndex, line ->
                var x = 0
                line.forEachIndexed { position, item ->
                    if (position > 0) x += if (line[position - 1].group == item.group) gapPx else groupGapPx
                    val placeable = placeables[item.group][item.index]
                    val target = IntOffset(x, y + (lineHeights[lineIndex] - placeable.height) / 2)
                    if (animatedMotion == null) {
                        placeable.placeRelative(target)
                    } else with(animatedMotion) {
                        place(placeable = placeable, key = keys[item.group][item.index], target = target)
                    }
                    x += placeable.width
                }
                y += lineHeights[lineIndex] + gapPx
            }
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<List<IntrinsicMeasurable>>, height: Int) =
        measurables.flatten().maxOfOrNull { it.minIntrinsicWidth(height) } ?: 0

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<List<IntrinsicMeasurable>>, height: Int): Int {
        val widths = measurables.map { group -> group.map { it.maxIntrinsicWidth(height) } }
        val line = widths.flatMapIndexed { group, items -> items.indices.map { GroupedItem(group, it) } }
        return lineWidth(line, widths, gap.roundToPx(), groupGap.roundToPx())
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<List<IntrinsicMeasurable>>, width: Int) =
        intrinsicHeight(measurables, width) { measurable, itemWidth -> measurable.minIntrinsicHeight(itemWidth) }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<List<IntrinsicMeasurable>>, width: Int) =
        intrinsicHeight(measurables, width) { measurable, itemWidth -> measurable.maxIntrinsicHeight(itemWidth) }

    /**
     * The height [measure] arrives at for [width], which the song's grid asks for at every column width it tries: the
     * lines are decided from the items' natural widths as they are there.
     */
    private fun IntrinsicMeasureScope.intrinsicHeight(
        measurables: List<List<IntrinsicMeasurable>>,
        width: Int,
        heightOf: (IntrinsicMeasurable, Int) -> Int,
    ): Int {
        val gapPx = gap.roundToPx()
        val widths = measurables.map { group -> group.map { it.maxIntrinsicWidth(Constraints.Infinity) } }
        val lines = groupedLines(groupWidths = widths, maxWidth = width, gap = gapPx, groupGap = groupGap.roundToPx())
        if (lines.isEmpty()) return 0
        val minRowHeightPx = minRowHeight.roundToPx()
        val lineHeights = lines.map { line ->
            maxOf(line.maxOf { heightOf(measurables[it.group][it.index], minOf(widths[it.group][it.index], width)) }, minRowHeightPx)
        }
        return lineHeights.sum() + gapPx * (lines.size - 1)
    }
}

/** One item of [GroupedRows]: the [index]th of the [group]th group. */
internal data class GroupedItem(
    val group: Int,
    val index: Int,
)

/**
 * The lines [GroupedRows] puts the items in within [maxWidth], [groupWidths] being the widths of each group's items, in
 * the order they are read in.
 */
internal fun groupedLines(groupWidths: List<List<Int>>, maxWidth: Int, gap: Int, groupGap: Int): List<List<GroupedItem>> {
    val lines = mutableListOf<List<GroupedItem>>()
    var line = mutableListOf<GroupedItem>()
    var lineWidth = 0
    fun endLine() {
        if (line.isNotEmpty()) lines += line
        line = mutableListOf()
        lineWidth = 0
    }
    groupWidths.forEachIndexed { group, widths ->
        if (widths.isEmpty()) return@forEachIndexed
        val items = widths.indices.map { GroupedItem(group, it) }
        val groupWidth = widths.sum() + gap * (widths.size - 1)
        when {
            line.isNotEmpty() && lineWidth + groupGap + groupWidth <= maxWidth -> {
                line += items
                lineWidth += groupGap + groupWidth
            }

            groupWidth <= maxWidth -> {
                endLine()
                line += items
                lineWidth = groupWidth
            }

            else -> {
                endLine()
                items.forEach { item ->
                    val width = widths[item.index]
                    if (line.isNotEmpty() && lineWidth + gap + width <= maxWidth) {
                        line += item
                        lineWidth += gap + width
                    } else {
                        endLine()
                        line += item
                        lineWidth = width
                    }
                }
                endLine()
            }
        }
    }
    endLine()
    return lines
}

/** How wide [line] is, its items being as wide as [groupWidths] says, [gap] apart within a group and [groupGap] between. */
private fun lineWidth(line: List<GroupedItem>, groupWidths: List<List<Int>>, gap: Int, groupGap: Int) =
    line.foldIndexed(0) { position, width, item ->
        width + groupWidths[item.group][item.index] + when {
            position == 0 -> 0
            line[position - 1].group == item.group -> gap
            else -> groupGap
        }
    }
