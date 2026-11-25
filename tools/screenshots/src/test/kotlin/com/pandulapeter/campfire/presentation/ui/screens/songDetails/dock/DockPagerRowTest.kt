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

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.setlists
import com.pandulapeter.campfire.presentation.resources.song_details_next_song
import com.pandulapeter.campfire.presentation.resources.song_details_step_again
import com.pandulapeter.campfire.screenshots.opensPlayingSheet
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.tap
import kotlin.test.Test
import kotlin.test.assertTrue

/** A setlist's pager bar is the dock's last row: on its surface, under its chips, and read with it. */
@OptIn(ExperimentalTestApi::class)
internal class DockPagerRowTest {

    @Test
    fun `a setlist's pager row is the dock's last row, its Up next state included`() = runAppTest(setlists = listOf(TWO_SONGS_SETLIST)) {
        tap(hasText(string(Res.string.setlists)) and hasClickAction())
        // A setlist's row is numbered, which its text starts with.
        tap(hasText(FIRST_SONG_TITLE, substring = true) and hasClickAction())
        val keyChip = onNode(opensPlayingSheet()).fetchSemanticsNode()
        val nextSong = onNode(hasContentDescription(string(Res.string.song_details_next_song)) and hasClickAction()).fetchSemanticsNode()
        assertTrue(nextSong.boundsInRoot.top >= keyChip.boundsInRoot.bottom, "The pager row is not under the dock's chips.")
        assertTrue(dockOf(nextSong).holds(keyChip), "The pager row is not a row of the dock.")
        // Stepped to the song's end, the row names the next song, still in the dock.
        val stepAgain = hasText(string(Res.string.song_details_step_again))
        repeat(MAX_STEPS) {
            if (onAllNodes(stepAgain, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) {
                onRoot().performKeyInput { pressKey(Key.DirectionDown) }
                waitForIdle()
            }
        }
        val prompt = onNode(stepAgain, useUnmergedTree = true).fetchSemanticsNode()
        assertTrue(dockOf(prompt).holds(keyChip), "The pager row's Up next is not in the dock.")
    }

    /** The traversal group [node] is read in, which for the dock's rows is the dock. */
    private fun dockOf(node: SemanticsNode): SemanticsNode = generateSequence(node.parent) { it.parent }
        .first { it.config.getOrNull(SemanticsProperties.IsTraversalGroup) == true }

    private fun SemanticsNode.holds(other: SemanticsNode): Boolean = id == other.id || children.any { it.holds(other) }
}

private const val FIRST_SONG_TITLE = "Home on the Range"
private const val TWO_SONGS_SETLIST = "campfire_tests-two_songs.setlist.json"

/** More than the song has pages, so that its end is reached. */
private const val MAX_STEPS = 20
