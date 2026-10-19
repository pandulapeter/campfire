/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.scales.Scale
import com.pandulapeter.campfire.presentation.ui.chords.SCALE_CHIPS_TITLE_GAP
import com.pandulapeter.campfire.presentation.ui.chords.ScaleChip
import com.pandulapeter.campfire.presentation.ui.chords.ScaleChipsTitle
import com.pandulapeter.campfire.presentation.ui.components.controls.CHIP_GAP
import com.pandulapeter.campfire.presentation.ui.songLayout.greedyRowStarts
import com.pandulapeter.campfire.presentation.ui.songLayout.slotRows
import com.pandulapeter.campfire.presentation.ui.songView.model.RenderSection

/**
 * The rows of the chips of [scales] on [rootName] that its [slots] hold at the width they are measured at, under [title] where there is one:
 * the chips in their order, as many to a row as fit, one slot per row the Scales section may be cut between (see
 * [RenderSection.Scales]), as the Chords section's diagrams are. Every chip is composed in every chunk,
 * since which row a chip falls in depends on that width and the section is measured at several before one wins (see
 * [SongSectionsLayout]); the chips of the rows other chunks hold are left unplaced, so they are neither drawn, tapped,
 * nor read. Its intrinsic heights count the rows the chips wrap into at the width asked for, which is what the grid
 * measures a chunk by.
 *
 * Every row but the last is followed by a chip gap and the last by [trailingGap], so that a cut leaves the gap at
 * the bottom of a page rather than at the top of the next.
 */
@Composable
internal fun ScaleChipRows(
    modifier: Modifier = Modifier,
    title: String?,
    rootName: String,
    scales: List<Scale>,
    slots: IntRange,
    trailingGap: Dp,
    notation: ChordNotation,
    onScaleSelected: (scaleId: String, rootName: String) -> Unit,
) = Layout(
    modifier = modifier,
    content = {
        if (title != null) {
            ScaleChipsTitle(
                title = title,
                textAlign = TextAlign.Start,
            )
        }
        scales.forEach { scale ->
            ScaleChip(
                scale = scale,
                rootName = rootName,
                notation = notation,
                onScaleSelected = onScaleSelected,
            )
        }
    },
    measurePolicy = remember(title != null, slots, trailingGap) {
        ScaleChipRowsMeasurePolicy(hasTitle = title != null, slots = slots, trailingGap = trailingGap)
    },
)

private class ScaleChipRowsMeasurePolicy(
    private val hasTitle: Boolean,
    private val slots: IntRange,
    private val trailingGap: Dp,
) : MeasurePolicy {

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val chips = measurables.drop(if (hasTitle) 1 else 0).map { it.measure(Constraints(maxWidth = constraints.maxWidth)) }
        val gap = CHIP_GAP.roundToPx()
        // A layout refuses an unbounded size, so the rows are as wide as the chips on one line there.
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else (chips.sumOf { it.width } + gap * (chips.size - 1)).coerceAtLeast(0)
        val starts = greedyRowStarts(FloatArray(chips.size) { chips[it].width.toFloat() }, gap.toFloat(), width.toFloat())
        val rows = slotRows(starts.size, slots.first, slots.last, isLastSlot = false)
        val title = if (hasTitle && !rows.isEmpty()) measurables.first().measure(Constraints(maxWidth = width)) else null
        val height = rowsHeight(starts, rows, chips.map { it.height }, title?.height)
        return layout(width = width, height = height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            var y = 0
            if (title != null) {
                title.placeRelative(0, 0)
                y += title.height + SCALE_CHIPS_TITLE_GAP.roundToPx()
            }
            for (row in rows) {
                var x = 0
                val cells = starts[row] until (starts.getOrNull(row + 1) ?: chips.size)
                for (cell in cells) {
                    chips[cell].placeRelative(x, y)
                    x += chips[cell].width + gap
                }
                y += cells.maxOf { chips[it].height } + gap
            }
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) =
        measurables.maxOfOrNull { it.minIntrinsicWidth(height) } ?: 0

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int {
        val chips = measurables.drop(if (hasTitle) 1 else 0)
        val line = chips.sumOf { it.maxIntrinsicWidth(height) } + CHIP_GAP.roundToPx() * (chips.size - 1).coerceAtLeast(0)
        return maxOf(line, if (hasTitle) measurables.first().maxIntrinsicWidth(height) else 0)
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) =
        intrinsicHeight(measurables, width)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) =
        intrinsicHeight(measurables, width)

    /** The height [measure] arrives at for [width], from the chips' natural widths as they are there. */
    private fun IntrinsicMeasureScope.intrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int {
        val chips = measurables.drop(if (hasTitle) 1 else 0)
        val widths = chips.map { minOf(it.maxIntrinsicWidth(Constraints.Infinity), width) }
        val starts = greedyRowStarts(FloatArray(chips.size) { widths[it].toFloat() }, CHIP_GAP.roundToPx().toFloat(), width.toFloat())
        val rows = slotRows(starts.size, slots.first, slots.last, isLastSlot = false)
        val titleHeight = if (hasTitle && !rows.isEmpty()) measurables.first().maxIntrinsicHeight(width) else null
        return rowsHeight(starts, rows, chips.indices.map { chips[it].maxIntrinsicHeight(widths[it]) }, titleHeight)
    }

    /**
     * How tall [rows] of the rows starting at [starts] are, of chips [chipHeights] tall: each as tall as its tallest chip,
     * under a title [titleHeight] tall where there is one, and each followed by its gap. Nothing where there are no rows.
     */
    private fun IntrinsicMeasureScope.rowsHeight(starts: IntArray, rows: IntRange, chipHeights: List<Int>, titleHeight: Int?): Int {
        if (rows.isEmpty()) return 0
        val gap = CHIP_GAP.roundToPx()
        val rowHeights = rows.sumOf { row -> (starts[row] until (starts.getOrNull(row + 1) ?: chipHeights.size)).maxOf { chipHeights[it] } }
        val gaps = gap * (rows.last - rows.first) + if (rows.last < starts.size - 1) gap else trailingGap.roundToPx()
        return (titleHeight?.let { it + SCALE_CHIPS_TITLE_GAP.roundToPx() } ?: 0) + rowHeights + gaps
    }
}
