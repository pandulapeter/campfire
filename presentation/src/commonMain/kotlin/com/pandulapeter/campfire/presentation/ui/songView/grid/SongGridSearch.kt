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

import com.pandulapeter.campfire.presentation.ui.screens.songDetails.SongSectionsLayout
import com.pandulapeter.campfire.presentation.ui.songView.model.SongUnits
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.places
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.roundToInt

/** What the search knows of a unit of the song: its size at the reader's own text size, measured once and kept. */
internal interface SectionMetrics {

    /** The height of the unit at [index] laid out [width] wide. */
    fun height(index: Int, width: Int): Int

    /** The narrowest the unit at [index] can be laid out without breaking a line that does not wrap. */
    fun minWidth(index: Int): Int

    /** The width of the widest line of the unit at [index], unwrapped. */
    fun maxWidth(index: Int): Int
}

/** Every dimension and switch the search is decided by, in pixels. */
internal data class SongGridSpec(
    val minColumnWidth: Int,
    val narrowColumnWidth: Int,
    val maxColumnWidth: Int,
    val columnGap: Int,
    val sectionGap: Int,
    val rowGap: Int,
    val availableHeight: Int,
    val maxRowHeight: Int,
    val endInset: Int,
    val keepsEndInset: Boolean,
    val cardPadding: Int,
    val lyricsLineHeight: Int,
    val fitScales: List<Float>,
    val canCutSections: Boolean,
    val isSingleColumn: Boolean,
)

/**
 * Searches the grid [SongSectionsLayout] lays the [units] of a song out in, from the [metrics] of every unit and the
 * dimensions of the [spec], without a measurement pass of its own.
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
 * A song that has to be scrolled leaves [stepButtonInset] of the end edge empty, for the buttons that step
 * through it drawn over it there; the grid is searched for at that narrower width, and a song that fits the screen there
 * is laid out across the full one - unless [keepsStepButtonInset], where the buttons page through a setlist and are
 * there whatever the song is.
 */
internal class SongGridSearch(
    val units: SongUnits,
    private val metrics: SectionMetrics,
    private val spec: SongGridSpec,
) {
    private val unitCount = units.unitSections.size
    private val sectionCount = units.sectionStarts.size - 1
    private val columnGapPx = spec.columnGap
    private val sectionGapPx = spec.sectionGap
    private val rowGapPx = spec.rowGap
    private val maxColumnWidthPx = spec.maxColumnWidth
    private val availableHeightPx = spec.availableHeight
    private val maxRowHeightPx = spec.maxRowHeight
    private val endInsetPx = spec.endInset
    val piecePadding = IntArray(sectionCount) { if (units.cardStarts[it] >= 0) spec.cardPadding else 0 }
    private val lineHeightPx = 2 * spec.lyricsLineHeight
    private val isPagedBySections = spec.isSingleColumn || (sectionCount > MAX_CUT_SECTION_COUNT && units.timingStarts.size > 1)
    // A songbook too long to be cut into pages is stepped by rows where it has columns, each named by its first section,
    // so a change of tempo or time in a row's second column would only be heard on the next row: one that changes is a
    // single column, stepped by its sections, which the click follows past every change.
    private fun widthColumnCountFor(totalWidth: Int) = if (isPagedBySections) {
        1
    } else {
        ((totalWidth + columnGapPx) / (spec.minColumnWidth + columnGapPx)).coerceAtLeast(1)
    }
    // Columns narrower than a readable one are only ever set where a song is cut into pages, since that is where they
    // can save one.
    private fun narrowColumnCountFor(totalWidth: Int) = if (isPagedBySections || !spec.canCutSections || sectionCount > MAX_CUT_SECTION_COUNT) {
        widthColumnCountFor(totalWidth)
    } else {
        maxOf(widthColumnCountFor(totalWidth), (totalWidth + columnGapPx) / (spec.narrowColumnWidth + columnGapPx))
    }
    private fun columnWidthFor(totalWidth: Int, columnCount: Int) = ((totalWidth - columnGapPx * (columnCount - 1)) / columnCount).coerceIn(0, maxColumnWidthPx)
    fun unitsOf(section: Int) = units.sectionStarts[section] until units.sectionStarts[section + 1]
    fun maxWidthOf(unit: Int) = metrics.maxWidth(unit)

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
    private fun wideWidthFor(section: Int, totalWidth: Int, fitScale: Float) = unitsOf(section)
        .maxOf { metrics.minWidth(it) }
        .scaledBy(fitScale)
        .takeIf { it > maxColumnWidthPx && totalWidth > maxColumnWidthPx }
        ?.let { minOf(it, totalWidth) }

    // A section is narrow where every line of it fits half the column without wrapping, so that setting it beside
    // another costs it nothing. A change of tempo or time is never one, since it heads its page rather than sharing a row.
    private fun isNarrowSection(section: Int, halfColumnWidth: Int, fitScale: Float) = !units.isTiming(section) &&
        unitsOf(section).all { maxWidthOf(it).scaledBy(fitScale) <= halfColumnWidth }

    // A wide row is a single column in which every section is as wide as it needs: the ones whose lines do not wrap as
    // wide as those lines, and the rest as wide as a single column, so that what wraps is not read in lines longer
    // than a column's because a staff of tablature shares the row.
    fun SectionGrid.columnWidthOf(unit: Int, totalWidth: Int, fitScale: Float) = if (wideRows[rows[unit]]) {
        wideWidthFor(units.unitSections[unit], totalWidth, fitScale) ?: columnWidthFor(totalWidth, 1)
    } else {
        columnWidthFor(totalWidth, columnCounts[rows[unit]])
    }

    fun gridKey(settledWidth: Int) = SectionGridKey(
        settledWidth = settledWidth,
        availableHeight = availableHeightPx,
        maxRowHeight = maxRowHeightPx,
        maxColumnCount = narrowColumnCountFor(settledWidth).coerceAtMost(maxOf(1, unitCount)),
        endInset = endInsetPx,
        keepsEndInset = spec.keepsEndInset,
    )

    // The grid of the sections of [segment], the units of a stretch of the song that starts at [sectionOffset], every
    // index in it counted from the stretch's first section: the whole song where it is one stretch. Its text is set at
    // fitScale of the reader's size, which reads every height at a width that much wider and scales it down: a line
    // set smaller in a column is the same line at its own size in a proportionally wider one.
    private fun searchSegment(totalWidth: Int, segment: SongUnits, sectionOffset: Int, availableHeightPx: Int, fitScale: Float): SegmentLayout {
        val unitOffset = units.sectionStarts[sectionOffset]
        val sectionCount = segment.sectionStarts.size - 1
        val unitCount = segment.unitSections.size
        val readableColumnCount = widthColumnCountFor(totalWidth)
        val maxColumnCount = readableColumnCount.coerceAtMost(maxOf(1, sectionCount))
        val piecePadding = piecePadding.copyOfRange(sectionOffset, sectionOffset + sectionCount)

        fun unitHeightAt(unit: Int, columnWidth: Int): Int {
            val index = unit + unitOffset
            val width = measuredWidthOf(unitWidthFor(index, columnWidth, fitScale), fitScale)
            return metrics.height(index, width).scaledBy(fitScale)
        }

        // A chorded line that wraps counts twice, since its continuation carries a row of chords too. A line no wider
        // than its column wraps nothing, which is what most lines are, and is not measured again to find that out.
        fun wrappedLinesAt(unit: Int, columnWidth: Int): Int {
            if (segment.lineCount[unit] == 0 || spec.lyricsLineHeight <= 0) return 0
            val index = unit + unitOffset
            val width = measuredWidthOf(unitWidthFor(index, columnWidth, fitScale), fitScale)
            val maxWidth = maxWidthOf(index)
            if (maxWidth <= width) return 0
            val wrappedHeight = metrics.height(index, width)
            val unwrappedHeight = metrics.height(index, maxWidth)
            return ((wrappedHeight - unwrappedHeight).toFloat() / spec.lyricsLineHeight).roundToInt().coerceAtLeast(0)
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

        // Every count a search weighs is compared with the same grid of the fewest columns, so its wrapping is summed once.
        var fewestGrid: SectionGrid? = null
        var fewestWrappedLines = 0
        fun SectionGrid.wrappedLinesOnce(): Int {
            if (this !== fewestGrid) {
                fewestGrid = this
                fewestWrappedLines = wrappedLines()
            }
            return fewestWrappedLines
        }

        // How unevenly a grid of a single row fills the page: the share of the width its columns fill against the share
        // of the height its tallest one does, the columns as wide as the widest thing in them, the way they are drawn.
        // A grid that wraps more lines than the one of the fewest columns is no candidate, since wider lines read better
        // than a better filled page looks.
        fun SectionGrid.imbalance(height: Int, fewest: SectionGrid): Double? {
            if (this !== fewest && wrappedLines() > fewest.wrappedLinesOnce()) return null
            val cellWidths = Array(columnCounts.size) { row -> IntArray(columnCounts[row]) }
            for (unit in 0 until unitCount) {
                val index = unit + unitOffset
                val contentWidth = minOf(unitColumnWidth(unit), unitWidthFor(index, unitColumnWidth(unit), fitScale), maxWidthOf(index).scaledBy(fitScale))
                cellWidths[rows[unit]][columns[unit]] = maxOf(cellWidths[rows[unit]][columns[unit]], contentWidth)
            }
            // The gaps between the columns are filled as much as the columns are: what the eye sees is the block they make.
            val filledWidth = (0 until columnCounts.size).maxOf { row -> cellWidths[row].sum() + columnGapPx * (columnCounts[row] - 1) }
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
        if (spec.canCutSections && availableHeightPx > 0 && readableColumnCount == 1 && sectionCount <= MAX_CUT_SECTION_COUNT) {
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
        if (!spec.canCutSections || availableHeightPx <= 0 || sectionCount > MAX_CUT_SECTION_COUNT) return searched.laidOut()
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
    private fun searchGrid(totalWidth: Int, fitScale: Float): SegmentLayout {
        val timingStarts = units.timingStarts
        if (timingStarts.size < 2 || !spec.canCutSections || availableHeightPx <= 0 || sectionCount > MAX_CUT_SECTION_COUNT) {
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
    private fun searchFitted(totalWidth: Int, canFit: Boolean): FittedLayout {
        val plain = searchGrid(totalWidth, fitScale = 1f)
        if (spec.fitScales.isEmpty() || plain.isReadWithoutStepping() || plain.pageCount < 2 || maxRowHeightPx == Int.MAX_VALUE) return FittedLayout(plain, 1f)
        if (!canFit) return FittedLayout(plain, 1f, isFitDeferred = true)
        val pageCapacity = narrowColumnCountFor(totalWidth).toLong() * maxRowHeightPx
        val plainGrid = plain.searched.grid
        val unwrappedSectionHeights = LongArray(sectionCount)
        for (unit in 0 until unitCount) {
            // The width the plain layout measured the unit at where nothing in it wrapped there, which is the same key and so
            // a cache hit, and its own widest line where something did, which wrappedLinesAt has measured already wherever
            // the grid was scored. A text is as tall at every width from its widest line up, so either is its unwrapped height.
            val measuredWidth = unitWidthFor(unit, plainGrid.columnWidthOf(unit, totalWidth, fitScale = 1f), fitScale = 1f)
            unwrappedSectionHeights[units.unitSections[unit]] += metrics.height(unit, maxOf(measuredWidth, maxWidthOf(unit)))
        }
        val halfColumnWidth = columnWidthFor(totalWidth, 2)
        val fitted = HashMap<Float, SegmentLayout>()
        val fitScale = chooseFitScale(plain.pageCount, spec.fitScales) { scale ->
            val isPairable = { section: Int -> isNarrowSection(section, halfColumnWidth, scale) }
            if (!couldSaveAPage(unwrappedSectionHeights, isPairable, unitCount, scale, plain.pageCount, pageCapacity)) {
                null
            } else {
                searchGrid(totalWidth, scale).also { fitted[scale] = it }.pageCount
            }
        }
        return FittedLayout(fitted[fitScale] ?: plain, fitScale)
    }

    fun decide(settledWidth: Int, canFit: Boolean): DecidedGrid {
        // The buttons that step through the song sit at the end of the screen, so a song that has to be scrolled leaves
        // them that edge. One that fits the screen has no buttons to leave room for, and is laid out
        // across the whole width - but only where it still fits there, since the grid found for the whole width may be
        // one of fewer, taller columns, which would scroll with the buttons over it. Several rows read across are
        // stepped through however short they are, since each is followed by empty space down to the bottom of the screen.
        val insetSearch = if (endInsetPx > 0) searchFitted(settledWidth - endInsetPx, canFit) else null
        return when {
            insetSearch == null -> searchFitted(settledWidth, canFit).decided(isInset = false)
            spec.keepsEndInset || !insetSearch.layout.isReadWithoutStepping() -> insetSearch.decided(isInset = true)
            else -> searchFitted(settledWidth, canFit).let { full ->
                if (full.layout.isReadWithoutStepping()) full.decided(isInset = false) else insetSearch.decided(isInset = true)
            }
        }
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
internal fun Int.scaledBy(fitScale: Float) = if (fitScale == 1f) this else (this * fitScale).roundToInt()

/**
 * The width text drawn [width] wide at [fitScale] of the reader's size is laid out at, at its own size: rounded up, so
 * that a line drawn exactly as wide as itself is never laid out a pixel too narrow for its last word.
 */
internal fun measuredWidthOf(width: Int, fitScale: Float) = if (fitScale == 1f) width else ceil(width / fitScale).toInt()

/**
 * The most sections a song may have for its sections to be cut at all. A file of more than this is a songbook rather
 * than a song, and is read by paging through it; the flow is tried in every number of columns, runs on the main
 * thread, and runs again on every frame of a pinch.
 */
private const val MAX_CUT_SECTION_COUNT = 200
