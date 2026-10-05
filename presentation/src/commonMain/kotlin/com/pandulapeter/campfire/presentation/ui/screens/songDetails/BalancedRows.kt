/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Its items in rows of equal length: all of them in one row where they fit, otherwise half of them in each of two
 * rows (an even count only), otherwise one per row — never a flow that leaves three of four controls stacked over one
 * pair, which reads as an accident rather than a layout. With two per row the items stand in columns, each as wide as
 * the widest item in it, so the second item of each row starts at the same place. Every row is as tall as the tallest
 * item, wherever it stands, so that an item with no pill (the time signature, which is only read) takes as much room
 * on a line of its own as the controls above it; items are centered vertically in their rows.
 *
 * Every item is measured once, at the whole width: a row of items that fits is drawn at their natural widths, and
 * where none does the one item per row is given the whole width anyway.
 *
 * Where [isAnimated], an item the rows move — a stepper that widened, the text grown a step, the rows rearranged —
 * springs from where it was to its new place, on the spring the song's sections move on, so the controls travel together
 * with the section that holds them. An item is followed by its `layoutId` where it has one, so that one appearing or
 * going does not send the others flying from each other's places; one that appears is simply placed.
 */
@Composable
internal fun BalancedRows(
    modifier: Modifier = Modifier,
    gap: Dp,
    isAnimated: Boolean,
    content: @Composable () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val motion = remember(coroutineScope) { BalancedRowsMotion(coroutineScope) }
    Layout(
        modifier = modifier,
        content = content,
        measurePolicy = BalancedRowsMeasurePolicy(gap = gap, motion = motion, isAnimated = isAnimated),
    )
}

/**
 * Where each item of [BalancedRows] is drawn while it travels, by the item's key. The animations are started from the
 * placement, which is the one place the new positions are known, and read there too, so a frame of the travel only
 * places the items again rather than measuring them.
 */
private class BalancedRowsMotion(private val coroutineScope: CoroutineScope) {

    private val offsets = mutableMapOf<Any, Animatable<IntOffset, *>>()

    fun Placeable.PlacementScope.place(placeable: Placeable, key: Any, target: IntOffset) {
        val offset = offsets.getOrPut(key) { Animatable(target, IntOffset.VectorConverter) }
        if (offset.targetValue != target) {
            coroutineScope.launch {
                offset.animateTo(
                    targetValue = target,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold),
                )
            }
        }
        placeable.placeRelative(offset.value)
    }

    /** Forgets every item that is no longer among [keys], so that one coming back is placed rather than sent travelling. */
    fun retain(keys: Set<Any>) {
        offsets.keys.retainAll(keys)
    }

    /**
     * Forgets every item, while the rows are not animated: a change that keeps coming (a pinch, a window edge dragged) is
     * followed frame by frame, and a travel that started before it would otherwise go on from a place the item has left.
     */
    fun clear() = offsets.clear()
}

private class BalancedRowsMeasurePolicy(
    private val gap: Dp,
    private val motion: BalancedRowsMotion,
    private val isAnimated: Boolean,
) : MeasurePolicy {

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val gapPx = gap.roundToPx()
        val placeables = measurables.map { it.measure(Constraints(maxWidth = constraints.maxWidth)) }
        val columnCount = balancedColumnCount(placeables.map { it.width }, constraints.maxWidth, gapPx)
        val columnWidths = columnWidths(placeables.map { it.width }, columnCount)
        val rows = placeables.chunked(columnCount)
        val rowHeight = placeables.maxOfOrNull { it.height } ?: 0
        val width = (columnWidths.sum() + gapPx * (columnCount - 1)).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (rowHeight * rows.size + gapPx * (rows.size - 1).coerceAtLeast(0)).coerceAtLeast(constraints.minHeight)
        // The lookahead pass of the song's sections is where they are headed, so the items are placed there at once.
        val animatedMotion = motion.takeIf { isAnimated && !isLookingAhead }
        val keys = measurables.mapIndexed { index, measurable -> measurable.layoutId ?: index }
        if (!isAnimated) motion.clear() else if (!isLookingAhead) motion.retain(keys.toSet())
        return layout(width, height) {
            var y = 0
            rows.forEachIndexed { rowIndex, row ->
                var x = 0
                row.forEachIndexed { column, placeable ->
                    val target = IntOffset(x, y + (rowHeight - placeable.height) / 2)
                    if (animatedMotion == null) {
                        placeable.placeRelative(target)
                    } else with(animatedMotion) {
                        place(placeable = placeable, key = keys[rowIndex * columnCount + column], target = target)
                    }
                    x += columnWidths[column] + gapPx
                }
                y += rowHeight + gapPx
            }
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) =
        measurables.maxOfOrNull { it.minIntrinsicWidth(height) } ?: 0

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) =
        measurables.sumOf { it.maxIntrinsicWidth(height) } + gap.roundToPx() * (measurables.size - 1).coerceAtLeast(0)

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) =
        intrinsicHeight(measurables, width) { measurable, itemWidth -> measurable.minIntrinsicHeight(itemWidth) }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) =
        intrinsicHeight(measurables, width) { measurable, itemWidth -> measurable.maxIntrinsicHeight(itemWidth) }

    /**
     * The height [measure] arrives at for [width], which the song's grid asks for at every column width it tries: the
     * rows are decided from the items' natural widths as they are there.
     */
    private fun IntrinsicMeasureScope.intrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
        heightOf: (IntrinsicMeasurable, Int) -> Int,
    ): Int {
        if (measurables.isEmpty()) return 0
        val gapPx = gap.roundToPx()
        val widths = measurables.map { it.maxIntrinsicWidth(Constraints.Infinity) }
        val columnCount = balancedColumnCount(widths, width, gapPx)
        val rowCount = (measurables.size + columnCount - 1) / columnCount
        val rowHeight = measurables.indices.maxOf { index -> heightOf(measurables[index], minOf(widths[index], width)) }
        return rowHeight * rowCount + gapPx * (rowCount - 1)
    }
}

/**
 * How many of the items [widths] describe [BalancedRows] puts in a row within [maxWidth]: all of them, half of them
 * where their count is even and more than two, or one.
 */
internal fun balancedColumnCount(widths: List<Int>, maxWidth: Int, gap: Int): Int {
    val count = widths.size
    if (count <= 1) return 1
    val candidates = listOfNotNull(count, (count / 2).takeIf { count % 2 == 0 && count > 2 })
    return candidates.firstOrNull { columns ->
        columnWidths(widths, columns).sum() + gap * (columns - 1) <= maxWidth
    } ?: 1
}

/** The width of each column when [widths] are laid out [columnCount] to a row: the widest item standing in it. */
private fun columnWidths(widths: List<Int>, columnCount: Int) = List(columnCount) { column ->
    widths.indices.filter { it % columnCount == column }.maxOfOrNull { widths[it] } ?: 0
}
