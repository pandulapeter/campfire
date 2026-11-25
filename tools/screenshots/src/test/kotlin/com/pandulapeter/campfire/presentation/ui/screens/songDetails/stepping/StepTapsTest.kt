/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.stepping

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.openSong
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.scroll
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.songPieces
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.songViewport
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.RISING_SUN_TITLE
import com.pandulapeter.campfire.screenshots.TestWindow
import com.pandulapeter.campfire.screenshots.runAppTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A tap on the bottom half of a song steps it on, and a second tap made while the first step is still moving the song
 * steps on again from where that one is headed, as a second press of the pedal does: a reader tapping twice in quick
 * succession means two steps, not one.
 */
@OptIn(ExperimentalTestApi::class)
internal class StepTapsTest {

    @Test
    fun `two quick taps on the bottom half of a song step it twice`() = runAppTest(window = TestWindow.PHONE) {
        openSong(RISING_SUN_TITLE)
        val point = tapPoint()
        mainClock.autoAdvance = false
        try {
            onRoot().performTouchInput { click(point) }
            mainClock.advanceTimeBy(BETWEEN_QUICK_TAPS_MILLIS)
            onRoot().performTouchInput { click(point) }
        } finally {
            mainClock.autoAdvance = true
        }
        waitForIdle()
        val quick = scroll()
        repeat(2) {
            onRoot().performKeyInput { pressKey(Key.DirectionUp) }
            waitForIdle()
        }
        assertEquals(0f, scroll(), "Two steps back did not return the song to its top.")
        onRoot().performTouchInput { click(point) }
        waitForIdle()
        val once = scroll()
        onRoot().performTouchInput { click(point) }
        waitForIdle()
        val twice = scroll()
        assertTrue(once > 0f && twice > once, "Two slow taps did not step the song twice ($once, then $twice), so there is nothing to compare with.")
        assertEquals(twice, quick, "Two quick taps took the song to $quick, where two slow ones take it to $twice (one takes it to $once).")
    }

    private fun AppTestScope.scroll() = checkNotNull(songViewport()).scroll()

    /**
     * A point on the bottom half of the song which nothing inside it takes a tap at (a section header folds on one): on the
     * words of a line there, below the chords drawn over them, else in the room under the first page's last row.
     */
    private fun AppTestScope.tapPoint(): Offset {
        val viewport = checkNotNull(songViewport())
        val bounds = viewport.boundsInRoot
        val clickable = onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
            .filter { it.height < bounds.height / 2 }
        val isFree = { point: Offset -> point.y > bounds.center.y && point.y < bounds.bottom - LYRICS_TAP_INSET && clickable.none { it.contains(point) } }
        return songPieces(viewport, songTop = 0f).filter { it.text.trim().contains(' ') && it.text.any(Char::isLowerCase) }
            .map { Offset(it.bounds.left + LYRICS_TAP_INSET, it.bounds.bottom - LYRICS_TAP_INSET) }
            .firstOrNull(isFree) ?: Offset(bounds.left + bounds.width / 4, bounds.bottom - bounds.height / 8).also { check(isFree(it)) }
    }
}

private const val BETWEEN_QUICK_TAPS_MILLIS = 150L
private const val LYRICS_TAP_INSET = 4f
