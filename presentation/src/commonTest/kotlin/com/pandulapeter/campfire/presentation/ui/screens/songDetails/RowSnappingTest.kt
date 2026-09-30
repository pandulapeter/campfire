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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RowSnappingTest {

    /** Unless given, every row's content reaches down to where the next one rests, and the last one's to the end. */
    private fun snap(
        target: Float,
        dividers: List<Int>,
        start: Float = 0f,
        bottoms: List<Int> = dividers.drop(1) + (3000 + 1000),
        viewport: Int = 1000,
        maxValue: Int = 3000,
    ) = snappedScrollTarget(
        start = start,
        target = target,
        rows = SongRows(restingOffsets = dividers, bottoms = bottoms),
        viewportHeight = viewport,
        maxValue = maxValue,
    )

    @Test
    fun rowsShorterThanTheViewportSnapToTheNearestDivider() {
        assertEquals(800f, snap(target = 700f, dividers = listOf(800, 1600)))
        assertEquals(800f, snap(target = 1100f, dividers = listOf(800, 1600)))
        assertEquals(1600f, snap(target = 1300f, dividers = listOf(800, 1600)))
        assertEquals(0f, snap(target = 300f, dividers = listOf(800, 1600)))
    }

    @Test
    fun theEndsOfTheSongAreSnapPoints() {
        assertEquals(0f, snap(target = -500f, dividers = listOf(800)))
        assertEquals(3000f, snap(start = 900f, target = 5000f, dividers = listOf(800)))
    }

    @Test
    fun aTallRowIsLandedOnAtItsTop() {
        // The row from 500 to 2500 has its bottom at the bottom of the viewport at 1500.
        assertEquals(500f, snap(start = 0f, target = 1200f, dividers = listOf(500, 2500)))
        assertEquals(500f, snap(start = 2500f, target = 1700f, dividers = listOf(500, 2500)))
        assertEquals(2500f, snap(start = 0f, target = 2200f, dividers = listOf(500, 2500)))
    }

    @Test
    fun aTallRowScrollsFreelyOnceItIsBeingRead() {
        assertEquals(1200f, snap(start = 500f, target = 1200f, dividers = listOf(500, 2500)))
        assertEquals(700f, snap(start = 1200f, target = 700f, dividers = listOf(500, 2500)))
        assertEquals(1500f, snap(start = 900f, target = 1700f, dividers = listOf(500, 2500)))
        assertEquals(2500f, snap(start = 900f, target = 2200f, dividers = listOf(500, 2500)))
    }

    @Test
    fun aTallLastRowScrollsFreelyToTheEndOfTheSong() {
        assertEquals(2400f, snap(start = 1000f, target = 2900f, dividers = listOf(2400)))
        assertEquals(2900f, snap(start = 2400f, target = 2900f, dividers = listOf(2400)))
        assertEquals(2600f, snap(start = 2900f, target = 2600f, dividers = listOf(2400)))
    }

    @Test
    fun theSpaceAfterARowDoesNotMakeItTall() {
        // Both rows end 700 below where they rest, and are followed by empty space down to the next one.
        val dividers = listOf(300, 1400)
        val bottoms = listOf(1000, 2100)
        assertEquals(300f, snap(start = 700f, target = 700f, dividers = dividers, bottoms = bottoms, maxValue = 1400))
        assertEquals(1400f, snap(start = 300f, target = 1000f, dividers = dividers, bottoms = bottoms, maxValue = 1400))
    }

    @Test
    fun aDividerPastTheEndOfTheScrollIsTheEnd() {
        assertEquals(3000f, snap(target = 2800f, dividers = listOf(2000, 3400)))
        assertEquals(2000f, snap(target = 2400f, dividers = listOf(2000, 3400)))
    }

    @Test
    fun theButtonsStepToTheNeighbouringRows() {
        val rows = listOf(300, 1300, 2300)
        assertEquals(300, nextStepOffset(scroll = 0, stepOffsets = rows, maxValue = 3000))
        assertEquals(null, previousStepOffset(scroll = 0, stepOffsets = rows, maxValue = 3000))
        // The header is a row of its own, so only the very top of the song has nothing above it.
        assertEquals(0, previousStepOffset(scroll = 300, stepOffsets = rows, maxValue = 3000))
        assertEquals(0, previousStepOffset(scroll = 150, stepOffsets = rows, maxValue = 3000))
        assertEquals(2300, nextStepOffset(scroll = 1300, stepOffsets = rows, maxValue = 3000))
        assertEquals(300, previousStepOffset(scroll = 1300, stepOffsets = rows, maxValue = 3000))
        // Inside a tall row, the previous button goes back to its own top.
        assertEquals(1300, previousStepOffset(scroll = 1800, stepOffsets = rows, maxValue = 3000))
        assertEquals(null, nextStepOffset(scroll = 2300, stepOffsets = rows, maxValue = 3000))
        // A row the scroll cannot reach the top of is as far as it goes, and there is no row after that.
        assertEquals(2000, nextStepOffset(scroll = 1300, stepOffsets = rows, maxValue = 2000))
        assertEquals(null, nextStepOffset(scroll = 2000, stepOffsets = rows, maxValue = 2000))
    }

    @Test
    fun stepsFollowVerticalPositionAcrossColumnsAndClampUnreachableSections() {
        val sections = listOf(2300, -24, 1300, 300, 1300, 3400)
        assertEquals(1300, nextStepOffset(scroll = 300, stepOffsets = sections, maxValue = 2000))
        assertEquals(300, previousStepOffset(scroll = 1300, stepOffsets = sections, maxValue = 2000))
        assertEquals(2000, nextStepOffset(scroll = 1300, stepOffsets = sections, maxValue = 2000))
        assertEquals(1300, previousStepOffset(scroll = 2000, stepOffsets = sections, maxValue = 2000))
        assertEquals(null, nextStepOffset(scroll = 2000, stepOffsets = sections, maxValue = 2000))
    }

    @Test
    fun stepsRespectTheOnePixelToleranceAndTheEndsOfTheSong() {
        val sections = listOf(-24, 300, 1300, 2300)
        assertEquals(1300, nextStepOffset(scroll = 299, stepOffsets = sections, maxValue = 3000))
        assertEquals(0, previousStepOffset(scroll = 301, stepOffsets = sections, maxValue = 3000))
        assertEquals(null, previousStepOffset(scroll = 1, stepOffsets = sections, maxValue = 3000))
        assertEquals(null, nextStepOffset(scroll = 0, stepOffsets = sections, maxValue = 0))
        assertEquals(null, previousStepOffset(scroll = 0, stepOffsets = sections, maxValue = 0))
        assertEquals(null, nextStepOffset(scroll = 100, stepOffsets = emptyList(), maxValue = 3000))
        assertEquals(0, previousStepOffset(scroll = 100, stepOffsets = emptyList(), maxValue = 3000))
    }

    /** A short row, a row twice the viewport's height and a short last row, each followed by empty space. */
    private val rowsWithATallOne = SongRows(
        restingOffsets = listOf(300, 1300, 3300),
        bottoms = listOf(1100, 3000, 3700),
        stepOffsets = listOf(300, 1300, 3300),
        stepSections = listOf(0, 1, 2),
        isSteppedByRow = true,
    )

    /** Pages of 900: the viewport less the overlap. */
    private val window = ReadingWindow(overlap = 100)

    private fun next(scroll: Int, rows: SongRows = rowsWithATallOne, maxValue: Int = 3300, window: ReadingWindow = this.window) =
        nextStepTarget(scroll = scroll, rows = rows, viewportHeight = 1000, window = window, maxValue = maxValue)

    private fun previous(scroll: Int, rows: SongRows = rowsWithATallOne, maxValue: Int = 3300) =
        previousStepTarget(scroll = scroll, rows = rows, viewportHeight = 1000, window = window, maxValue = maxValue)

    @Test
    fun aRowTallerThanTheScreenIsPagedThroughBeforeTheNextOne() {
        assertEquals(300, next(scroll = 0))
        assertEquals(1300, next(scroll = 300))
        // Its content ends 2000 below the top of the viewport at its top, so it is paged until that end is in view.
        assertEquals(2000, next(scroll = 1300))
        assertEquals(3300, next(scroll = 2000))
        assertEquals(null, next(scroll = 3300))
    }

    @Test
    fun steppingBackLandsOnTheEndOfATallRowAndTheTopOfAShortOne() {
        assertEquals(2000, previous(scroll = 3300))
        assertEquals(1300, previous(scroll = 2000))
        assertEquals(300, previous(scroll = 1300))
        assertEquals(0, previous(scroll = 300))
        assertEquals(null, previous(scroll = 0))
    }

    @Test
    fun aTallLastRowIsPagedThroughToTheEndOfTheSongBeforeTheButtonGoesOn() {
        val rows = rowsWithATallOne.copy(bottoms = listOf(1100, 3000, 5000))
        assertEquals(4000, next(scroll = 3300, rows = rows, maxValue = 4000))
        assertTrue(!isStepStop(4000, rows, maxValue = 4000))
        assertEquals(null, next(scroll = 4000, rows = rows, maxValue = 4000))
    }

    @Test
    fun sectionsTallerThanTheScreenArePagedThroughToTheEndOfTheSong() {
        val sections = SongRows(stepOffsets = listOf(-24, 400, 2000), stepSections = listOf(0, 1, 2))
        val window = ReadingWindow(end = 50, overlap = 100)
        assertEquals(1300, next(scroll = 400, rows = sections, maxValue = 3000, window = window))
        assertEquals(2000, next(scroll = 1300, rows = sections, maxValue = 3000, window = window))
        // The last section has no stop after it, so it is paged through to the end of the song.
        assertEquals(2900, next(scroll = 2000, rows = sections, maxValue = 3000, window = window))
        assertEquals(3000, next(scroll = 2900, rows = sections, maxValue = 3000, window = window))
        // Short of the end by less than the empty space after the song, all of it has been on screen.
        assertEquals(null, next(scroll = 2960, rows = sections, maxValue = 3000, window = window))
        assertEquals(1100, previous(scroll = 2000, rows = sections, maxValue = 3000))
    }

    @Test
    fun aSectionThatFitsWhatCanBeReadIsSteppedPastWhole() {
        // Further apart than a page, but no further than what can be read at once, so nothing between them is skipped.
        val sections = SongRows(stepOffsets = listOf(0, 950), stepSections = listOf(0, 1))
        assertEquals(950, next(scroll = 0, rows = sections, maxValue = 3000))
        assertEquals(0, previous(scroll = 950, rows = sections, maxValue = 3000))
    }

    @Test
    fun aPageIsNeverLessThanHalfOfWhatCanBeRead() {
        assertEquals(500, ReadingWindow(overlap = 2000).pageHeight(viewportHeight = 1000))
        assertEquals(700, ReadingWindow(top = 50, bottom = 150, overlap = 100).pageHeight(viewportHeight = 1000))
    }

    @Test
    fun theReaderStaysInTheRowTheirFirstSectionMovesTo() {
        // Three rows starting with sections 0, 2 and 5, the reader resting on the second one.
        val before = SongRows(restingOffsets = listOf(300, 1300, 2300), bottoms = listOf(1000, 2000, 3000), stepOffsets = listOf(300, 1300, 2300), stepSections = listOf(0, 2, 5), isSteppedByRow = true)
        val anchor = readingAnchorOf(scroll = 1300, rows = before)
        assertEquals(ReadingAnchor(section = 2, offset = 0), anchor)
        // A narrower window puts sections 2 and 3 in a row of their own, starting at 1900, and section 4 in the next one.
        val after = SongRows(restingOffsets = listOf(300, 1100, 1900, 2700), bottoms = listOf(900, 1700, 2500, 3300), stepOffsets = listOf(300, 1100, 1900, 2700), stepSections = listOf(0, 1, 2, 4), isSteppedByRow = true)
        assertEquals(1900, anchoredScrollOffset(anchor!!, after, viewportHeight = 1000, maxValue = 4000))
        // A wider one joins the first two rows, and the reader's section is now in the first.
        val wider = SongRows(restingOffsets = listOf(300, 1300), bottoms = listOf(1000, 2000), stepOffsets = listOf(300, 1300), stepSections = listOf(0, 5), isSteppedByRow = true)
        assertEquals(300, anchoredScrollOffset(anchor, wider, viewportHeight = 1000, maxValue = 4000))
    }

    @Test
    fun theReaderKeepsTheirPlaceInsideATallRowAsFarAsItReaches() {
        val rows = SongRows(restingOffsets = listOf(300, 1300), bottoms = listOf(1000, 3300), stepOffsets = listOf(300, 1300), stepSections = listOf(0, 3), isSteppedByRow = true)
        val anchor = readingAnchorOf(scroll = 1800, rows = rows)!!
        assertEquals(ReadingAnchor(section = 3, offset = 500), anchor)
        val shorter = SongRows(restingOffsets = listOf(300, 1100), bottoms = listOf(900, 2400), stepOffsets = listOf(300, 1100), stepSections = listOf(0, 3), isSteppedByRow = true)
        assertEquals(1400, anchoredScrollOffset(anchor, shorter, viewportHeight = 1000, maxValue = 4000))
        val muchShorter = SongRows(restingOffsets = listOf(300, 1100), bottoms = listOf(900, 1800), stepOffsets = listOf(300, 1100), stepSections = listOf(0, 3), isSteppedByRow = true)
        assertEquals(1100, anchoredScrollOffset(anchor, muchShorter, viewportHeight = 1000, maxValue = 4000))
    }

    @Test
    fun theReaderInTheHeaderStaysInIt() {
        val rows = SongRows(restingOffsets = listOf(300, 1300), bottoms = listOf(1000, 2000), stepOffsets = listOf(300, 1300), stepSections = listOf(0, 3), isSteppedByRow = true)
        assertEquals(ReadingAnchor(section = null, offset = 0), readingAnchorOf(scroll = 0, rows = rows))
        val anchor = readingAnchorOf(scroll = 200, rows = rows)!!
        val shorterHeader = rows.copy(stepOffsets = listOf(150, 1150))
        assertEquals(150, anchoredScrollOffset(anchor, shorterHeader, viewportHeight = 1000, maxValue = 4000))
    }

    @Test
    fun sectionsOutOfVerticalOrderAreAnchoredByPosition() {
        // Two columns read top to bottom: sections 0 and 1 in the first, 2 and 3 in the second.
        val columns = SongRows(stepOffsets = listOf(-24, 800, -24, 600), stepSections = listOf(0, 1, 2, 3))
        assertEquals(ReadingAnchor(section = 3, offset = 100), readingAnchorOf(scroll = 700, rows = columns))
        // A single column stacks all four, and the reader is kept in section 3.
        val column = SongRows(stepOffsets = listOf(-24, 800, 1600, 2400), stepSections = listOf(0, 1, 2, 3))
        assertEquals(2500, anchoredScrollOffset(ReadingAnchor(section = 3, offset = 100), column, viewportHeight = 1000, maxValue = 4000))
        assertEquals(2000, anchoredScrollOffset(ReadingAnchor(section = 3, offset = 100), column, viewportHeight = 1000, maxValue = 2000))
    }

    @Test
    fun aSongNotLaidOutYetHasNoAnchor() {
        assertEquals(null, readingAnchorOf(scroll = 100, rows = SongRows()))
        assertEquals(null, anchoredScrollOffset(ReadingAnchor(section = 1, offset = 0), SongRows(), viewportHeight = 1000, maxValue = 4000))
    }
}
