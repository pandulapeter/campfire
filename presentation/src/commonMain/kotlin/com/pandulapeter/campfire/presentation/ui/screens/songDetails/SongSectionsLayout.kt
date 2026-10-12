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

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.pandulapeter.campfire.presentation.ui.components.EDGE_FADE_SIZE
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Flows its children (the song sections, followed by the dividers that may be drawn between rows of them) into
 * columns.
 *
 * The sections are read across the columns, the way the systems of sheet music are, rather than filling the columns
 * top to bottom and sending the reader on to the top of the next one: they are packed into rows, so that a song that
 * needs to be scrolled never sends the reader back to the top of
 * the next column, since whatever has been scrolled past has been played. Every row gets as many columns as its own
 * sections fill, so a row of two sections is split in two wider columns rather than leaving a hole where a third one
 * would go, and the rows are chosen to take as few pages as possible, the first ones as full as they can be (see
 * [flowIntoRows]). A row of several columns is never taller
 * than [maxRowHeight], the whole of the screen, since the reader could not reach the top of its next column without
 * scrolling back past what was just played, and a section taller than the screen gets a row of its own - but up to
 * that its columns are as tall as they need, so a song that fits the screen in columns is laid out exactly
 * as it would be read top to bottom (see [flowIntoRows]). A section with lines that do not wrap - a staff of tablature
 * longer than a column - may have a row of its own as wide as those lines, where that makes the song shorter. The rows
 * are told apart by a divider drawn in the gap between them. A width with room for a single column is cut into pages
 * the same way, a pair of narrow sections side by side on one where that is lower (see [flowIntoPages]); where nothing
 * is paged - the editor's preview, a songbook - it is laid out as a plain column, without dividers.
 *
 * The columns are made as wide (and therefore as few) as possible while the whole song still fits into
 * [availableHeight] so that the lyrics wrap as little as they can and the vertical space is actually used: a song
 * that needs three columns is not squeezed into five just because the window is wide enough for five. That is the
 * fewest columns that fit the song in a single row, wherever some number of them does, rather than fewer that fit it in
 * rows stepped through one at a time (see [searchColumnCount]). Songs that do not fit no matter what get as many
 * columns as the width allows in each row. Column widths stay between
 * [minColumnWidth] and [maxColumnWidth] and the whole block is centered, so a short
 * song does not end up as one screen-wide column of short lines. Within it a row narrower than the widest one is
 * centered too, a row of a single column included.
 *
 * What it places are the chunks of [units]: every section whole, or as the chunks it may be cut into, which follow each
 * other in one cell unless the grid cuts between them. Where the song is not read whole in a single row with its
 * sections whole and [canCutSections], it is flowed like a magazine instead, every column filled down to the screen and
 * a section running on into the next column or row (see [flowLikeAMagazine]). Of every number of columns the width
 * allows, and of the sections whole, the layout taken is the one of the fewest pages and then of the lowest score (see
 * [LayoutScoring]), the sections whole keeping a full tie and the most columns the rest. The cards of the sections
 * drawn on one are placed here, one behind every piece, from as many as each section could be cut into ([cardKeys]).
 *
 * A song that is stepped through all the same may be set in columns narrower than [minColumnWidth], down to
 * [narrowColumnWidth], wherever its lines mostly fit them and that takes a page away - read without stepping at all
 * where the fewest of them hold it in one row (see [withNarrowColumns]) - and a song a few lines too long for its pages
 * is set a little smaller, at the first of [fitScales] at which it takes a page fewer. The smaller text is the text at
 * its own size laid out in a column that much wider and drawn through a scaled layer, so nothing is composed again.
 * While what the grid is decided for keeps changing ([canFitText] false: a pinch, a window edge being dragged) the song is
 * laid out at the reader's own size, and it is fitted once the change holds still, which [fitDeferral] tells the sections
 * so that the fitted grid is glided to rather than landing in one frame.
 *
 * The candidate column counts are evaluated with the sections' intrinsic heights (they are only measured once, with
 * the width that won), starting from a single column and jumping straight to the smallest count that could possibly
 * fit whenever the current one does not, and a window with room for a single column asks for none of them, since it
 * is paged instead or, where it is not, is the same grid whatever the heights are.
 *
 * The [SectionGrid] is decided for the width the layout settles at ([extraWidth]), the columns themselves are laid
 * out in the width that is available right now, so that a layout that is still being resized keeps its sections
 * where they are and only lets them grow into the space as it arrives.
 *
 * A section that is not carried by `animateBounds` glides across a change of the grid ([sectionGlides]): where it was
 * placed before and where the new grid places it are measured in the lookahead pass, and the approach pass places it
 * that far from its new place, the distance running down to nothing. Only the grid changing moves a section by more
 * than the frame's own change, so everything else is followed as it comes.
 *
 * The layout is measured on every frame of a navigation transition and of a window being resized, so what the search
 * finds is kept in [sectionMeasurements]: the intrinsic height of every section at every width it was asked about,
 * and the grid decided for the last settled width, which is the same on every frame of a transition.
 *
 * [onRowsPlaced] is handed where the scroll rests on each row (the bottom edge of its divider) and where each ends as they settle,
 * every time the layout is measured ahead, which is before any of them is animated to its place, along with the stops
 * the song is stepped through: its pages wherever the song is read in rows or pages and scrolls, however many
 * sections a column of them holds, or otherwise a stop above every section. A song that has to be scrolled leaves [stepButtonInset] of the end edge empty, for the buttons that step
 * through it drawn over it there; the grid is searched for at that narrower width, and a song that fits the screen there
 * is laid out across the full one - unless [keepsStepButtonInset], where the buttons page through a setlist and are
 * there whatever the song is.
 *
 * Where the scroll comes to rest on those dividers ([rowViewportHeight]) and the song scrolls - where it is taller than
 * the screen, or is several rows - every row read across is followed by as much empty space as it leaves of the
 * viewport, so that a row is read with nothing but itself on the screen, and the last one can be brought to the top
 * like the others: the next row peeking in under one would be read as part of it, which is what the dividers are there
 * to prevent. A song of a single row that fits the screen gets none, since it is read whole. A row shorter than the screen
 * - and a song of a single row that fits it, in what the screen shows between [rowViewportTopPadding] and
 * [rowViewportBottomPadding] - is then spread out over it, its sections a little further apart and the rest of the
 * space split above and below them, rather than packed at the top of a screen that is otherwise empty (see
 * [SectionGrid.arrange]). The grid is still decided without that space, since it is not something a shorter song could
 * save.
 *
 * [lyricsLineHeight] is the height of one line of lyrics in pixels, at the size the song is drawn at: twice it is the
 * height of a chorded line, which the flows are handed as what the empty space of a column is counted in, and a unit
 * wraps a line more wherever a width makes it a line of lyrics taller than at the width that wraps nothing.
 */
@Composable
internal fun SongSectionsLayout(
    modifier: Modifier = Modifier,
    minColumnWidth: Dp,
    narrowColumnWidth: Dp,
    maxColumnWidth: Dp,
    columnGap: Dp,
    sectionGap: Dp,
    rowGap: Dp,
    availableHeight: Dp,
    maxRowHeight: Dp,
    rowViewportHeight: Dp,
    rowViewportTopPadding: Dp,
    rowViewportBottomPadding: Dp,
    stepButtonInset: Dp,
    keepsStepButtonInset: Boolean,
    extraWidth: Dp,
    units: SongUnits,
    unitKeys: List<UnitKey>,
    cardKeys: List<CardKey>,
    animations: List<SectionAnimation>,
    cardPadding: Dp,
    canCutSections: Boolean,
    isSingleColumn: Boolean,
    sectionGlides: SectionGlides?,
    sectionMeasurements: SectionMeasurements,
    lyricsLineHeight: Int,
    fitScales: List<Float>,
    canFitText: Boolean,
    fitDeferral: FitDeferral,
    centeringHold: ColumnCenteringHold?,
    onRowsPlaced: (SongRows) -> Unit,
    content: @Composable () -> Unit,
) = Layout(
    modifier = modifier,
    content = content,
) { allMeasurables, constraints ->
    val unitCount = units.unitSections.size
    val sectionCount = units.sectionStarts.size - 1
    val measurables = allMeasurables.take(unitCount)
    val cardMeasurables = allMeasurables.subList(unitCount, unitCount + units.cardCount)
    val dividerMeasurables = allMeasurables.drop(unitCount + units.cardCount)
    val width = constraints.maxWidth
    val settledWidth = width + extraWidth.roundToPx()
    val columnGapPx = columnGap.roundToPx()
    val sectionGapPx = sectionGap.roundToPx()
    val rowGapPx = rowGap.roundToPx()
    val maxColumnWidthPx = maxColumnWidth.roundToPx()
    val availableHeightPx = if (availableHeight.isSpecified) availableHeight.roundToPx() else 0
    val maxRowHeightPx = if (maxRowHeight.isSpecified && maxRowHeight > 0.dp) maxRowHeight.roundToPx() else Int.MAX_VALUE
    val endInsetPx = stepButtonInset.roundToPx()
    val piecePadding = IntArray(sectionCount) { if (units.cardStarts[it] >= 0) cardPadding.roundToPx() else 0 }
    val lineHeightPx = 2 * lyricsLineHeight
    val isPagedBySections = isSingleColumn || (sectionCount > MAX_CUT_SECTION_COUNT && units.timingStarts.size > 1)
    // A songbook too long to be cut into pages is stepped by rows where it has columns, each named by its first section,
    // so a change of tempo or time in a row's second column would only be heard on the next row: one that changes is a
    // single column, stepped by its sections, which the click follows past every change.
    fun widthColumnCountFor(totalWidth: Int) = if (isPagedBySections) {
        1
    } else {
        ((totalWidth + columnGapPx) / (minColumnWidth.roundToPx() + columnGapPx)).coerceAtLeast(1)
    }
    // Columns narrower than a readable one are only ever set where a song is cut into pages, since that is where they
    // can save one.
    fun narrowColumnCountFor(totalWidth: Int) = if (isPagedBySections || !canCutSections || sectionCount > MAX_CUT_SECTION_COUNT) {
        widthColumnCountFor(totalWidth)
    } else {
        maxOf(widthColumnCountFor(totalWidth), (totalWidth + columnGapPx) / (narrowColumnWidth.roundToPx() + columnGapPx))
    }
    fun columnWidthFor(totalWidth: Int, columnCount: Int) = ((totalWidth - columnGapPx * (columnCount - 1)) / columnCount).coerceIn(0, maxColumnWidthPx)
    fun unitsOf(section: Int) = units.sectionStarts[section] until units.sectionStarts[section + 1]
    fun maxWidthOf(unit: Int) = sectionMeasurements.maxWidth(unit, measurables[unit]::maxIntrinsicWidth)

    // A card is as wide as its widest line, up to the width of its column, so that a short chorus does not stretch a wide
    // empty surface across the column, and every chunk of it is laid out at that width, so that its pieces are as wide
    // as each other. A line that is wider still wraps at the column. Every width here is the one a unit takes on the
    // screen, which for text set at fitScale is that much less than the width it is measured at (see measuredWidthOf).
    fun unitWidthFor(unit: Int, columnWidth: Int, fitScale: Float): Int {
        val section = units.unitSections[unit]
        if (units.cardStarts[section] < 0) return columnWidth
        return minOf(columnWidth, unitsOf(section).maxOf(::maxWidthOf).scaledBy(fitScale))
    }

    // The width of a row the section has to itself, or null where that would be no
    // wider than a column: a section can be narrower than its minimum intrinsic width only by breaking the lines that
    // do not wrap (a staff of tablature, the bars of a grid), while everything else in it wraps as a column's would.
    fun wideWidthFor(section: Int, totalWidth: Int, fitScale: Float) = unitsOf(section)
        .maxOf { sectionMeasurements.minWidth(it, measurables[it]::minIntrinsicWidth) }
        .scaledBy(fitScale)
        .takeIf { it > maxColumnWidthPx && totalWidth > maxColumnWidthPx }
        ?.let { minOf(it, totalWidth) }

    // A section is narrow where every line of it fits half the column without wrapping, so that setting it beside
    // another costs it nothing. A change of tempo or time is never one, since it heads its page rather than sharing a row.
    fun isNarrowSection(section: Int, halfColumnWidth: Int, fitScale: Float) = !units.isTiming(section) &&
        unitsOf(section).all { maxWidthOf(it).scaledBy(fitScale) <= halfColumnWidth }

    // A wide row is a single column in which every section is as wide as it needs: the ones whose lines do not wrap as
    // wide as those lines, and the rest as wide as a single column, so that what wraps is not read in lines longer
    // than a column's because a staff of tablature shares the row.
    fun SectionGrid.columnWidthOf(unit: Int, totalWidth: Int, fitScale: Float) = if (wideRows[rows[unit]]) {
        wideWidthFor(units.unitSections[unit], totalWidth, fitScale) ?: columnWidthFor(totalWidth, 1)
    } else {
        columnWidthFor(totalWidth, columnCounts[rows[unit]])
    }

    val gridKey = SectionGridKey(
        settledWidth = settledWidth,
        availableHeight = availableHeightPx,
        maxRowHeight = maxRowHeightPx,
        maxColumnCount = narrowColumnCountFor(settledWidth).coerceAtMost(maxOf(1, unitCount)),
        endInset = endInsetPx,
        keepsEndInset = keepsStepButtonInset,
    )

    // The grid of the sections of [segment], the units of a stretch of the song that starts at [sectionOffset], every
    // index in it counted from the stretch's first section: the whole song where it is one stretch. Its text is set at
    // fitScale of the reader's size, which reads every height at a width that much wider and scales it down: a line
    // set smaller in a column is the same line at its own size in a proportionally wider one.
    fun searchSegment(totalWidth: Int, segment: SongUnits, sectionOffset: Int, availableHeightPx: Int, fitScale: Float): SegmentLayout {
        val unitOffset = units.sectionStarts[sectionOffset]
        val sectionCount = segment.sectionStarts.size - 1
        val unitCount = segment.unitSections.size
        val readableColumnCount = widthColumnCountFor(totalWidth)
        val maxColumnCount = readableColumnCount.coerceAtMost(maxOf(1, sectionCount))
        val piecePadding = piecePadding.copyOfRange(sectionOffset, sectionOffset + sectionCount)

        fun unitHeightAt(unit: Int, columnWidth: Int): Int {
            val index = unit + unitOffset
            val width = measuredWidthOf(unitWidthFor(index, columnWidth, fitScale), fitScale)
            return sectionMeasurements.height(index = index, width = width, measure = measurables[index]::maxIntrinsicHeight).scaledBy(fitScale)
        }

        // A chorded line that wraps counts twice, since its continuation carries a row of chords too. A line no wider
        // than its column wraps nothing, which is what most lines are, and is not measured again to find that out.
        fun wrappedLinesAt(unit: Int, columnWidth: Int): Int {
            if (segment.lineCount[unit] == 0 || lyricsLineHeight <= 0) return 0
            val index = unit + unitOffset
            val width = measuredWidthOf(unitWidthFor(index, columnWidth, fitScale), fitScale)
            val maxWidth = maxWidthOf(index)
            if (maxWidth <= width) return 0
            val wrappedHeight = sectionMeasurements.height(index = index, width = width, measure = measurables[index]::maxIntrinsicHeight)
            val unwrappedHeight = sectionMeasurements.height(index = index, width = maxWidth, measure = measurables[index]::maxIntrinsicHeight)
            return ((wrappedHeight - unwrappedHeight).toFloat() / lyricsLineHeight).roundToInt().coerceAtLeast(0)
        }

        fun sectionHeightAt(section: Int, columnWidth: Int) =
            (segment.sectionStarts[section] until segment.sectionStarts[section + 1]).sumOf { unitHeightAt(it, columnWidth) }

        fun heightAt(section: Int, columnCount: Int) = sectionHeightAt(section, columnWidthFor(totalWidth, columnCount))

        fun wideHeightAt(section: Int) = wideWidthFor(section + sectionOffset, totalWidth, fitScale)?.let { sectionHeightAt(section, it) }

        fun SectionGrid.unitColumnWidth(unit: Int) = if (wideRows[rows[unit]]) {
            wideWidthFor(segment.unitSections[unit] + sectionOffset, totalWidth, fitScale) ?: columnWidthFor(totalWidth, 1)
        } else {
            columnWidthFor(totalWidth, columnCounts[rows[unit]])
        }

        fun gridFor(columnCount: Int) = when {
            // A single column is every section stacked in its order, however tall each of them is, so it is the one
            // grid that is known without an intrinsic measurement. Asking for the heights anyway lays every line of
            // the song out once for a number nobody reads and then once more to be drawn, and where nothing is paged -
            // the editor's preview, a songbook - a window too narrow for a second column has no other grid.
            columnCount == 1 -> singleColumnGrid(sectionCount)
            else -> flowIntoRows(
                sectionCount = sectionCount,
                maxColumnCount = columnCount,
                heightAt = ::heightAt,
                wideHeightAt = ::wideHeightAt,
                sectionGap = sectionGapPx,
                maxRowHeight = maxRowHeightPx,
            )
        }.expandedTo(segment.unitSections)

        fun SectionGrid.unitHeights() = IntArray(unitCount) { unitHeightAt(it, unitColumnWidth(it)) }

        fun SectionGrid.height() = arrange(
            heights = unitHeights(),
            sectionGap = sectionGapPx,
            rowGap = rowGapPx,
            unitSections = segment.unitSections,
            piecePadding = piecePadding,
        ).height

        fun SectionGrid.pageCount(heights: IntArray = unitHeights()) = pageCount(
            heights = heights,
            sectionGap = sectionGapPx,
            maxRowHeight = maxRowHeightPx,
            unitSections = segment.unitSections,
            piecePadding = piecePadding,
        )

        val scoring = LayoutScoring(
            sectionStarts = segment.sectionStarts,
            lineHeight = lineHeightPx,
            lineCount = { segment.lineCount[it] },
            isStanzaBreakBefore = { segment.isStanzaBreakBefore[it] },
        )

        fun SectionGrid.scored(): ScoredGrid {
            val heights = unitHeights()
            return ScoredGrid(
                grid = this,
                pageCount = pageCount(heights),
                score = score(heights, scoring, piecePadding, sectionGapPx, maxRowHeightPx) { wrappedLinesAt(it, unitColumnWidth(it)) },
            )
        }

        fun SearchedGrid.laidOut(pageCount: Int = grid.pageCount()) = SegmentLayout(this, pageCount)

        fun SectionGrid.wrappedLines() = (0 until unitCount).sumOf { wrappedLinesAt(it, unitColumnWidth(it)) }

        // How unevenly a grid of a single row fills the page: the share of the width its columns fill against the share
        // of the height its tallest one does, the columns as wide as the widest thing in them, the way they are drawn.
        // A grid that wraps more lines than the one of the fewest columns is no candidate, since wider lines read better
        // than a better filled page looks.
        fun SectionGrid.imbalance(height: Int, fewest: SectionGrid): Double? {
            if (this !== fewest && wrappedLines() > fewest.wrappedLines()) return null
            val cellWidths = Array(columnCounts.size) { row -> IntArray(columnCounts[row]) }
            for (unit in 0 until unitCount) {
                val index = unit + unitOffset
                val contentWidth = minOf(unitColumnWidth(unit), unitWidthFor(index, unitColumnWidth(unit), fitScale), maxWidthOf(index).scaledBy(fitScale))
                cellWidths[rows[unit]][columns[unit]] = maxOf(cellWidths[rows[unit]][columns[unit]], contentWidth)
            }
            val filledWidth = cellWidths.maxOf { it.sum() }
            if (filledWidth <= 0 || height <= 0 || totalWidth <= 0) return null
            return abs(ln(filledWidth.toDouble() / totalWidth) - ln(height.toDouble() / availableHeightPx))
        }

        fun ScoredGrid.laidOut(): SegmentLayout {
            val height = if (grid.pageCount == 1) grid.height() else Int.MAX_VALUE
            return SearchedGrid(grid, fits = height <= availableHeightPx, height = height).laidOut(pageCount)
        }

        fun magazine(columnCount: Int) = flowLikeAMagazine(
            sectionStarts = segment.sectionStarts,
            columnCount = columnCount,
            heightAt = { unit, columns -> unitHeightAt(unit, columnWidthFor(totalWidth, columns)) },
            isCuttableBefore = { unit -> segment.isCuttableBefore[unit] },
            piecePadding = piecePadding,
            sectionGap = sectionGapPx,
            maxRowHeight = maxRowHeightPx,
            lineHeight = lineHeightPx,
            lineCount = { unit -> segment.lineCount[unit] },
            isStanzaBreakBefore = { unit -> segment.isStanzaBreakBefore[unit] },
        )

        // A song set in readable columns but still stepped through may be set in narrower ones that its lines fit, where
        // that takes a page away (see withNarrowColumns): read whole in one row of them, the sections whole, wherever the
        // fewest such columns hold it so, and otherwise cut like a magazine.
        fun withNarrowColumns(readable: ScoredGrid): SegmentLayout {
            val narrowColumnCount = narrowColumnCountFor(totalWidth).coerceAtMost(unitCount)
            if (narrowColumnCount <= readableColumnCount || readable.pageCount <= 1) return readable.laidOut()
            val fittingCounts = (readableColumnCount + 1..narrowColumnCount).filter { columnCount ->
                val columnWidth = columnWidthFor(totalWidth, columnCount)
                fitsNarrowColumns(segment.lineCount) { wrappedLinesAt(it, columnWidth) }
            }
            fittingCounts.filter { it <= sectionCount }.forEach { columnCount ->
                val grid = gridFor(columnCount)
                if (grid.pageCount == 1) {
                    val height = grid.height()
                    if (height <= availableHeightPx) return SearchedGrid(grid, fits = true, height = height).laidOut(pageCount = 1)
                }
            }
            return withNarrowColumns(readable, fittingCounts.reversed().map { magazine(it).scored() }).laidOut()
        }

        // A width with room for one column is stepped through a page at a time, the pages filled the way the rows of
        // several columns are below, so that a step turns a screen of the song rather than a section of it.
        if (canCutSections && availableHeightPx > 0 && readableColumnCount == 1 && sectionCount <= MAX_CUT_SECTION_COUNT) {
            val halfColumnWidth = columnWidthFor(totalWidth, 2)
            val paged = flowIntoPages(
                sectionStarts = segment.sectionStarts,
                heightAt = { unit, columns -> unitHeightAt(unit, columnWidthFor(totalWidth, columns)) },
                isNarrow = { section -> isNarrowSection(section + sectionOffset, halfColumnWidth, fitScale) },
                isCuttableBefore = { unit -> segment.isCuttableBefore[unit] },
                piecePadding = piecePadding,
                sectionGap = sectionGapPx,
                maxRowHeight = maxRowHeightPx,
                lineHeight = lineHeightPx,
                lineCount = { unit -> segment.lineCount[unit] },
                isStanzaBreakBefore = { unit -> segment.isStanzaBreakBefore[unit] },
            )
            return withNarrowColumns(paged.scored())
        }

        val searched = if (availableHeightPx > 0) {
            searchColumnCount(
                maxColumnCount = maxColumnCount,
                availableHeight = availableHeightPx,
                stackedHeight = { (0 until sectionCount).sumOf { heightAt(it, 1) } + sectionGapPx * (sectionCount - 1).coerceAtLeast(0) },
                gridFor = ::gridFor,
                heightOf = { it.height() },
                imbalanceOf = { grid, height, fewest -> grid.imbalance(height, fewest) },
            )
        } else {
            SearchedGrid(gridFor(maxColumnCount), fits = false, height = Int.MAX_VALUE)
        }

        // A song read whole in a single row is never cut. Anything else is stepped through a page at a time, so it is
        // flowed the way a magazine is set wherever the width has room for two columns, since a page holds the most of
        // the song that way, and every page saved is one the reader does not have to turn.
        val cutColumnCount = readableColumnCount.coerceAtMost(unitCount)
        if (isReadWithoutStepping(searched.fits, searched.grid.pageCount)) return searched.laidOut(pageCount = 1)
        if (!canCutSections || availableHeightPx <= 0 || sectionCount > MAX_CUT_SECTION_COUNT) return searched.laidOut()
        // The sections whole first, so that they keep a full tie: a song whose sections fill the columns as they are is
        // not cut to look the same. Then every count of columns, the most first, a narrower count being taken only
        // where its wider columns, which wrap less, score better.
        var best = searched.grid.scored()
        for (columnCount in cutColumnCount downTo 2) {
            val candidate = magazine(columnCount).scored()
            if (candidate.isBetterThan(best)) best = candidate
        }
        return withNarrowColumns(best)
    }

    // A song that changes its tempo or time signature is laid out a stretch at a time, each searched for as a song of
    // its own against the height of a page, and the grids put end to end, so that every stretch starts a page: the page
    // being read is what the click follows. Such a song is always stepped through, whatever its stretches fit. Where
    // nothing is paged anyway the changes are lines of the one column.
    fun searchGrid(totalWidth: Int, fitScale: Float): SegmentLayout {
        val timingStarts = units.timingStarts
        if (timingStarts.size < 2 || !canCutSections || availableHeightPx <= 0 || sectionCount > MAX_CUT_SECTION_COUNT) {
            return searchSegment(totalWidth, units, sectionOffset = 0, availableHeightPx = availableHeightPx, fitScale = fitScale)
        }
        val segments = timingStarts.indices.map { index ->
            val from = timingStarts[index]
            val until = timingStarts.getOrElse(index + 1) { sectionCount }
            searchSegment(totalWidth, units.slice(from, until), sectionOffset = from, availableHeightPx = maxRowHeightPx, fitScale = fitScale)
        }
        return SegmentLayout(
            searched = SearchedGrid(segments.map { it.searched.grid }.concatenated(), fits = false, height = Int.MAX_VALUE),
            pageCount = segments.sumOf { it.pageCount },
        )
    }

    // A song a few lines too long for its pages is set a little smaller where that takes its last page away (see
    // fitScalesFor). A scale is only searched at where the song could fit one page fewer even with none of its lines
    // wrapped, no gap between two sections, and every narrow section beside another, which a song a whole page over
    // never does, so that a song a whole page over is not laid out at two more widths for nothing. While what the grid is
    // decided for keeps changing ([canFit] false), the song is not fitted at all, only marked as one that may be.
    fun searchFitted(totalWidth: Int, canFit: Boolean): FittedLayout {
        val plain = searchGrid(totalWidth, fitScale = 1f)
        if (fitScales.isEmpty() || plain.isReadWithoutStepping() || plain.pageCount < 2 || maxRowHeightPx == Int.MAX_VALUE) return FittedLayout(plain, 1f)
        if (!canFit) return FittedLayout(plain, 1f, isFitDeferred = true)
        val pageCapacity = narrowColumnCountFor(totalWidth).toLong() * maxRowHeightPx
        val plainGrid = plain.searched.grid
        val unwrappedSectionHeights = LongArray(sectionCount)
        for (unit in 0 until unitCount) {
            // The width the plain layout measured the unit at where nothing in it wrapped there, which is the same key and so
            // a cache hit, and its own widest line where something did, which wrappedLinesAt has measured already wherever
            // the grid was scored. A text is as tall at every width from its widest line up, so either is its unwrapped height.
            val measuredWidth = unitWidthFor(unit, plainGrid.columnWidthOf(unit, totalWidth, fitScale = 1f), fitScale = 1f)
            unwrappedSectionHeights[units.unitSections[unit]] += sectionMeasurements.height(
                index = unit,
                width = maxOf(measuredWidth, maxWidthOf(unit)),
                measure = measurables[unit]::maxIntrinsicHeight,
            )
        }
        val halfColumnWidth = columnWidthFor(totalWidth, 2)
        val fitted = HashMap<Float, SegmentLayout>()
        val fitScale = chooseFitScale(plain.pageCount, fitScales) { scale ->
            val isPairable = { section: Int -> isNarrowSection(section, halfColumnWidth, scale) }
            if (!couldSaveAPage(unwrappedSectionHeights, isPairable, unitCount, scale, plain.pageCount, pageCapacity)) {
                null
            } else {
                searchGrid(totalWidth, scale).also { fitted[scale] = it }.pageCount
            }
        }
        return FittedLayout(fitted[fitScale] ?: plain, fitScale)
    }

    val decidedGrid = sectionMeasurements.grid(gridKey, canFitText) { canFit ->
        // The buttons that step through the song sit at the end of the screen, so a song that has to be scrolled leaves
        // them that edge. One that fits the screen has no buttons to leave room for, and is laid out
        // across the whole width - but only where it still fits there, since the grid found for the whole width may be
        // one of fewer, taller columns, which would scroll with the buttons over it. Several rows read across are
        // stepped through however short they are, since each is followed by empty space down to the bottom of the screen.
        val insetSearch = if (endInsetPx > 0) searchFitted(settledWidth - endInsetPx, canFit) else null
        when {
            insetSearch == null -> searchFitted(settledWidth, canFit).decided(isInset = false)
            keepsStepButtonInset || !insetSearch.layout.isReadWithoutStepping() -> insetSearch.decided(isInset = true)
            else -> searchFitted(settledWidth, canFit).let { full ->
                if (full.layout.isReadWithoutStepping()) full.decided(isInset = false) else insetSearch.decided(isInset = true)
            }
        }
    }
    if (fitDeferral.isPending != decidedGrid.isFitDeferred) fitDeferral.isPending = decidedGrid.isFitDeferred
    val (grid, isInset, fitScale) = decidedGrid
    val layoutWidth = if (isInset) (width - endInsetPx).coerceAtLeast(0) else width
    val columnWidths = IntArray(unitCount) { grid.columnWidthOf(it, layoutWidth, fitScale) }
    // A column is only as wide as the widest thing in it, up to the width it was given, so that a row can be centered
    // by what it shows rather than by the empty space its columns leave after short lines. Only a card needs its width
    // to decide the grid; everything else wraps at the column's width, which is the same height at any width it fits
    // in, so the widths of the rest are only asked for here, once, for the grid that won.
    val cellWidths = Array(grid.columnCounts.size) { row -> IntArray(grid.columnCounts[row]) }
    for (unit in 0 until unitCount) {
        val row = grid.rows[unit]
        val columnWidth = columnWidths[unit]
        val contentWidth = if (units.cardStarts[units.unitSections[unit]] >= 0) {
            unitWidthFor(unit, columnWidth, fitScale)
        } else {
            minOf(columnWidth, maxWidthOf(unit).scaledBy(fitScale))
        }
        cellWidths[row][grid.columns[unit]] = maxOf(cellWidths[row][grid.columns[unit]], contentWidth)
    }
    // The pages of a single column are one column cut into screens, so its lines start at the same edge on all of them.
    if (grid.sharesKeyline) {
        val singleColumnWidth = cellWidths.indices.filter { grid.columnCounts[it] == 1 }.maxOfOrNull { cellWidths[it][0] } ?: 0
        cellWidths.forEachIndexed { row, widths -> if (grid.columnCounts[row] == 1) widths[0] = singleColumnWidth }
    }
    // A card narrower than its cell keeps its own width; everything else takes the cell's, so that its lines, which
    // take their direction from their own content, start at the same edge as the other lines of the cell. In a wide row,
    // where every section has a width of its own, a section is as wide as its widest line instead, every chunk of it
    // alike, so that it can be centered whole.
    val unitWidths = IntArray(unitCount) { unit ->
        val section = units.unitSections[unit]
        when {
            units.cardStarts[section] >= 0 -> unitWidthFor(unit, columnWidths[unit], fitScale)
            grid.wideRows[grid.rows[unit]] -> unitsOf(section).maxOf { minOf(columnWidths[it], maxWidthOf(it).scaledBy(fitScale)) }
            else -> cellWidths[grid.rows[unit]][grid.columns[unit]]
        }
    }
    // Text set smaller to fit the page is the text at its own size laid out wider and drawn scaled down, so that the
    // song is never composed a second time at the smaller style, and its first frame is already the fitted one.
    val placeables = measurables.mapIndexed { index, measurable ->
        val unitWidth = measuredWidthOf(unitWidths[index], fitScale)
        val heightLimit = maxAnimatedSectionHeight(unitWidth)
        // Only a section that is being animated is held to the limit. One that reaches it is cut short for the one
        // frame it takes the composition to take the animation off it, which happens far below the screen.
        val maxHeight = if (measurable.layoutId === AnimatedSectionLayoutId) minOf(constraints.maxHeight, heightLimit) else constraints.maxHeight
        val placeable = measurable.measure(Constraints(minWidth = unitWidth, maxWidth = unitWidth, maxHeight = maxHeight))
        // The approach pass of an animated section reports the size the animation is at, which says nothing about
        // the size it is going to: only the lookahead pass measures that.
        if (isLookingAhead) animations[index].isTooTallToAnimate = placeable.height >= heightLimit
        placeable
    }
    val hasSeveralPages = grid.pageCount > 1
    val unitHeights = IntArray(placeables.size) { placeables[it].height.scaledBy(fitScale) }
    // A single column that is not paged has no rows to tell apart, so it is laid out as one, without dividers or space
    // under it. Rows read across only scroll where they are taller than the screen, or where there are several pages of
    // them, each followed by the rest of the screen. A song of one page that fits the screen is left as it is: space
    // under it would only make it scrollable.
    val isScrolledByRow = (hasSeveralPages || grid.columnCounts.any { it > 1 }) && (
        grid.arrange(unitHeights, sectionGapPx, rowGapPx, unitSections = units.unitSections, piecePadding = piecePadding).height > availableHeightPx ||
            hasSeveralPages && rowViewportHeight.isSpecified
        )
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
    val cellStarts = if (grid.sharesKeyline) {
        // Every row is placed in one block centered as a whole: a single column's rows at its start edge, and a pair of
        // narrow sections with the first there too and the second from the middle of the block, or as far from the
        // first as a column gap where that is further.
        val blockWidth = cellWidths.maxOfOrNull { widths -> widths.sum() + columnGapPx * (widths.size - 1) } ?: 0
        val blockStart = startsOf(intArrayOf(blockWidth)).first()
        val isLtr = layoutDirection == LayoutDirection.Ltr
        Array(cellWidths.size) { row ->
            val widths = cellWidths[row]
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
        Array(cellWidths.size) { row -> startsOf(cellWidths[row]) }
    }
    // The dividers span the whole width rather than the rows, which are as wide as the text size makes the columns: a
    // divider that grew and shrank with a pinch would read as part of the song rather than as the page's own.
    val dividerConstraints = Constraints(minWidth = width, maxWidth = width)
    val dividerPlaceables = dividerMeasurables.take((grid.pageCount - 1).coerceAtLeast(0)).map { it.measure(dividerConstraints) }
    val dividerHeight = dividerPlaceables.firstOrNull()?.height ?: 0
    val isPaddedToViewport = isScrolledByRow && rowViewportHeight.isSpecified
    val rowViewportHeightPx = if (isPaddedToViewport) rowViewportHeight.roundToPx() else 0
    // How much of the viewport a row has with the scroll resting on the divider above it: measured from the row's top
    // rather than from where the scroll rests (the bottom of its divider), and less what the scroll holds below this.
    val readableRowHeight = if (isPaddedToViewport) {
        rowViewportHeightPx - rowGapPx / 2 + (dividerHeight - dividerHeight / 2) - rowViewportBottomPadding.roundToPx()
    } else {
        0
    }
    // A song read whole is spread over the room it was decided for, or what the screen shows of it while that is less -
    // the metronome panel or the app bar on their way in - so that spreading it never makes it scroll.
    val wholeSongHeight = if (!isScrolledByRow && rowViewportHeight.isSpecified && availableHeightPx > 0) {
        minOf(availableHeightPx, rowViewportHeight.roundToPx() - rowViewportTopPadding.roundToPx() - rowViewportBottomPadding.roundToPx())
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
        heldSpreads = centeringHold?.heldSpreads(grid.columnCounts),
        unitSections = units.unitSections,
        piecePadding = piecePadding,
    )
    if (isLookingAhead && centeringHold != null && !centeringHold.isHolding) {
        centeringHold.spreads = arrangement.columnSpreads
        centeringHold.columnCounts = grid.columnCounts
    }
    val dividerTops = arrangement.dividerTops
    // The approach pass places the rows where their sections' animations have got to, which is not where a scroll
    // comes to rest: a fold toggled a moment before a fling would otherwise have it snap to a divider still moving.
    // What is reported is the bottom edge of each divider, so a scroll resting there has the divider just above it.
    if (isLookingAhead) {
        val dividerBottoms = dividerTops.map { it - dividerHeight / 2 + dividerHeight }
        // The first row has no divider above it, and is rested on at its own top.
        val restingOffsets = if (dividerBottoms.isEmpty()) dividerBottoms else listOf(0) + dividerBottoms
        // A song read in rows is stepped through by their pages, however many sections each column holds: stepping to
        // every section would stop in the middle of a row. A single row has no stops of its own, and is stepped through
        // by its sections.
        val isSteppedByRow = isScrolledByRow && restingOffsets.isNotEmpty()
        val singleColumnUnits = (0 until unitCount).filter { grid.columnCounts[grid.rows[it]] == 1 }
        onRowsPlaced(
            SongRows(
                restingOffsets = restingOffsets,
                bottoms = if (dividerBottoms.isEmpty()) emptyList() else arrangement.pageBottoms,
                // A section is stepped to as far above it as the song fades out under the top of the screen, so that it
                // starts where that fade ends and is read whole, its label included. The first one's stop is above the
                // top of this layout, which whoever is handed them clamps to the top of the song.
                stepOffsets = if (isSteppedByRow) {
                    restingOffsets
                } else {
                    val fadePx = EDGE_FADE_SIZE.roundToPx()
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
            ),
        )
    }
    val dividers = dividerPlaceables.mapIndexed { index, placeable ->
        placeable to IntOffset(x = 0, y = dividerTops[index] - placeable.height / 2)
    }
    val positions = Array(placeables.size) { index ->
        val row = grid.rows[index]
        val column = grid.columns[index]
        val cellStart = cellStarts[row][column]
        // A card narrower than its cell sits at the cell's start, which is its right edge in a right to left layout. A
        // wide row is a single column of sections as wide as each needs, so each of them is centered in it, as the
        // columns of every other row are centered in the width.
        val x = when {
            grid.wideRows[row] -> cellStart + (cellWidths[row][column] - unitWidths[index]) / 2
            layoutDirection == LayoutDirection.Rtl -> cellStart + cellWidths[row][column] - unitWidths[index]
            else -> cellStart
        }
        IntOffset(x = x, y = arrangement.tops[index])
    }
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
    val cardPlaceables = cards.map { (card, bounds) ->
        val measurable = cardMeasurables[card]
        val heightLimit = maxAnimatedSectionHeight(bounds.width)
        val height = if (measurable.layoutId === AnimatedSectionLayoutId) minOf(bounds.height, heightLimit) else bounds.height
        if (isLookingAhead) animations[unitCount + card].isTooTallToAnimate = bounds.height >= heightLimit
        measurable.measure(Constraints.fixed(bounds.width, height))
    }
    if (isLookingAhead && sectionGlides != null) {
        sectionGlides.follow(
            keys = unitKeys + cards.map { cardKeys[it.first] },
            grid = decidedGrid,
            positions = Array(unitCount + cards.size) { if (it < unitCount) positions[it] else cards[it - unitCount].second.topLeft },
            isCarriedByBounds = { index ->
                (if (index < unitCount) measurables[index] else cardMeasurables[cards[index - unitCount].first]).layoutId === AnimatedSectionLayoutId
            },
        )
    }
    layout(width, arrangement.height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
        // Read while placing, so that a glide only places the sections again on every frame it runs.
        fun glideOf(key: Any) = if (isLookingAhead || sectionGlides == null) IntOffset.Zero else sectionGlides.offsetOf(key)
        // The cards first, since what is placed later is drawn over what was placed before it.
        cardPlaceables.forEachIndexed { index, placeable ->
            val (card, bounds) = cards[index]
            placeable.place(bounds.topLeft + glideOf(cardKeys[card]))
        }
        placeables.forEachIndexed { index, placeable ->
            val position = positions[index] + glideOf(unitKeys[index])
            // A layer scales the hit testing with the drawing, so the fold toggles and the steppers of a fitted song are
            // still where they are drawn, and the text is drawn through the canvas transform, as sharp as at any size.
            if (fitScale == 1f) {
                placeable.place(position)
            } else {
                placeable.placeWithLayer(position) {
                    scaleX = fitScale
                    scaleY = fitScale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
            }
        }
        dividers.forEach { (placeable, position) -> placeable.place(position) }
    }
}

/**
 * What a stretch of the song, or the whole of it, was set as: its grid, and how many pages it takes as [pageCount]
 * counts them.
 */
private class SegmentLayout(
    val searched: SearchedGrid,
    val pageCount: Int,
) {

    /** Whether the song is read whole, with nothing to step through. */
    fun isReadWithoutStepping() = isReadWithoutStepping(searched.fits, searched.grid.pageCount)
}

/** A song set as [layout], its text at [fitScale] of the reader's size, or not yet fitted though it may be ([isFitDeferred]). */
private class FittedLayout(
    val layout: SegmentLayout,
    val fitScale: Float,
    val isFitDeferred: Boolean = false,
) {

    fun decided(isInset: Boolean) = DecidedGrid(layout.searched.grid, isInset = isInset, fitScale = fitScale, isFitDeferred = isFitDeferred)
}

/** This width or height of text at the reader's size as it is drawn at [fitScale] of it. */
private fun Int.scaledBy(fitScale: Float) = if (fitScale == 1f) this else (this * fitScale).roundToInt()

/**
 * The width text drawn [width] wide at [fitScale] of the reader's size is laid out at, at its own size: rounded up, so
 * that a line drawn exactly as wide as itself is never laid out a pixel too narrow for its last word.
 */
private fun measuredWidthOf(width: Int, fitScale: Float) = if (fitScale == 1f) width else ceil(width / fitScale).toInt()

/**
 * The tallest a section of [columnWidth] is measured while it carries `animateBounds`, which measures its content
 * with `Constraints.fixed` of the section's own size on every pass. A `Constraints` has 31 bits for a width and a
 * height together: 18 of them are left for the height next to a width of less than [WIDE_SECTION_WIDTH], and 16
 * next to a wider one. Both limits are half of what would fit, since a spring that is turned around on its way
 * can carry the animated size past both of its ends.
 */
private fun maxAnimatedSectionHeight(columnWidth: Int) =
    if (columnWidth < WIDE_SECTION_WIDTH) MAX_ANIMATED_SECTION_HEIGHT else MAX_ANIMATED_WIDE_SECTION_HEIGHT

/**
 * The most sections a song may have for its sections to be cut at all. A file of more than this is a songbook rather
 * than a song, and is read by paging through it; the flow is tried in every number of columns, runs on the main
 * thread, and runs again on every frame of a pinch.
 */
private const val MAX_CUT_SECTION_COUNT = 200

private const val MAX_ANIMATED_SECTION_HEIGHT = 1 shl 17
private const val MAX_ANIMATED_WIDE_SECTION_HEIGHT = 1 shl 15
private const val WIDE_SECTION_WIDTH = (1 shl 13) - 1
