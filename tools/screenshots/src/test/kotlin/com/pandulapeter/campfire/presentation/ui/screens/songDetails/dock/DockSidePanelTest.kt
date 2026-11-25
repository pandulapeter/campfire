/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.TestWindow
import com.pandulapeter.campfire.screenshots.openSong
import com.pandulapeter.campfire.screenshots.opensPlayingSheet
import com.pandulapeter.campfire.screenshots.runAppTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A wide window has the dock as a side panel at the song's end, a narrower one as rows under it, and a window resized
 * across the line moves the dock between the two without the song jumping.
 */
@OptIn(ExperimentalTestApi::class)
internal class DockSidePanelTest {

    @Test
    fun `a wide window shows the dock as a side panel beside the song`() = runAppTest(window = TestWindow.DESKTOP) {
        openSong()
        waitForIdle()
        assertBeside(keyChip(), firstLine())
    }

    @Test
    fun `a phone on its side keeps the dock under the song`() = runAppTest(window = TestWindow.PHONE_ON_ITS_SIDE) {
        openSong()
        waitForIdle()
        assertUnder(keyChip(), firstLine())
    }

    @Test
    fun `widening the window slides the panel in and narrowing it takes it out without moving the song`() =
        runAppTest(window = NARROW) {
            openSong()
            waitForIdle()
            assertUnder(keyChip(), firstLine())
            listOf(WIDE to ::assertBeside, NARROW to ::assertUnder).forEach { (window, assertPlaced) ->
                val before = firstLine()
                mainClock.autoAdvance = false
                resizeWindow(window.width, window.height)
                // The song's start stays on its keyline on every frame of the dock moving and of the song laid out anew.
                repeat(SWITCH_FRAMES) {
                    mainClock.advanceTimeByFrame()
                    val line = firstLine()
                    assertTrue(abs(line.left - before.left) < 1f, "The song's start jumped to $line from $before as the dock moved.")
                }
                mainClock.autoAdvance = true
                waitForIdle()
                assertPlaced(keyChip(), firstLine())
            }
        }

    private fun AppTestScope.keyChip() = onNode(opensPlayingSheet()).fetchSemanticsNode().boundsInRoot

    private fun AppTestScope.firstLine() = onNode(hasText(FIRST_LINE, substring = true)).fetchSemanticsNode().boundsInRoot

    private fun assertBeside(keyChip: Rect, line: Rect) {
        assertTrue(keyChip.left >= line.right, "The key chip at $keyChip is not beside the song's line at $line.")
        assertTrue(keyChip.top < line.bottom, "The key chip at $keyChip is not at the panel's top.")
    }

    private fun assertUnder(keyChip: Rect, line: Rect) =
        assertTrue(keyChip.top >= line.bottom, "The key chip at $keyChip is not under the song's line at $line.")
}

private val NARROW = TestWindow(width = 800.dp, height = 600.dp)
private val WIDE = TestWindow(width = 1024.dp, height = 600.dp)

/** More frames than the dock takes to move and the song to settle into its new grid. */
private const val SWITCH_FRAMES = 60

/** The words House of the Rising Sun starts with, a comment over its picking pattern. */
private const val FIRST_LINE = "Pick the strings one at a time"
