/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songs

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.create
import com.pandulapeter.campfire.presentation.resources.songs_actions
import com.pandulapeter.campfire.presentation.resources.songs_choose_setlists
import com.pandulapeter.campfire.presentation.resources.songs_select_multiple
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.runAppTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
internal class SongCardAnimationsTest {

    @Test
    fun `starting the selection mode fades every card's star and menu into its box`() = runAppTest {
        val actions = string(Res.string.songs_actions)
        val selectMultiple = string(Res.string.songs_select_multiple)
        onAllNodesWithContentDescription(actions)[0].performClick()
        waitUntil { isShown(hasText(selectMultiple)) }
        mainClock.autoAdvance = false
        onNode(hasText(selectMultiple)).performClick()
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        assertTrue(isShown(hasContentDescription(actions)), "The cards' menu buttons left without fading out.")
        assertTrue(isShown(hasText(selectMultiple)), "The menu the mode was started from vanished instead of fading out.")
        mainClock.autoAdvance = true
        waitUntil(timeoutMillis = SETTLE_MILLIS) { !isShown(hasContentDescription(actions)) && !isShown(hasText(selectMultiple)) }
    }

    @Test
    fun `a song's first filing turns and fills its star`() = runAppTest {
        val star = string(Res.string.songs_choose_setlists)
        onAllNodesWithContentDescription(star)[0].performClick()
        // The test library has no setlist to choose, so the press asks for a new one's name, and Create files the song.
        waitUntil { isShown(hasSetTextAction()) }
        onAllNodes(hasSetTextAction())[0].performTextInput("Campfire")
        mainClock.autoAdvance = false
        onNode(hasText(string(Res.string.create)) and hasClickAction()).performClick()
        // A star turned off its resting angle stands in a wider box than its own square, which an untouched one keeps.
        var hasTurned = false
        repeat(TURN_FRAMES) {
            if (!hasTurned) {
                mainClock.advanceTimeByFrame()
                hasTurned = onAllNodesWithContentDescription(star, useUnmergedTree = true).fetchSemanticsNodes().any { node ->
                    node.boundsInRoot.width > node.size.width + 1
                }
            }
        }
        assertTrue(hasTurned, "The first filing of a song changed its star without turning it.")
    }

    private fun AppTestScope.isShown(matcher: SemanticsMatcher) = onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
}

/** Long enough for the selection mode's fades to end. */
private const val SETTLE_MILLIS = 3_000L

/** Two seconds of frames, for the setlist to be written and the star's turn to start. */
private const val TURN_FRAMES = 120
