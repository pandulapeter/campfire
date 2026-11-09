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
import com.pandulapeter.campfire.data.model.preferences.PageTurn
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_next_page
import com.pandulapeter.campfire.presentation.resources.song_details_previous_page
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.TestWindow
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.tap
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A song turned a column at a time: with two columns on screen, the pedal and a tap on the right half of the screen slide
 * the song along by one column, the one that was second now where the first one was, and a tap on the left half slides
 * it back; its step buttons are a row along the bottom that covers no line. Asked only of where the lines and the
 * buttons are on screen.
 */
@OptIn(ExperimentalTestApi::class)
internal class ColumnTurnTest {

    @Test
    fun `the pedal and the screen's halves turn a song one column at a time`() = runAppTest(
        window = TestWindow.DESKTOP,
        songs = listOf(LONG_SONG_FILE),
        pageTurn = PageTurn.COLUMN,
    ) {
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText(LONG_SONG_TITLE))
        tap(hasText(LONG_SONG_TITLE) and hasClickAction())
        waitUntil(timeoutMillis = OPEN_TIMEOUT_MILLIS) { stripViewport() != null }
        waitForIdle()
        stepsForward("Page Down") { onRoot().performKeyInput { pressKey(Key.PageDown) } }
        assertTrue(columnStarts(inView = true).size >= 2, "The song is not read in two columns: they start at ${columnStarts(inView = true)}.")
        val previous = onNode(hasContentDescription(string(Res.string.song_details_previous_page))).fetchSemanticsNode().boundsInRoot
        val next = onNode(hasContentDescription(string(Res.string.song_details_next_page))).fetchSemanticsNode().boundsInRoot
        val lowestLine = lines().filter { isInView(it) }.maxOf { it.bounds.bottom }
        assertTrue(previous.top == next.top && previous.right < next.left, "The step buttons are not one row, the previous one first: $previous, $next.")
        assertTrue(lowestLine <= next.top, "The row of step buttons at ${next.top} covers a line ending at $lowestLine.")
        val viewport = checkNotNull(stripViewport()).boundsInRoot
        stepsForward("A tap on the right half") { onRoot().performTouchInput { click(Offset(viewport.left + viewport.width * 3 / 4, viewport.bottom - TAP_ABOVE_BOTTOM)) } }
        val first = firstLineOfColumn(columnStarts(inView = true).first())
        onRoot().performTouchInput { click(Offset(viewport.left + viewport.width / 4, viewport.bottom - TAP_ABOVE_BOTTOM)) }
        waitForIdle()
        assertTrue(columnStarts(inView = true).first() < lineNow(first).bounds.left, "A tap on the left half did not bring the column before back into view.")
        assertTrue(isInView(lineNow(first)), "A tap on the left half took \"${first.text}\" out of view rather than one column back.")
    }

    /** Asserts that [step] brings the column after the first one in view to the front, and takes that first one out of view. */
    private fun AppTestScope.stepsForward(what: String, step: () -> Unit) {
        val firstColumn = columnStarts(inView = true).first()
        val leaving = firstLineOfColumn(firstColumn)
        val arriving = firstLineOfColumn(columnStarts(inView = false).first { it > firstColumn + COLUMN_TOLERANCE })
        step()
        waitForIdle()
        val arrived = lineNow(arriving)
        assertTrue(abs(columnStarts(inView = true).first() - arrived.bounds.left) <= COLUMN_TOLERANCE, "$what did not bring \"${arriving.text}\" to the front.")
        assertTrue(!isInView(lineNow(leaving)), "$what left \"${leaving.text}\" in view.")
    }

    /** The song's scroll, sideways: the innermost container scrolled across that holds its lines, the pager's being outer. */
    private fun AppTestScope.stripViewport(): SemanticsNode? = onAllNodes(isScrolledAcross, useUnmergedTree = true).fetchSemanticsNodes()
        .lastOrNull { node -> songPieces(node, 0f).isNotEmpty() }

    /**
     * Where the columns start, left to right, only those wholly within the window where [inView]: the left edges that
     * lines on several heights share, which a row of pills or chips set inside a column does not.
     */
    private fun AppTestScope.columnStarts(inView: Boolean) = lines().filter { !inView || isInView(it) }
        .groupBy { it.bounds.left.toInt() }
        .filterValues { lines -> lines.map { it.bounds.top }.distinct().size >= MIN_COLUMN_LINES }
        .keys.map(Int::toFloat).sorted()

    private fun AppTestScope.isInView(line: Piece) = checkNotNull(stripViewport()).boundsInRoot.let { window ->
        line.bounds.left >= window.left && line.bounds.right <= window.right
    }

    private fun AppTestScope.lines() = songPieces(checkNotNull(stripViewport()), 0f).filter { it.text.isNotBlank() }

    private fun AppTestScope.lineNow(line: Piece) = lines().single { it.key == line.key }

    /** The topmost line of the column starting at [left]. */
    private fun AppTestScope.firstLineOfColumn(left: Float) = lines().filter { abs(it.bounds.left - left) <= COLUMN_TOLERANCE }.minBy { it.bounds.top }
}

private val isScrolledAcross = SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)

private const val LONG_SONG_TITLE = "The Long Road North"
private const val OPEN_TIMEOUT_MILLIS = 10_000L
private const val LONG_SONG_FILE = "campfire_tests-the_long_road_north.cho"

/** How far apart two lines may start and still be read as starting one column: a card's padding. */
private const val COLUMN_TOLERANCE = 16f

/** How many lines start at one left edge for it to be a column's. */
private const val MIN_COLUMN_LINES = 3

/** How far above the bottom of the song a tap lands, below its lines, where nothing inside takes it. */
private const val TAP_ABOVE_BOTTOM = 24f
