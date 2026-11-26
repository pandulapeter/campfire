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

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.TestWindow
import com.pandulapeter.campfire.screenshots.openSong
import com.pandulapeter.campfire.screenshots.runAppTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A wide window has the dock as a side panel at the song's end, its diagrams over the tempo chip at its foot, a narrower
 * one as rows under it, the tempo chip ending the diagrams' row, and a window resized across the line moves the dock
 * between the two without the song jumping.
 */
@OptIn(ExperimentalTestApi::class)
internal class DockSidePanelTest {

    @Test
    fun `a wide window shows the dock as a side panel beside the song, the tempo chip at its foot`() = runAppTest(window = TestWindow.DESKTOP) {
        openSong()
        waitForIdle()
        assertBeside()
    }

    @Test
    fun `a phone on its side keeps the dock under the song`() = runAppTest(window = TestWindow.PHONE_ON_ITS_SIDE) {
        openSong()
        waitForIdle()
        val tempoChip = tempoChip()
        val line = firstLine()
        assertTrue(tempoChip.top >= line.bottom, "The tempo chip at $tempoChip is not under the song's line at $line.")
    }

    @Test
    fun `widening the window slides the panel in and narrowing it takes it out without moving the song`() =
        runAppTest(window = NARROW) {
            openSong()
            waitForIdle()
            assertUnder()
            listOf(WIDE to { assertBeside() }, NARROW to { assertUnder() }).forEach { (window, assertPlaced) ->
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
                assertPlaced()
            }
        }

    private fun AppTestScope.firstLine() = onNode(hasText(FIRST_LINE, substring = true)).fetchSemanticsNode().boundsInRoot

    /** The panel's diagrams stand beside the song's line, over the tempo chip at its foot. */
    private fun AppTestScope.assertBeside() {
        val diagram = diagram()
        val tempoChip = tempoChip()
        val line = firstLine()
        assertTrue(diagram.left >= line.right, "The diagram at $diagram is not beside the song's line at $line.")
        assertTrue(tempoChip.top >= diagram.bottom, "The tempo chip at $tempoChip is not at the panel's foot under the diagram at $diagram.")
    }

    /** The rows stand under the song's line, the tempo chip ending the diagrams' row. */
    private fun AppTestScope.assertUnder() {
        val diagram = diagram()
        val tempoChip = tempoChip()
        val line = firstLine()
        assertTrue(diagram.top >= line.bottom, "The diagram at $diagram is not under the song's line at $line.")
        assertTrue(tempoChip.top < diagram.bottom && tempoChip.bottom > diagram.top, "The tempo chip at $tempoChip is not in the row of the diagram at $diagram.")
    }
}

private val NARROW = TestWindow(width = 800.dp, height = 600.dp)
private val WIDE = TestWindow(width = 1024.dp, height = 600.dp)

/** More frames than the dock takes to move and the song to settle into its new grid. */
private const val SWITCH_FRAMES = 60

/** The words House of the Rising Sun starts with, a comment over its picking pattern. */
private const val FIRST_LINE = "Pick the strings one at a time"
