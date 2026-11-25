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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.preferences.PageTurn
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.setlists
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.RISING_SUN_TITLE
import com.pandulapeter.campfire.screenshots.TestWindow
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.tap
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What the song details screen spends on what is not the song costs a reader at most two page turns: on the smallest
 * phone, at the default text size, House of the Rising Sun and a long strummed song take at most [MOST_EXTRA_STEPS] more
 * pages (turned by page) or steps (turned by column) than they did while the song started with its playing controls, its
 * chord diagrams, and its scale suggestions, which the dock under it now holds, its diagrams kept on an upright phone at
 * that cost. Counted by the step dots, as a reader would ("Page 1 of 8"), alone and in a setlist, whose pager bar takes
 * room of its own. A phone on its side and two desktop windows take no more pages than they did with the dock's rows
 * under the pages, as they were counted before its side panel.
 */
@OptIn(ExperimentalTestApi::class)
internal class PageCountTest {

    @Test
    fun `House of the Rising Sun takes at most two more pages than it did`() = assertAtMost(RISING_SUN_TITLE, PageTurn.PAGE, alone = 8, inSetlist = 9)

    @Test
    fun `House of the Rising Sun takes at most two more column steps than it did`() = assertAtMost(RISING_SUN_TITLE, PageTurn.COLUMN, alone = 7, inSetlist = 8)

    @Test
    fun `a long strummed song takes at most two more pages than it did`() = assertAtMost(LONG_SONG_TITLE, PageTurn.PAGE, alone = 10, inSetlist = 11)

    @Test
    fun `a long strummed song takes at most two more column steps than it did`() = assertAtMost(LONG_SONG_TITLE, PageTurn.COLUMN, alone = 10, inSetlist = 10)

    @Test
    fun `the long songs on a phone on its side take no more pages than the dock's bottom row cost them`() =
        assertWindow(TestWindow.PHONE_ON_ITS_SIDE, risingSun = 12 to 16, longSong = 12 to 16)

    @Test
    fun `the long songs in a small desktop window take no more pages than the dock's bottom row cost them`() =
        assertWindow(TestWindow(width = 800.dp, height = 600.dp), risingSun = 4 to 5, longSong = 7 to 8)

    @Test
    fun `the long songs in a laptop's window take no more pages than the dock's bottom row cost them`() =
        assertWindow(TestWindow.DESKTOP, risingSun = 3 to 3, longSong = 4 to 5)

    /**
     * Each of the two songs in [window], turned by page, alone and in a setlist, at most as many pages as [risingSun] and
     * [longSong] say (alone to in a setlist), which are what they took with the dock's rows under the pages.
     */
    private fun assertWindow(window: TestWindow, risingSun: Pair<Int, Int>, longSong: Pair<Int, Int>) {
        assertAtMost(RISING_SUN_TITLE, PageTurn.PAGE, alone = risingSun.first, inSetlist = risingSun.second, window = window, extraSteps = 0)
        assertAtMost(LONG_SONG_TITLE, PageTurn.PAGE, alone = longSong.first, inSetlist = longSong.second, window = window, extraSteps = 0)
    }

    /** [alone] and [inSetlist] are the counts from before the dock, which [extraSteps] more are allowed past. */
    private fun assertAtMost(
        title: String,
        pageTurn: PageTurn,
        alone: Int,
        inSetlist: Int,
        window: TestWindow = TestWindow.PHONE,
        extraSteps: Int = MOST_EXTRA_STEPS,
    ) {
        listOf(false to alone + extraSteps, true to inSetlist + extraSteps).forEach { (isInSetlist, most) ->
            runAppTest(window = window, songs = listOf(LONG_SONG_FILE), setlists = listOf(SETLIST_FILE), pageTurn = pageTurn) {
                if (isInSetlist) {
                    tap(hasText(string(Res.string.setlists)) and hasClickAction())
                    // A setlist's row is numbered, which its text starts with.
                    tap(hasText(title, substring = true) and hasClickAction())
                } else {
                    onNode(hasScrollToNodeAction()).performScrollToNode(hasText(title))
                    // By its title, at the card's top: its middle may be one of its tag chips, which filters the list instead.
                    onNode(hasText(title) and hasClickAction()).performTouchInput { click(Offset(centerX, top + TITLE_DEPTH)) }
                }
                // Every song here is longer than the screen, so its dots come once it is laid out.
                waitUntil(timeoutMillis = OPEN_TIMEOUT_MILLIS) { stepCount() != null }
                waitForIdle()
                val count = checkNotNull(stepCount())
                val case = "$title turned by ${pageTurn.name.lowercase()}${if (isInSetlist) " in a setlist" else ""} at ${window.width} × ${window.height}"
                println("Page count: $case: $count")
                assertTrue(count <= most, "$case takes $count steps, more than the $most it may.")
            }
        }
    }

    /** How many stops the step dots count, null while there are none. */
    private fun AppTestScope.stepCount() = onAllNodes(isStepProgress, useUnmergedTree = true).fetchSemanticsNodes()
        .firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo) }
        ?.let { it.range.endInclusive.roundToInt() + 1 }
}

private val isStepProgress = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

private const val OPEN_TIMEOUT_MILLIS = 10_000L

/** What the owner accepted a long song paying for the dock's diagrams on an upright phone. */
private const val MOST_EXTRA_STEPS = 2

/** How far down a song card its title is, in its first line. */
private const val TITLE_DEPTH = 16f
private const val LONG_SONG_TITLE = "The Lighthouse Keeper"
private const val LONG_SONG_FILE = "campfire_tests-the_lighthouse_keeper.cho"
private const val SETLIST_FILE = "campfire_tests-page_counts.setlist.json"
