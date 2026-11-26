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

import androidx.compose.ui.test.ExperimentalTestApi
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

/** The key chip in song details' app bar: what it names, and the Playing sheet it opens. */
@OptIn(ExperimentalTestApi::class)
internal class KeyChipTest {

    @Test
    fun `the key chip names the song's key and opens the Playing sheet with its transposition and capo`() = runAppTest {
        openSong()
        // House of the Rising Sun is in Am.
        onNode(hasContentDescription("Am", substring = true) and opensPlayingSheet()).assertExists()
        openPlayingSheet()
        onNode(hasText(string(Res.string.song_details_dock_playing))).assertExists()
        onNode(hasContentDescription(string(Res.string.song_details_transpose_up))).assertExists()
        onNode(hasContentDescription(string(Res.string.song_details_capo_increase))).assertExists()
    }

    @Test
    fun `a capo put on shows on the key chip under its key, and taken off leaves it`() = runAppTest {
        openSong()
        // The chip is read as one description, the capo in it, under the key on screen.
        val capoTwo = hasContentDescription(string(Res.string.song_details_capo, 2), substring = true) and opensPlayingSheet()
        onNode(capoTwo).assertDoesNotExist()
        openPlayingSheet()
        repeat(2) { tap(hasContentDescription(string(Res.string.song_details_capo_increase))) }
        pressBack()
        waitUntil { onAllNodes(capoTwo).fetchSemanticsNodes().isNotEmpty() }
        openPlayingSheet()
        repeat(2) { tap(hasContentDescription(string(Res.string.song_details_capo_decrease))) }
        pressBack()
        waitUntil { onAllNodes(capoTwo).fetchSemanticsNodes().isEmpty() }
    }
}
