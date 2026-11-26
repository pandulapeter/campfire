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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipeLeft
import com.pandulapeter.campfire.presentation.ui.platform.ImpersonatedPlatform
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.tap
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Song details' dock bringing what is on the screen first once the reader has settled on a page: the chords a later
 * part plays move to the front of the diagram row, which scrolls back to its start, and a part played over another
 * scale than the home one has its group at the start of the scale row, past the home group, which keeps its place.
 */
@OptIn(ExperimentalTestApi::class)
internal class DockOrderUiTest {

    @Test
    fun `stepping to a part whose chords come late in the song brings them to the front and the row back to its start`() = runAppTest(
        songs = listOf(SONG_FILE),
        platform = ImpersonatedPlatform.WINDOWS,
    ) {
        openTheSong()
        // Scrolled by hand to its end first, so that only the reorder can bring the row back.
        onNode(diagramOf("G")).performTouchInput { swipeLeft() }
        waitForIdle()
        repeat(STEPS_TO_THE_LAST_VERSE) { stepForward() }
        waitUntil(timeoutMillis = REORDER_TIMEOUT) { diagram("Am").left < diagram("C").left }
        waitUntil(timeoutMillis = REORDER_TIMEOUT) { diagram("Am").left >= 0f && diagram("Am").right <= tempoChip().left }
        assertTrue(diagram("F").left < diagram("C").left, "F at ${diagram("F")} did not come before C at ${diagram("C")}.")
    }

    @Test
    fun `a part over another scale than the home one scrolls the scale row past the home group to its own`() = runAppTest(
        songs = listOf(SONG_FILE),
        platform = ImpersonatedPlatform.WINDOWS,
    ) {
        openTheSong()
        repeat(STEPS_INTO_THE_BRIDGE) { stepForward() }
        // The group's title in the dock, which is under the page's own heading of the same name.
        waitUntil(timeoutMillis = REORDER_TIMEOUT) { dockTitle(BRIDGE).left in 0f..MAX_ROW_START }
        val title = dockTitle(BRIDGE)
        assertTrue(title.left in 0f..MAX_ROW_START, "The bridge's scales at $title are not at the start of the scale row.")
    }

    private fun AppTestScope.openTheSong() {
        tap(hasText(SONG_TITLE) and hasClickAction())
        waitForIdle()
    }

    private fun AppTestScope.stepForward() {
        onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()
    }

    private fun AppTestScope.diagramOf(chord: String) = hasClickAction() and SemanticsMatcher("is the diagram of $chord") { node ->
        node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            .any { description -> description.filterNot { it in DIRECTION_ISOLATES }.startsWith("$chord: ") }
    }

    private fun AppTestScope.diagram(chord: String): Rect = onAllNodes(diagramOf(chord)).fetchSemanticsNodes().first().boundsInRoot

    private fun AppTestScope.dockTitle(text: String): Rect = onAllNodes(hasText(text)).fetchSemanticsNodes().maxBy { it.boundsInRoot.top }.boundsInRoot
}

/** A song in C with a bridge in D, its last verse on chords the first plays none of. */
private const val SONG_FILE = "campfire_tests-the_turning_tide.cho"
private const val SONG_TITLE = "The Turning Tide"
private const val BRIDGE = "Bridge"

/** Two pages into the song, well inside its long bridge, whatever a page holds of the first verse. */
private const val STEPS_INTO_THE_BRIDGE = 2

/** Past the bridge to the song's end, where only the last verse is on the screen. */
private const val STEPS_TO_THE_LAST_VERSE = 8

/** The reorder waits for the screen to settle, and for any reorder before it to have stood a while. */
private const val REORDER_TIMEOUT = 6_000L

/** The scale row's start, its padding in from the screen's edge, give or take a little, in dp. */
private const val MAX_ROW_START = 24f

private const val DIRECTION_ISOLATES = "⁦⁧⁨⁩"
