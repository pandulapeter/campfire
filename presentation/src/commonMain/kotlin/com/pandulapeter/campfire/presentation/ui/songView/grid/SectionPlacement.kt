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
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection

/**
 * How wide every column, cell and unit of a decided grid is, worked out before the units are measured at those widths:
 * [cellWidths] what a cell shows, [slotWidths] the room a row is centered by.
 */
internal class SectionWidths(
    val columnWidths: IntArray,
    val cellWidths: Array<IntArray>,
    val slotWidths: Array<IntArray>,
    val unitWidths: IntArray,
)

/** The widths of [decided] laid out [layoutWidth] wide. */
internal fun SongGridSearch.sectionWidthsOf(decided: DecidedGrid, layoutWidth: Int): SectionWidths {
    val grid = decided.grid
    val unitCount = units.unitSections.size
    val columnWidths = IntArray(unitCount) { grid.columnWidthOf(it, layoutWidth) }
    // A column is only as wide as the widest thing in it, up to the width it was given. Only a card needs its width
    // to decide the grid; everything else wraps at the column's width, which is the same height at any width it fits
    // in, so the widths of the rest are only asked for here, once, for the grid that won.
    val cellWidths = Array(grid.columnCounts.size) { row -> IntArray(grid.columnCounts[row]) }
    for (unit in 0 until unitCount) {
        val row = grid.rows[unit]
        val columnWidth = columnWidths[unit]
        val contentWidth = if (units.cardStarts[units.unitSections[unit]] >= 0) {
            unitWidthFor(unit, columnWidth)
        } else {
            minOf(columnWidth, maxWidthOf(unit))
        }
        cellWidths[row][grid.columns[unit]] = maxOf(cellWidths[row][grid.columns[unit]], contentWidth)
    }
    // The pages of a single column are one column cut into screens, so its lines start at the same edge on all of them.
    if (grid.sharesKeyline) {
        val singleColumnWidth = cellWidths.indices.filter { grid.columnCounts[it] == 1 }.maxOfOrNull { cellWidths[it][0] } ?: 0
        cellWidths.forEachIndexed { row, widths -> if (grid.columnCounts[row] == 1) widths[0] = singleColumnWidth }
    }
    // A row is centered by the widths its columns were given rather than by what they show: what they show depends on
    // where their lines happen to wrap, which jumps back and forth as the text grows, and would move every column of
    // the row sideways with it. A wide row, whose sections are each as wide as they need, is centered by those.
    val slotWidths = Array(grid.columnCounts.size) { row ->
        if (grid.wideRows[row]) cellWidths[row].copyOf() else IntArray(grid.columnCounts[row])
    }
    for (unit in 0 until unitCount) {
        val row = grid.rows[unit]
        if (!grid.wideRows[row]) slotWidths[row][grid.columns[unit]] = maxOf(slotWidths[row][grid.columns[unit]], columnWidths[unit])
    }
    // A card narrower than its cell keeps its own width; everything else takes the cell's, so that its lines, which
    // take their direction from their own content, start at the same edge as the other lines of the cell. In a wide row,
    // where every section has a width of its own, a section is as wide as its widest line instead, every chunk of it
    // alike, so that it can be centered whole.
    val unitWidths = IntArray(unitCount) { unit ->
        val section = units.unitSections[unit]
        when {
            units.cardStarts[section] >= 0 -> unitWidthFor(unit, columnWidths[unit])
            grid.wideRows[grid.rows[unit]] -> unitsOf(section).maxOf { minOf(columnWidths[it], maxWidthOf(it)) }
            else -> cellWidths[grid.rows[unit]][grid.columns[unit]]
        }
    }
    return SectionWidths(columnWidths = columnWidths, cellWidths = cellWidths, slotWidths = slotWidths, unitWidths = unitWidths)
}

/** Where the decided grid puts every unit and every card, and whether the song is scrolled by its rows. */
internal class SectionPlacement(
    val isScrolledByRow: Boolean,
    val isPaddedToViewport: Boolean,
    val arrangement: SongArrangement,
    val positions: Array<IntOffset>,
    val cards: List<Pair<Int, IntRect>>,
)

/**
 * Places the units of [grid], measured [unitHeights] tall at the [widths] they were given, under [dividerHeight] tall
 * dividers: every pixel value is the layout's, [rowViewportHeight] null where the scroll does not rest on the rows.
 */
internal fun placeSections(
    grid: SectionGrid,
    widths: SectionWidths,
    unitHeights: IntArray,
    dividerHeight: Int,
    units: SongUnits,
    piecePadding: IntArray,
    width: Int,
    layoutWidth: Int,
    isInset: Boolean,
    endInsetPx: Int,
    columnGapPx: Int,
    sectionGapPx: Int,
    rowGapPx: Int,
    availableHeightPx: Int,
    rowViewportHeight: Int?,
    rowViewportTopPaddingPx: Int,
    rowViewportBottomPaddingPx: Int,
    layoutDirection: LayoutDirection,
    lineHeightPx: Int,
    heldSpreads: ColumnSpreads?,
    heldPageShifts: Map<Int, Int>?,
): SectionPlacement {
    val slotWidths = widths.slotWidths
    val unitWidths = widths.unitWidths
    val hasSeveralPages = grid.pageCount > 1
    // A single column that is not paged has no rows to tell apart, so it is laid out as one, without dividers or space
    // under it. Rows read across only scroll where they are taller than the screen, or where there are several pages of
    // them, each followed by the rest of the screen. A song of one page that fits the screen is left as it is: space
    // under it would only make it scrollable.
    val isScrolledByRow = (hasSeveralPages || grid.columnCounts.any { it > 1 }) && (
        grid.arrange(unitHeights, sectionGapPx, rowGapPx, unitSections = units.unitSections, piecePadding = piecePadding).height > availableHeightPx ||
            hasSeveralPages && rowViewportHeight != null
        )
    val cellStarts = cellStartsOf(grid, slotWidths, width, layoutWidth, isInset, endInsetPx, columnGapPx, layoutDirection)
    val isPaddedToViewport = isScrolledByRow && rowViewportHeight != null
    val rowViewportHeightPx = if (isPaddedToViewport) rowViewportHeight else 0
    // How much of the viewport a row has with the scroll resting on the divider above it: measured from the row's top
    // rather than from where the scroll rests (the bottom of its divider), and less what the scroll holds below this.
    val readableRowHeight = if (isPaddedToViewport) {
        rowViewportHeightPx - rowGapPx / 2 + (dividerHeight - dividerHeight / 2) - rowViewportBottomPaddingPx
    } else {
        0
    }
    // A song read whole is spread over the room it was decided for, or what the screen shows of it while that is less -
    // the metronome panel or the app bar on their way in - so that spreading it never makes it scroll.
    val wholeSongHeight = if (!isScrolledByRow && rowViewportHeight != null && availableHeightPx > 0) {
        minOf(availableHeightPx, rowViewportHeight - rowViewportTopPaddingPx - rowViewportBottomPaddingPx)
    } else {
        0
    }
    val arrangement = grid.arrange(
        heights = unitHeights,
        sectionGap = sectionGapPx,
        rowGap = rowGapPx,
        // Resting on a divider, half a row gap above its row, the viewport ends half a row gap short of the next row's
        // divider, so that the divider stays out of it too.
        minRowPitch = if (isPaddedToViewport) rowViewportHeightPx + rowGapPx else 0,
        minLastRowHeight = readableRowHeight,
        // A row read with nothing but itself on the screen is spread out over what the screen shows of it, the pages of
        // a single column too: a full one barely moves, and one holding a short section alone is read in the middle of
        // the screen rather than at the top of an empty one.
        centeredRowHeight = maxOf(readableRowHeight, wholeSongHeight),
        // Stanzas further apart than a chorded line read as strangers rather than as one song.
        maxExtraSectionGap = lineHeightPx,
        heldSpreads = heldSpreads,
        heldPageShifts = heldPageShifts,
        unitSections = units.unitSections,
        piecePadding = piecePadding,
    )
    val positions = Array(unitHeights.size) { index ->
        val row = grid.rows[index]
        val column = grid.columns[index]
        val cellStart = cellStarts[row][column]
        // What is narrower than its slot sits at the slot's start, which is its right edge in a right to left layout. A
        // wide row is a single column of sections as wide as each needs, so each of them is centered in it, as the
        // columns of every other row are centered in the width.
        val x = when {
            grid.wideRows[row] -> cellStart + (slotWidths[row][column] - unitWidths[index]) / 2
            layoutDirection == LayoutDirection.Rtl -> cellStart + slotWidths[row][column] - unitWidths[index]
            else -> cellStart
        }
        IntOffset(x = x, y = arrangement.tops[index])
    }
    return SectionPlacement(
        isScrolledByRow = isScrolledByRow,
        isPaddedToViewport = isPaddedToViewport,
        arrangement = arrangement,
        positions = positions,
        cards = cardBoundsOf(grid, units, positions, unitHeights, unitWidths, piecePadding),
    )
}

/**
 * Where every cell of [grid] starts, [slotWidths] wide: the cells of a row spread evenly across the width, a row of
 * fewer columns than the fullest (the last page, as a rule) packed from the start edge of the fullest one, or every row
 * in one block where the grid shares a keyline.
 */
internal fun cellStartsOf(
    grid: SectionGrid,
    slotWidths: Array<IntArray>,
    width: Int,
    layoutWidth: Int,
    isInset: Boolean,
    endInsetPx: Int,
    columnGapPx: Int,
    layoutDirection: LayoutDirection,
): Array<IntArray> {
    // The space a row leaves is shared out evenly: as much of it before the first column, between every two and after
    // the last, so that no column looks pushed to one side of the row. Two columns are never closer than a column gap,
    // what is left of the space then being split between the two edges. The whole width is shared out wherever that
    // keeps the row out of the edge left to the buttons, and only the rest of it otherwise.
    fun spacedEvenly(widths: IntArray, regionStart: Int, regionWidth: Int): IntArray {
        val shownCount = widths.count { it > 0 }
        val freeSpace = (regionWidth - widths.sum()).coerceAtLeast(0)
        val gap = maxOf(columnGapPx, freeSpace / (shownCount + 1))
        var start = regionStart + ((freeSpace - gap * (shownCount - 1).coerceAtLeast(0)) / 2).coerceAtLeast(0)
        // A column left empty takes no room at all.
        return IntArray(widths.size) { column -> start.also { if (widths[column] > 0) start += widths[column] + gap } }
    }
    fun startsOf(widths: IntArray): IntArray {
        val acrossWholeWidth = spacedEvenly(widths, regionStart = 0, regionWidth = width)
        val rowEnd = widths.indices.filter { widths[it] > 0 }.maxOfOrNull { acrossWholeWidth[it] + widths[it] } ?: 0
        val rowStart = widths.indices.filter { widths[it] > 0 }.minOfOrNull { acrossWholeWidth[it] } ?: 0
        return when {
            !isInset -> acrossWholeWidth
            layoutDirection == LayoutDirection.Ltr && rowEnd <= layoutWidth -> acrossWholeWidth
            layoutDirection == LayoutDirection.Rtl && rowStart >= endInsetPx -> acrossWholeWidth
            layoutDirection == LayoutDirection.Ltr -> spacedEvenly(widths, regionStart = 0, regionWidth = layoutWidth)
            else -> spacedEvenly(widths, regionStart = endInsetPx, regionWidth = layoutWidth)
        }
    }
    return if (grid.sharesKeyline) {
        // Every row is placed in one block centered as a whole: a single column's rows at its start edge, and a pair of
        // narrow sections with the first there too and the second from the middle of the block, or as far from the
        // first as a column gap where that is further.
        val blockWidth = slotWidths.maxOfOrNull { widths -> widths.sum() + columnGapPx * (widths.size - 1) } ?: 0
        val blockStart = startsOf(intArrayOf(blockWidth)).first()
        val isLtr = layoutDirection == LayoutDirection.Ltr
        Array(slotWidths.size) { row ->
            val widths = slotWidths[row]
            IntArray(widths.size) { column ->
                val offset = if (column == 0) {
                    0
                } else {
                    maxOf(widths[0] + columnGapPx, (blockWidth + columnGapPx) / 2).coerceAtMost(blockWidth - widths[column])
                }
                if (isLtr) blockStart + offset else blockStart + blockWidth - offset - widths[column]
            }
        }
    } else {
        val starts = Array(slotWidths.size) { row -> startsOf(slotWidths[row]) }
        fun shownCount(row: Int) = slotWidths[row].count { it > 0 }
        // A row of fewer columns starts where the columns of the fullest one do, rather than spread out on its own: a
        // last page of one column would otherwise sit in the middle of the screen, away from where every page before it
        // started. A wide row is a staff of tablature centered whole, and is left so.
        val fullRow = slotWidths.indices.filter { !grid.wideRows[it] }.maxByOrNull(::shownCount) ?: return starts
        val fullColumns = slotWidths[fullRow].indices.filter { slotWidths[fullRow][it] > 0 }
        val fullStart = fullColumns.minOfOrNull { starts[fullRow][it] } ?: return starts
        val fullEnd = fullColumns.maxOf { starts[fullRow][it] + slotWidths[fullRow][it] }
        val regionStart = if (isInset && layoutDirection == LayoutDirection.Rtl) endInsetPx else 0
        val regionEnd = if (isInset && layoutDirection == LayoutDirection.Ltr) layoutWidth else width
        for (row in slotWidths.indices) {
            if (grid.wideRows[row] || shownCount(row) >= shownCount(fullRow)) continue
            val widths = slotWidths[row]
            val blockWidth = widths.sum() + columnGapPx * (shownCount(row) - 1).coerceAtLeast(0)
            val blockStart = if (layoutDirection == LayoutDirection.Ltr) fullStart else fullEnd - blockWidth
            var x = blockStart.coerceAtMost(regionEnd - blockWidth).coerceAtLeast(regionStart)
            starts[row] = IntArray(widths.size) { column -> x.also { if (widths[column] > 0) x += widths[column] + columnGapPx } }
        }
        starts
    }
}

/**
 * The card of every piece of a section drawn on one, by its number in the song's cards, and where it is placed: over
 * the piece's units at [positions], reaching over the padding the arrangement leaves at a cut.
 */
internal fun cardBoundsOf(
    grid: SectionGrid,
    units: SongUnits,
    positions: Array<IntOffset>,
    unitHeights: IntArray,
    unitWidths: IntArray,
    piecePadding: IntArray,
): List<Pair<Int, IntRect>> {
    val unitCount = units.unitSections.size
    // Every piece of a section on a card - the whole section, where it is not cut - is drawn on a card of its own,
    // which reaches over the padding the arrangement leaves at a cut: the piece's card, and where it is placed.
    val cards = mutableListOf<Pair<Int, IntRect>>()
    var pieceStart = 0
    var piece = 0
    for (index in 0 until unitCount) {
        val section = units.unitSections[index]
        val isLastOfSection = index == units.sectionStarts[section + 1] - 1
        if (!isLastOfSection && grid.rows[index + 1] == grid.rows[index] && grid.columns[index + 1] == grid.columns[index]) continue
        if (units.cardStarts[section] >= 0) {
            val top = positions[pieceStart].y - if (pieceStart > units.sectionStarts[section]) piecePadding[section] else 0
            val bottom = positions[index].y + unitHeights[index] + if (isLastOfSection) 0 else piecePadding[section]
            val left = positions[pieceStart].x
            cards += (units.cardStarts[section] + piece) to IntRect(left, top, left + unitWidths[pieceStart], bottom)
        }
        piece = if (isLastOfSection) 0 else piece + 1
        pieceStart = index + 1
    }
    return cards
}

/**
 * Where the scroll rests on each row of [arrangement] and where each ends, with the stops the song is stepped through,
 * a section's [fadePx] above it.
 */
internal fun songRowsOf(
    grid: SectionGrid,
    units: SongUnits,
    arrangement: SongArrangement,
    unitHeights: IntArray,
    dividerHeight: Int,
    isScrolledByRow: Boolean,
    fadePx: Int,
): SongRows {
    val unitCount = units.unitSections.size
    val sectionCount = units.sectionStarts.size - 1
    val dividerTops = arrangement.dividerTops
    val dividerBottoms = dividerTops.map { it - dividerHeight / 2 + dividerHeight }
    // The first row has no divider above it, and is rested on at its own top.
    val restingOffsets = if (dividerBottoms.isEmpty()) dividerBottoms else listOf(0) + dividerBottoms
    // A song read in rows is stepped through by their pages, however many sections each column holds: stepping to
    // every section would stop in the middle of a row. A single row has no stops of its own, and is stepped through
    // by its sections.
    val isSteppedByRow = isScrolledByRow && restingOffsets.isNotEmpty()
    val singleColumnUnits = (0 until unitCount).filter { grid.columnCounts[grid.rows[it]] == 1 }
    return SongRows(
        restingOffsets = restingOffsets,
        bottoms = if (dividerBottoms.isEmpty()) emptyList() else arrangement.pageBottoms,
        // A section is stepped to as far above it as the song fades out under the top of the screen, so that it
        // starts where that fade ends and is read whole, its label included. The first one's stop is above the
        // top of this layout, which whoever is handed them clamps to the top of the song.
        stepOffsets = if (isSteppedByRow) {
            restingOffsets
        } else {
            List(sectionCount) { section -> arrangement.tops[units.sectionStarts[section]] - fadePx }
        },
        stepSections = if (isSteppedByRow) {
            grid.columnCounts.indices.filter(grid::startsPage).map { row -> units.unitSections[grid.rows.indexOfFirst { it == row }] }
        } else {
            List(sectionCount) { it }
        },
        isSteppedByRow = isSteppedByRow,
        // Only a single column is paged through by its lines: a row of several is never taller than the screen,
        // and a pair of narrow sections that runs a page on past it is paged through by the screen.
        lineTops = singleColumnUnits.map { arrangement.tops[it] },
        lineBottoms = singleColumnUnits.map { arrangement.tops[it] + unitHeights[it] },
        lineSections = singleColumnUnits.map { units.unitSections[it] },
        timingSections = units.timingSections,
    )
}
