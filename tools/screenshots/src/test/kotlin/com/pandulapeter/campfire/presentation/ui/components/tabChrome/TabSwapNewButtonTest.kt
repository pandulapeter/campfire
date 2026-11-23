/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.tabChrome

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.setlists
import com.pandulapeter.campfire.presentation.resources.songs_new_song
import com.pandulapeter.campfire.presentation.resources.tools
import com.pandulapeter.campfire.screenshots.runAppTest
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
internal class TabSwapNewButtonTest {

    /** The Metronome's play button stands on the New button's spot, so the two are one filled circle, only the icon crossing. */
    @Test
    fun `the New button stays one filled circle while Songs is swapped for the Metronome`() = runAppTest {
        val button = onNodeWithContentDescription(string(Res.string.songs_new_song)).fetchSemanticsNode().boundsInRoot
        // Inside the circle, clear of both icons.
        val probe = Offset(button.left + button.width * PROBE_FRACTION, button.center.y)
        val filled = captureWindow().colorAt(probe)
        mainClock.autoAdvance = false
        // The Tools item opens the first tool, the Metronome.
        onNode(hasText(string(Res.string.tools)) and hasClickAction()).performTouchInput { click() }
        repeat(SWAP_FRAMES) { frame ->
            mainClock.advanceTimeBy(FRAME_MILLIS)
            val color = captureWindow().colorAt(probe)
            assertTrue(color.isCloseTo(filled), "The circle was $color rather than $filled on frame $frame of the swap.")
        }
    }

    /** Both lists have the same New button at the same spot, so nothing of it may change on any frame of their swap. */
    @Test
    fun `the New button stays as it is while Songs is swapped for Setlists`() = runAppTest(setlists = listOf(TWO_SONGS_SETLIST)) {
        val button = onNodeWithContentDescription(string(Res.string.songs_new_song)).fetchSemanticsNode().boundsInRoot
        val probes = listOf(
            Offset(button.left + button.width * PROBE_FRACTION, button.center.y),
            button.center,
        )
        val before = captureWindow().let { window -> probes.map { window.colorAt(it) } }
        mainClock.autoAdvance = false
        onNode(hasText(string(Res.string.setlists)) and hasClickAction()).performTouchInput { click() }
        repeat(SWAP_FRAMES) { frame ->
            mainClock.advanceTimeBy(FRAME_MILLIS)
            val window = captureWindow()
            probes.forEachIndexed { index, probe ->
                val color = window.colorAt(probe)
                assertTrue(color.isCloseTo(before[index]), "The button was $color rather than ${before[index]} at $probe on frame $frame of the swap.")
            }
        }
    }

    private fun ImageBitmap.colorAt(offset: Offset) = toPixelMap(startX = offset.x.roundToInt(), startY = offset.y.roundToInt(), width = 1, height = 1)[0, 0]

    private fun Color.isCloseTo(other: Color) =
        abs(red - other.red) < TOLERANCE && abs(green - other.green) < TOLERANCE && abs(blue - other.blue) < TOLERANCE
}

private const val TWO_SONGS_SETLIST = "campfire_tests-two_songs.setlist.json"
private const val PROBE_FRACTION = 0.2f
private const val FRAME_MILLIS = 16L
private const val SWAP_FRAMES = 40
private const val TOLERANCE = 0.03f
