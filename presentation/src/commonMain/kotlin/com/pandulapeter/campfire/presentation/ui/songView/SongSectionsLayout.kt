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

import com.pandulapeter.campfire.presentation.ui.songView.firstSection.places
import com.pandulapeter.campfire.presentation.ui.songView.model.CardKey
import com.pandulapeter.campfire.presentation.ui.songView.model.SongUnits
import com.pandulapeter.campfire.presentation.ui.songView.model.UnitKey
import com.pandulapeter.campfire.presentation.ui.songView.grid.SectionGrid
import com.pandulapeter.campfire.presentation.ui.songView.grid.SectionMeasurements
import com.pandulapeter.campfire.presentation.ui.songView.grid.SectionMetrics
import com.pandulapeter.campfire.presentation.ui.songView.grid.SongGridSearch
import com.pandulapeter.campfire.presentation.ui.songView.grid.SongGridSpec
import com.pandulapeter.campfire.presentation.ui.songView.grid.arrange
import com.pandulapeter.campfire.presentation.ui.songView.grid.flowIntoPages
import com.pandulapeter.campfire.presentation.ui.songView.grid.flowIntoRows
import com.pandulapeter.campfire.presentation.ui.songView.grid.measuredWidthOf
import com.pandulapeter.campfire.presentation.ui.songView.grid.pageCount
import com.pandulapeter.campfire.presentation.ui.songView.grid.placeSections
import com.pandulapeter.campfire.presentation.ui.songView.grid.scaledBy
import com.pandulapeter.campfire.presentation.ui.songView.grid.sectionWidthsOf
import com.pandulapeter.campfire.presentation.ui.songView.grid.songRowsOf
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DividerDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.pandulapeter.campfire.presentation.ui.components.EDGE_FADE_SIZE
import kotlin.math.roundToInt

/**
 * Flows its children (the song sections, followed by the cards the sections on one are drawn on) into columns, and
 * draws the dividers between rows of them.
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
    rowViewportHeight: () -> Dp,
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
) {
    // Read in composition, so that a theme cross-fade draws the dividers again in its new color.
    val dividerColor = DividerDefaults.color
    // State rather than a plain field: a measure that moves the tops without changing the layout's size (a centering
    // hold released, a fold elsewhere in the row) does not otherwise draw this node again.
    val dividerTops = remember { mutableStateOf(emptyList<Int>()) }
    Layout(
        modifier = modifier.drawWithContent {
            // After the content, so that the dividers stay over the cards and the sections, their layers included.
            drawContent()
            val thickness = DividerDefaults.Thickness.toPx()
            val dividerHeight = DividerDefaults.Thickness.roundToPx()
            dividerTops.value.forEach { top ->
                // Through the middle of a divider of the rounded thickness centred on the top. It spans the whole width
                // rather than the rows, which are as wide as the text size makes the columns, and is never scaled with them:
                // a divider that grew and shrank with a pinch would read as part of the song rather than as the page's own.
                val y = top - dividerHeight / 2 + thickness / 2
                drawLine(color = dividerColor, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = thickness)
            }
        },
        content = content,
    ) { allMeasurables, constraints ->
        val unitCount = units.unitSections.size
        val measurables = allMeasurables.take(unitCount)
        val cardMeasurables = allMeasurables.subList(unitCount, unitCount + units.cardCount)
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
        val widths = search.sectionWidthsOf(decidedGrid, layoutWidth)
        // Text set smaller to fit the page is the text at its own size laid out wider and drawn scaled down, so that the
        // song is never composed a second time at the smaller style, and its first frame is already the fitted one.
        val placeables = measurables.mapIndexed { index, measurable ->
            val unitWidth = measuredWidthOf(widths.unitWidths[index], fitScale)
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
        val unitHeights = IntArray(placeables.size) { placeables[it].height.scaledBy(fitScale) }
        // The height a divider takes between the rows, which decides the room of the last row and where a scroll rests.
        val dividerHeight = if (grid.pageCount > 1) DividerDefaults.Thickness.roundToPx() else 0
        val placement = placeSections(
            grid = grid,
            widths = widths,
            unitHeights = unitHeights,
            dividerHeight = dividerHeight,
            units = units,
            piecePadding = search.piecePadding,
            width = width,
            layoutWidth = layoutWidth,
            isInset = isInset,
            endInsetPx = endInsetPx,
            columnGapPx = columnGapPx,
            sectionGapPx = sectionGapPx,
            rowGapPx = rowGapPx,
            availableHeightPx = availableHeightPx,
            rowViewportHeight = rowViewportHeight().takeIf { it.isSpecified }?.roundToPx(),
            rowViewportTopPaddingPx = rowViewportTopPadding.roundToPx(),
            rowViewportBottomPaddingPx = rowViewportBottomPadding.roundToPx(),
            layoutDirection = layoutDirection,
            lineHeightPx = lineHeightPx,
            heldSpreads = centeringHold?.heldSpreads(grid.columnCounts, grid.joinsPrevious),
            heldPageShifts = centeringHold?.heldPageShifts(grid.columnCounts, grid.joinsPrevious),
        )
        val arrangement = placement.arrangement
        val positions = placement.positions
        val cards = placement.cards
        if (isLookingAhead && centeringHold != null && !centeringHold.isHolding) {
            centeringHold.spreads = arrangement.columnSpreads
            centeringHold.pageShifts = arrangement.pageShifts
            centeringHold.columnCounts = grid.columnCounts
            centeringHold.joinsPrevious = grid.joinsPrevious
        }
        // The approach pass places the rows where their sections' animations have got to, which is not where a scroll
        // comes to rest: a fold toggled a moment before a fling would otherwise have it snap to a divider still moving.
        // What is reported is the bottom edge of each divider, so a scroll resting there has the divider just above it.
        if (isLookingAhead) {
            onRowsPlaced(
                songRowsOf(
                    grid = grid,
                    units = units,
                    arrangement = arrangement,
                    unitHeights = unitHeights,
                    dividerHeight = dividerHeight,
                    isScrolledByRow = placement.isScrolledByRow,
                    fadePx = EDGE_FADE_SIZE.roundToPx(),
                ),
            )
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
            // Only the main pass: the approach measures animated sections at their animated sizes, so its tops are the
            // ones on screen, while the lookahead's are where they settle.
            if (!isLookingAhead) dividerTops.value = arrangement.dividerTops.take((grid.pageCount - 1).coerceAtLeast(0))
        }
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
