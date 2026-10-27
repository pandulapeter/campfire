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

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import com.pandulapeter.campfire.data.model.preferences.SongLayout
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_section_collapse
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.TestWindow
import com.pandulapeter.campfire.screenshots.runAppTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A song laid out in one column that scrolls by itself, driven as a performer drives it. While it plays, the clock is
 * the test's to advance: playing awaits a frame forever, and with a clock that advances by itself, waiting for the app
 * to go idle waits for that to end.
 */
@OptIn(ExperimentalTestApi::class)
internal class AutoScrollTest {

    @Test
    fun `a section folded while the song plays leaves the reader where the song had played to`() = runAppTest(
        window = TestWindow.PHONE,
        songs = listOf(LONG_SONG_FILE),
        songLayout = SongLayout.CONTINUOUS,
    ) {
        openSong(LONG_SONG_TITLE)
        val start = topmostLine()
        mainClock.autoAdvance = false
        try {
            onRoot().performKeyInput { pressKey(Key.Spacebar) }
            mainClock.advanceTimeBy(PLAYED_MILLIS)
            val played = topmostLine()
            assertTrue(played.index > start.index, "The song has not moved while playing, so nothing could send it back.")
            foldSectionBelowReadingLine()
            mainClock.advanceTimeBy(FOLDED_MILLIS)
            val folded = topmostLine()
            val playedIndex = folded.keys.indexOf(played.keys[played.index])
            assertTrue(
                folded.index >= playedIndex,
                "Folding a section sent the song back from \"${played.keys[played.index].first}\" to \"${folded.keys[folded.index].first}\".",
            )
        } finally {
            // Stopped before the clock is handed back, so that no frame loop is left running.
            onRoot().performKeyInput { pressKey(Key.Spacebar) }
            mainClock.advanceTimeByFrame()
            mainClock.autoAdvance = true
        }
    }

    @Test
    fun `two quick presses of the pedal nudge a scrolling song twice as far as one`() = runAppTest(
        window = TestWindow.PHONE,
        songs = listOf(LONG_SONG_FILE),
        songLayout = SongLayout.CONTINUOUS,
    ) {
        openSong(LONG_SONG_TITLE)
        val line = topmostLine().let { it.keys[it.index] }
        val start = lineTop(line)
        onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()
        val nudged = lineTop(line)
        val one = start - nudged
        mainClock.autoAdvance = false
        try {
            onRoot().performKeyInput { pressKey(Key.DirectionDown) }
            mainClock.advanceTimeBy(SECOND_PRESS_MILLIS)
            onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        } finally {
            mainClock.autoAdvance = true
        }
        waitForIdle()
        val two = nudged - lineTop(line)
        assertTrue(one > 0f, "A press of the pedal did not nudge the song.")
        assertTrue(abs(two - 2 * one) <= 1f, "Two quick presses nudged the song $two, not twice the $one of one press.")
    }

    /** The pieces of the song in the order they are read, and which of them is the topmost one wholly on screen. */
    private class TopmostLine(val keys: List<Pair<String, Int>>, val index: Int)

    private fun AppTestScope.topmostLine(): TopmostLine {
        val viewport = checkNotNull(songViewport())
        val bounds = viewport.unclippedBounds()
        val scroll = viewport.scroll()
        val pieces = songPieces(viewport, bounds.top - scroll)
        return TopmostLine(
            keys = pieces.map { it.key },
            index = pieces.indexOfFirst { it.bounds.top >= scroll && it.bounds.bottom <= scroll + bounds.height },
        )
    }

    /** Where the piece of the song [key] names is on screen. */
    private fun AppTestScope.lineTop(key: Pair<String, Int>) = songPieces(checkNotNull(songViewport()), 0f).single { it.key == key }.bounds.top

    /** Folds a section whose heading is on screen below the reading line, which is a third of the way down the song. */
    private fun AppTestScope.foldSectionBelowReadingLine() {
        val viewport = checkNotNull(songViewport()).unclippedBounds()
        val chevron = onAllNodes(hasContentDescription(string(Res.string.song_details_section_collapse), substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .first { node ->
                val bounds = node.unclippedBounds()
                bounds.top > viewport.top + viewport.height / 2 && bounds.bottom < viewport.bottom
            }
        onNode(SemanticsMatcher("is the chevron") { it.id == chevron.id }, useUnmergedTree = true).performTouchInput { click() }
    }
}

private const val LONG_SONG_FILE = "campfire_tests-the_long_road_north.cho"
private const val LONG_SONG_TITLE = "The Long Road North"

/** About five lines of the song at its 112 BPM, two bars a line. */
private const val PLAYED_MILLIS = 20_000L

/** Well inside the first nudge's quarter of a second, so that the second press lands while it still moves. */
private const val SECOND_PRESS_MILLIS = 100L

/** The wait for a new layout to settle, the section's spring, and the glide back, with time to spare. */
private const val FOLDED_MILLIS = 2_000L
