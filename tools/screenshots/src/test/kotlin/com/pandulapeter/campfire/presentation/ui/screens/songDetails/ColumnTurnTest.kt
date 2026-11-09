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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.pressKey
import com.pandulapeter.campfire.data.model.preferences.AppLanguage
import com.pandulapeter.campfire.data.model.preferences.PageTurn
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_next_page
import com.pandulapeter.campfire.presentation.resources.song_details_previous_page
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.TestAppearance
import com.pandulapeter.campfire.screenshots.TestWindow
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.tap
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A song turned a column at a time: the pedal and a tap on the half of the screen the song reads on to slide it along by
 * one column, bringing the next one to the front and taking the first one out of view, and a tap on the other half
 * slides it back; its step buttons are a row along the bottom, the previous one first in the song's direction, that
 * covers no line, and whose dots stay where they are as a button comes or goes. Each test reads one song in one interface direction, a song read from the right included, and asks
 * only where the lines and the buttons are on screen.
 */
@OptIn(ExperimentalTestApi::class)
internal class ColumnTurnTest {

    @Test
    fun `the pedal and the screen's halves turn a song one column at a time`() = turnsByColumn(LONG_SONG)

    @Test
    fun `a song read from the left turns towards the right in a right-to-left interface`() = turnsByColumn(
        song = LONG_SONG,
        appearance = TestAppearance(language = AppLanguage.HEBREW),
    )

    @Test
    fun `a song read from the right turns towards the left in a right-to-left interface`() = turnsByColumn(
        song = HEBREW_SONG,
        appearance = TestAppearance(language = AppLanguage.HEBREW),
    )

    @Test
    fun `a song read from the right turns towards the left in a left-to-right interface`() = turnsByColumn(HEBREW_SONG)

    private fun turnsByColumn(song: TurnedSong, appearance: TestAppearance = TestAppearance()) = runAppTest(
        window = TestWindow.DESKTOP,
        songs = listOf(song.file),
        pageTurn = PageTurn.COLUMN,
        appearance = appearance,
    ) {
        val reading = Reading(this, song.readsLeftwards)
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText(song.title))
        tap(hasText(song.title) and hasClickAction())
        waitUntil(timeoutMillis = OPEN_TIMEOUT_MILLIS) { reading.viewport() != null }
        waitForIdle()
        // At the song's start there is no previous button, which the dots do not move for.
        val dotsAtStart = onNode(isStepProgress).fetchSemanticsNode().boundsInRoot
        reading.stepsForward("Page Down") { onRoot().performKeyInput { pressKey(Key.PageDown) } }
        assertEquals(dotsAtStart, onNode(isStepProgress).fetchSemanticsNode().boundsInRoot, "The dots moved as the previous button came.")
        assertTrue(reading.columnsInView().size >= 2, "The song is not read in two columns: they are at ${reading.columnsInView()}.")
        val previous = onNode(hasContentDescription(string(Res.string.song_details_previous_page))).fetchSemanticsNode().boundsInRoot
        val next = onNode(hasContentDescription(string(Res.string.song_details_next_page))).fetchSemanticsNode().boundsInRoot
        val isPreviousFirst = if (song.readsLeftwards) previous.left > next.right else previous.right < next.left
        assertTrue(previous.top == next.top && isPreviousFirst, "The step buttons are not one row, the previous one first: $previous, $next.")
        val lowestLine = reading.lines().filter(reading::isInView).maxOf { it.bounds.bottom }
        assertTrue(lowestLine <= next.top, "The row of step buttons at ${next.top} covers a line ending at $lowestLine.")
        val viewport = checkNotNull(reading.viewport()).boundsInRoot
        val onwards = viewport.left + viewport.width * (if (song.readsLeftwards) 1 else 3) / 4
        val back = viewport.left + viewport.width * (if (song.readsLeftwards) 3 else 1) / 4
        reading.stepsForward("A tap on the half the song reads on to") { onRoot().performTouchInput { click(Offset(onwards, viewport.bottom - TAP_ABOVE_BOTTOM)) } }
        val first = reading.firstLineOfColumn(reading.columnsInView().first())
        onRoot().performTouchInput { click(Offset(back, viewport.bottom - TAP_ABOVE_BOTTOM)) }
        waitForIdle()
        assertTrue(reading.columnsInView().first() < reading.at(reading.lineNow(first)), "A tap on the other half did not bring the column before back.")
        assertTrue(reading.isInView(reading.lineNow(first)), "A tap on the other half took \"${first.text}\" out of view rather than one column back.")
    }
}

/** A song of the test resources, its [title], and whether it [readsLeftwards], as a Hebrew one does. */
private class TurnedSong(val title: String, val file: String, val readsLeftwards: Boolean)

/**
 * The lines of the song a [test] has open, where they are along the way it reads: from the left edge of the screen, or
 * where it [readsLeftwards], from the right one, so that the same questions ask the same of a song either way.
 */
@OptIn(ExperimentalTestApi::class)
private class Reading(private val test: AppTestScope, private val readsLeftwards: Boolean) {

    /** The song's scroll, sideways: the innermost container scrolled across that holds its lines, the pager's being outer. */
    fun viewport(): SemanticsNode? = test.onAllNodes(isScrolledAcross, useUnmergedTree = true).fetchSemanticsNodes()
        .lastOrNull { node -> songPieces(node, 0f).isNotEmpty() }

    fun lines() = songPieces(checkNotNull(viewport()), 0f).filter { it.text.isNotBlank() }

    /** How far along the song's reading direction [line] starts, its right edge counted leftwards in a song read that way. */
    fun at(line: Piece) = if (readsLeftwards) -line.bounds.right else line.bounds.left

    fun isInView(line: Piece) = checkNotNull(viewport()).boundsInRoot.let { window -> line.bounds.left >= window.left && line.bounds.right <= window.right }

    /**
     * Where the columns start along the song, first to last, only those wholly within the window where [inView]: the start
     * edges that lines on several heights share, and that no line of a column before them reaches across, as one of a
     * row of pills or chips set inside a column does.
     */
    fun columns(inView: Boolean): List<Float> {
        val lines = lines().filter { !inView || isInView(it) }
        val edges = lines.groupBy { at(it).toInt() }
            .filterValues { group -> group.map { it.bounds.top }.distinct().size >= MIN_COLUMN_LINES }
            .keys.map(Int::toFloat).sorted()
        return edges.fold(emptyList()) { columns, edge ->
            val isInside = lines.any { line -> columns.any { abs(at(line) - it) <= 1f } && at(line) + line.bounds.width > edge }
            if (isInside) columns else columns + edge
        }
    }

    fun columnsInView() = columns(inView = true)

    fun lineNow(line: Piece) = lines().single { it.key == line.key }

    /** The topmost line of the column starting [start] along the song. */
    fun firstLineOfColumn(start: Float) = lines().filter { abs(at(it) - start) <= COLUMN_TOLERANCE }.minBy { it.bounds.top }

    /** Asserts that [step] brings the column after the first one in view to the front, and takes that first one out of view. */
    fun stepsForward(what: String, step: () -> Unit) {
        val firstColumn = columnsInView().first()
        val leaving = firstLineOfColumn(firstColumn)
        val arriving = firstLineOfColumn(columns(inView = false).first { it > firstColumn + COLUMN_TOLERANCE })
        step()
        test.waitForIdle()
        assertTrue(abs(columnsInView().first() - at(lineNow(arriving))) <= COLUMN_TOLERANCE, "$what did not bring \"${arriving.text}\" to the front.")
        assertTrue(!isInView(lineNow(leaving)), "$what left \"${leaving.text}\" in view.")
    }
}

private val isStepProgress = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

private val isScrolledAcross = SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)

private const val OPEN_TIMEOUT_MILLIS = 10_000L
private val LONG_SONG = TurnedSong(title = "The Long Road North", file = "campfire_tests-the_long_road_north.cho", readsLeftwards = false)
private val HEBREW_SONG = TurnedSong(title = "Shir Aroch", file = "campfire_tests-shir_aroch.cho", readsLeftwards = true)

/** How far apart two lines may start and still be read as starting one column: a card's padding. */
private const val COLUMN_TOLERANCE = 16f

/** How many lines start at one left edge for it to be a column's. */
private const val MIN_COLUMN_LINES = 3

/** How far above the bottom of the song a tap lands, below its lines, where nothing inside takes it. */
private const val TAP_ABOVE_BOTTOM = 24f
