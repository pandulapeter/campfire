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
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_capo
import com.pandulapeter.campfire.presentation.resources.song_details_capo_decrease
import com.pandulapeter.campfire.presentation.resources.song_details_capo_increase
import com.pandulapeter.campfire.presentation.resources.song_details_dock_playing
import com.pandulapeter.campfire.presentation.resources.song_details_transpose_up
import com.pandulapeter.campfire.screenshots.openPlayingSheet
import com.pandulapeter.campfire.screenshots.openSong
import com.pandulapeter.campfire.screenshots.opensPlayingSheet
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.tap
import kotlin.test.Test

/** The key chip that starts song details' dock: what it names, and the Playing sheet it opens. */
@OptIn(ExperimentalTestApi::class)
internal class DockKeyChipTest {

    @Test
    fun `the key chip names the song's key and opens the Playing sheet with its transposition and capo`() = runAppTest {
        openSong()
        // House of the Rising Sun is in Am.
        onNode(hasText("Am") and hasAnyAncestor(opensPlayingSheet()), useUnmergedTree = true).assertExists()
        openPlayingSheet()
        onNode(hasText(string(Res.string.song_details_dock_playing))).assertExists()
        onNode(hasContentDescription(string(Res.string.song_details_transpose_up))).assertExists()
        onNode(hasContentDescription(string(Res.string.song_details_capo_increase))).assertExists()
    }

    @Test
    fun `a capo put on shows on the key chip as a half of its own, and taken off leaves it`() = runAppTest {
        openSong()
        val capoTwo = hasText(string(Res.string.song_details_capo, 2)) and hasAnyAncestor(opensPlayingSheet())
        onNode(capoTwo, useUnmergedTree = true).assertDoesNotExist()
        openPlayingSheet()
        repeat(2) { tap(hasContentDescription(string(Res.string.song_details_capo_increase))) }
        pressBack()
        waitUntil { onAllNodes(capoTwo, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        openPlayingSheet()
        repeat(2) { tap(hasContentDescription(string(Res.string.song_details_capo_decrease))) }
        pressBack()
        waitUntil { onAllNodes(hasText(string(Res.string.song_details_capo, 2)), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
    }
}
