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
 * sections a column of them holds, or otherwise a stop above every section.
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
    val availableHeightPx = if (availableHeight.isSpecified) availableHeight.roundToPx() else 0
    val maxRowHeightPx = if (maxRowHeight.isSpecified && maxRowHeight > 0.dp) maxRowHeight.roundToPx() else Int.MAX_VALUE
    val endInsetPx = stepButtonInset.roundToPx()
    val lineHeightPx = 2 * lyricsLineHeight
    // The measurables are only valid inside the pass that hands them over, so the search is built anew on every one.
    val search = SongGridSearch(
        units = units,
        metrics = object : SectionMetrics {
            override fun height(index: Int, width: Int) = sectionMeasurements.height(index = index, width = width, measure = measurables[index]::maxIntrinsicHeight)
            override fun minWidth(index: Int) = sectionMeasurements.minWidth(index, measurables[index]::minIntrinsicWidth)
            override fun maxWidth(index: Int) = sectionMeasurements.maxWidth(index, measurables[index]::maxIntrinsicWidth)
        },
        spec = SongGridSpec(
            minColumnWidth = minColumnWidth.roundToPx(),
            narrowColumnWidth = narrowColumnWidth.roundToPx(),
            maxColumnWidth = maxColumnWidth.roundToPx(),
            columnGap = columnGapPx,
            sectionGap = sectionGapPx,
            rowGap = rowGapPx,
            availableHeight = availableHeightPx,
            maxRowHeight = maxRowHeightPx,
            endInset = endInsetPx,
            keepsEndInset = keepsStepButtonInset,
            cardPadding = cardPadding.roundToPx(),
            lyricsLineHeight = lyricsLineHeight,
            fitScales = fitScales,
            canCutSections = canCutSections,
            isSingleColumn = isSingleColumn,
        ),
    )
    val decidedGrid = sectionMeasurements.grid(search.gridKey(settledWidth), canFitText) { canFit -> search.decide(settledWidth, canFit) }
    if (fitDeferral.isPending != decidedGrid.isFitDeferred) fitDeferral.isPending = decidedGrid.isFitDeferred
    val (grid, isInset, fitScale) = decidedGrid
    val layoutWidth = if (isInset) (width - endInsetPx).coerceAtLeast(0) else width
    val columnWidths = IntArray(unitCount) { with(search) { grid.columnWidthOf(it, layoutWidth, fitScale) } }
    // A column is only as wide as the widest thing in it, up to the width it was given, so that a row can be centered
    // by what it shows rather than by the empty space its columns leave after short lines. Only a card needs its width
    // to decide the grid; everything else wraps at the column's width, which is the same height at any width it fits
    // in, so the widths of the rest are only asked for here, once, for the grid that won.
    val cellWidths = Array(grid.columnCounts.size) { row -> IntArray(grid.columnCounts[row]) }
    for (unit in 0 until unitCount) {
        val row = grid.rows[unit]
        val columnWidth = columnWidths[unit]
        val contentWidth = if (units.cardStarts[units.unitSections[unit]] >= 0) {
            search.unitWidthFor(unit, columnWidth, fitScale)
        } else {
            minOf(columnWidth, search.maxWidthOf(unit).scaledBy(fitScale))
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
            units.cardStarts[section] >= 0 -> search.unitWidthFor(unit, columnWidths[unit], fitScale)
            grid.wideRows[grid.rows[unit]] -> search.unitsOf(section).maxOf { minOf(columnWidths[it], search.maxWidthOf(it).scaledBy(fitScale)) }
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
        grid.arrange(unitHeights, sectionGapPx, rowGapPx, unitSections = units.unitSections, piecePadding = search.piecePadding).height > availableHeightPx ||
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
        heldSpreads = centeringHold?.heldSpreads(grid.columnCounts, grid.joinsPrevious),
        heldPageShifts = centeringHold?.heldPageShifts(grid.columnCounts, grid.joinsPrevious),
        unitSections = units.unitSections,
        piecePadding = search.piecePadding,
    )
    if (isLookingAhead && centeringHold != null && !centeringHold.isHolding) {
        centeringHold.spreads = arrangement.columnSpreads
        centeringHold.pageShifts = arrangement.pageShifts
        centeringHold.columnCounts = grid.columnCounts
        centeringHold.joinsPrevious = grid.joinsPrevious
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
            val top = positions[pieceStart].y - if (pieceStart > units.sectionStarts[section]) search.piecePadding[section] else 0
            val bottom = positions[index].y + unitHeights[index] + if (isLastOfSection) 0 else search.piecePadding[section]
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
 * The tallest a section of [columnWidth] is measured while it carries `animateBounds`, which measures its content
 * with `Constraints.fixed` of the section's own size on every pass. A `Constraints` has 31 bits for a width and a
 * height together: 18 of them are left for the height next to a width of less than [WIDE_SECTION_WIDTH], and 16
 * next to a wider one. Both limits are half of what would fit, since a spring that is turned around on its way
 * can carry the animated size past both of its ends.
 */
private fun maxAnimatedSectionHeight(columnWidth: Int) =
    if (columnWidth < WIDE_SECTION_WIDTH) MAX_ANIMATED_SECTION_HEIGHT else MAX_ANIMATED_WIDE_SECTION_HEIGHT

private const val MAX_ANIMATED_SECTION_HEIGHT = 1 shl 17
private const val MAX_ANIMATED_WIDE_SECTION_HEIGHT = 1 shl 15
private const val WIDE_SECTION_WIDTH = (1 shl 13) - 1
