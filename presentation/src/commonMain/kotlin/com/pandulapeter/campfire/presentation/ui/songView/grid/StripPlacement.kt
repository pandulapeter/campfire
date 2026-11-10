/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView.grid

import com.pandulapeter.campfire.presentation.ui.songView.SongRows
import com.pandulapeter.campfire.presentation.ui.songView.model.SongUnits
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection

/**
 * Places the units of a strip ([SectionGrid.isStrip]), measured [unitHeights] tall at the [widths] they were given: its
 * columns side by side from the song's start edge, each column's sections stacked from the top as a cell's are (see
 * [arrange]), nothing spread out or centered, since the eye looks for the next column's first line where the last one's
 * was. The layout is as wide as the strip, which scrolls sideways in a viewport [width] wide.
 *
 * What a [columnGapPx] at the song's reading end of the viewport, and the step buttons where [isInset] ([endInsetPx], at
 * the screen's end, the left where [isInsetAtLeft], as for [placeSections]), leave of it, and a gap past it, is shared out into one slot for every column on screen, each
 * column centered in its own less that gap: the columns in view fill it exactly, a gap apart, and the ones beside them
 * start past its edge rather than in its margins. A column wider than a slot takes as many whole slots as it needs,
 * centered in them, so the columns in view still fill it and the next one starts past its edge. A step scrolls the strip by the slot of the column leaving, and the strip ends at the first stop that shows its last
 * column, so the last step is never one that shows nothing new. Where the stops are is [SectionPlacement.columnStops],
 * one a column up to that last one, counted from the song's start edge, which a scroll reversed for a song read
 * leftwards counts from too.
 */
internal fun placeStrip(
    grid: SectionGrid,
    widths: SectionWidths,
    unitHeights: IntArray,
    units: SongUnits,
    piecePadding: IntArray,
    width: Int,
    isInset: Boolean,
    endInsetPx: Int,
    columnGapPx: Int,
    sectionGapPx: Int,
    rowGapPx: Int,
    layoutDirection: LayoutDirection,
    isInsetAtLeft: Boolean,
): SectionPlacement {
    val columnWidths = widths.slotWidths.firstOrNull() ?: IntArray(0)
    val readsLeftwards = layoutDirection == LayoutDirection.Rtl
    // The buttons are at the end of the screen, which is the song's start where the two read in opposite directions. The
    // gap the screen fades the strip out over (`COLUMN_GAP`) is always at its reading end, where the next column comes in.
    val buttonInset = if (isInset) endInsetPx else 0
    val lead = if (isInsetAtLeft != readsLeftwards) buttonInset else 0
    val viewWidth = (width - buttonInset - columnGapPx).coerceAtLeast(0)
    val slotWidth = (viewWidth + columnGapPx) / grid.stripColumns.coerceAtLeast(1)
    val slots = IntArray(columnWidths.size) { column ->
        val needed = columnWidths[column] + columnGapPx
        if (slotWidth <= 0) maxOf(slotWidth, needed) else slotWidth * ((needed + slotWidth - 1) / slotWidth).coerceAtLeast(1)
    }
    val offsets = IntArray(slots.size) { ((slots[it] - columnGapPx - columnWidths[it]) / 2).coerceAtLeast(0) }
    val slotStarts = IntArray(slots.size)
    for (column in 1 until slots.size) slotStarts[column] = slotStarts[column - 1] + slots[column - 1]
    val contentEnd = slots.indices.maxOfOrNull { slotStarts[it] + offsets[it] + columnWidths[it] } ?: 0
    val lastStop = slotStarts.indexOfFirst { it + viewWidth >= contentEnd }.takeIf { it >= 0 } ?: slotStarts.lastIndex
    val columnStops = slotStarts.take(lastStop + 1)
    // The strip scrolls no further than its last stop, whose view holds everything after it.
    val totalWidth = width + (columnStops.lastOrNull() ?: 0)
    val arrangement = grid.arrange(
        heights = unitHeights,
        sectionGap = sectionGapPx,
        rowGap = rowGapPx,
        unitSections = units.unitSections,
        piecePadding = piecePadding,
    )
    val positions = Array(unitHeights.size) { index ->
        val column = grid.columns[index]
        val start = lead + slotStarts[column] + offsets[column]
        // What is narrower than its column sits at the column's start, which is its right edge in a song read leftwards.
        IntOffset(x = if (readsLeftwards) totalWidth - start - widths.unitWidths[index] else start, y = arrangement.tops[index])
    }
    return SectionPlacement(
        isScrolledByRow = false,
        isPaddedToViewport = false,
        arrangement = arrangement,
        positions = positions,
        cards = cardBoundsOf(grid, units, positions, unitHeights, widths.unitWidths, piecePadding),
        width = totalWidth,
        columnStops = columnStops,
    )
}

/**
 * The stops of a strip placed at [columnStops], one a column from the first, each named by the section its column starts
 * with, which a scroll reversed for a song that [readsLeftwards] counts from its start edge too.
 */
internal fun stripRowsOf(grid: SectionGrid, units: SongUnits, columnStops: List<Int>, readsLeftwards: Boolean) = SongRows(
    restingOffsets = columnStops,
    // A column is never taller than the screen, so a stop shows all of it.
    bottoms = columnStops,
    stepOffsets = columnStops,
    stepSections = columnStops.indices.map { column -> units.unitSections[grid.columns.indexOfFirst { it == column }] },
    isSteppedByRow = true,
    timingSections = units.timingSections,
    isAcross = true,
    readsLeftwards = readsLeftwards,
)
