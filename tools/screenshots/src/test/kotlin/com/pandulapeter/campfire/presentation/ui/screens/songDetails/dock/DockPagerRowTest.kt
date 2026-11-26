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
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.TestWindow
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.tap
import kotlin.test.Test
import kotlin.test.assertTrue

/** A setlist's pager bar runs under the dock, the rows under the song and the side panel alike, as wide as the screen. */
@OptIn(ExperimentalTestApi::class)
internal class DockPagerRowTest {

    @Test
    fun `a setlist's pager row runs under the dock, its Up next state included`() = runAppTest(setlists = listOf(TWO_SONGS_SETLIST)) {
        openSetlistSong()
        val tempoChip = tempoChip()
        val nextSong = nextSongButton()
        assertTrue(nextSong.top >= tempoChip.bottom, "The pager row at $nextSong is not under the dock's tempo chip at $tempoChip.")
        // Stepped to the song's end, the row names the next song.
        val stepAgain = hasText(string(Res.string.song_details_step_again))
        repeat(MAX_STEPS) {
            if (onAllNodes(stepAgain, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) {
                onRoot().performKeyInput { pressKey(Key.DirectionDown) }
                waitForIdle()
            }
        }
        onNode(stepAgain, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `in a wide window the pager row runs under the side panel too`() = runAppTest(window = TestWindow.DESKTOP, setlists = listOf(TWO_SONGS_SETLIST)) {
        openSetlistSong()
        waitForIdle()
        val tempoChip = tempoChip()
        val nextSong = nextSongButton()
        assertTrue(
            nextSong.top >= tempoChip.bottom && nextSong.right > tempoChip.left,
            "The pager row's end at $nextSong is not under the side panel's tempo chip at $tempoChip.",
        )
    }

    private fun AppTestScope.openSetlistSong() {
        tap(hasText(string(Res.string.setlists)) and hasClickAction())
        // A setlist's row is numbered, which its text starts with.
        tap(hasText(FIRST_SONG_TITLE, substring = true) and hasClickAction())
    }

    private fun AppTestScope.nextSongButton() =
        onNode(hasContentDescription(string(Res.string.song_details_next_song)) and hasClickAction()).fetchSemanticsNode().boundsInRoot
}

private const val FIRST_SONG_TITLE = "Home on the Range"
private const val TWO_SONGS_SETLIST = "campfire_tests-two_songs.setlist.json"

/** More than the song has pages, so that its end is reached. */
private const val MAX_STEPS = 20
