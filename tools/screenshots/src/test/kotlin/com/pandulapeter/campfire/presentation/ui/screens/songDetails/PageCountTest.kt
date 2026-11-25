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
import com.pandulapeter.campfire.data.model.preferences.PageTurn
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.setlists
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.RISING_SUN_TITLE
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.tap
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What the song details screen spends on what is not the song never costs a reader a page turn: on the smallest phone,
 * at the default text size, House of the Rising Sun and a long strummed song take no more pages (turned by page) or steps
 * (turned by column) than they did while the song started with its playing controls, its chord diagrams, and its scale
 * suggestions, whatever now holds them. Counted by the step dots, as a reader would ("Page 1 of 8"), alone and in a
 * setlist, whose pager bar takes room of its own.
 */
@OptIn(ExperimentalTestApi::class)
internal class PageCountTest {

    @Test
    fun `House of the Rising Sun takes no more pages than it did`() = assertAtMost(RISING_SUN_TITLE, PageTurn.PAGE, alone = 8, inSetlist = 9)

    @Test
    fun `House of the Rising Sun takes no more column steps than it did`() = assertAtMost(RISING_SUN_TITLE, PageTurn.COLUMN, alone = 7, inSetlist = 8)

    @Test
    fun `a long strummed song takes no more pages than it did`() = assertAtMost(LONG_SONG_TITLE, PageTurn.PAGE, alone = 10, inSetlist = 11)

    @Test
    fun `a long strummed song takes no more column steps than it did`() = assertAtMost(LONG_SONG_TITLE, PageTurn.COLUMN, alone = 10, inSetlist = 10)

    private fun assertAtMost(title: String, pageTurn: PageTurn, alone: Int, inSetlist: Int) {
        listOf(false to alone, true to inSetlist).forEach { (isInSetlist, most) ->
            runAppTest(songs = listOf(LONG_SONG_FILE), setlists = listOf(SETLIST_FILE), pageTurn = pageTurn) {
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
                val case = "$title turned by ${pageTurn.name.lowercase()}${if (isInSetlist) " in a setlist" else ""}"
                println("Page count: $case: $count")
                assertTrue(count <= most, "$case takes $count steps, more than the $most it took.")
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

/** How far down a song card its title is, in its first line. */
private const val TITLE_DEPTH = 16f
private const val LONG_SONG_TITLE = "The Lighthouse Keeper"
private const val LONG_SONG_FILE = "campfire_tests-the_lighthouse_keeper.cho"
private const val SETLIST_FILE = "campfire_tests-page_counts.setlist.json"
