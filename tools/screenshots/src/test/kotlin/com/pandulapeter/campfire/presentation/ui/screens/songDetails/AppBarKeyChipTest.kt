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
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.instrument_guitar
import com.pandulapeter.campfire.presentation.resources.settings_instrument
import com.pandulapeter.campfire.presentation.resources.song_details_text_size_decrease
import com.pandulapeter.campfire.presentation.resources.song_details_transposition
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_capo
import com.pandulapeter.campfire.presentation.resources.songs_actions
import com.pandulapeter.campfire.presentation.resources.songs_choose_setlists
import com.pandulapeter.campfire.presentation.resources.tuner
import com.pandulapeter.campfire.screenshots.TestWindow
import com.pandulapeter.campfire.screenshots.openPlayingSheet
import com.pandulapeter.campfire.screenshots.openSong
import com.pandulapeter.campfire.screenshots.opensPlayingSheet
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.tap
import kotlin.test.Test
import kotlin.test.assertTrue

/** Song details' key chip in the app bar and the Playing sheet it opens, with the text size left to the menu. */
@OptIn(ExperimentalTestApi::class)
internal class AppBarKeyChipTest {

    @Test
    fun `the smallest phone's app bar holds the key chip before the setlists' star`() = runAppTest {
        openSong()
        waitForIdle()
        val keyChip = onNode(opensPlayingSheet()).fetchSemanticsNode().boundsInRoot
        val star = onNode(hasContentDescription(string(Res.string.songs_choose_setlists)) and hasClickAction()).fetchSemanticsNode().boundsInRoot
        assertTrue(keyChip.bottom < APP_BAR_BOTTOM && star.bottom < APP_BAR_BOTTOM, "The key chip at $keyChip and the star at $star are not in the app bar.")
        assertTrue(star.left >= keyChip.right, "The star at $star does not follow the key chip at $keyChip.")
    }

    @Test
    fun `the key chip with its Key label fits the room the smallest phone's bar keeps for it`() = runAppTest {
        openSong()
        waitForIdle()
        // A pixel is a dp in the tests' window. House of the Rising Sun reads "Key Am".
        val width = onNode(opensPlayingSheet()).fetchSemanticsNode().boundsInRoot.width
        val room = (KEY_CHIP_ROOM - APP_BAR_KEY_CHIP_GAP).value
        assertTrue(width <= room, "The key chip is $width dp wide, more than the $room dp the bar keeps for it.")
    }

    @Test
    fun `the text size is the menu's however wide the bar is`() = runAppTest(window = TestWindow(width = 800.dp, height = 640.dp)) {
        openSong()
        waitForIdle()
        onNode(hasContentDescription(string(Res.string.song_details_text_size_decrease)) and hasClickAction()).assertDoesNotExist()
        tap(hasContentDescription(string(Res.string.songs_actions)))
        onNode(hasContentDescription(string(Res.string.song_details_text_size_decrease)) and hasAnyAncestor(isPopup())).assertExists()
    }

    @Test
    fun `the Playing sheet holds the transposition and the capo, with no instrument line or tuner`() = runAppTest {
        openSong()
        openPlayingSheet()
        waitForIdle()
        onNode(hasText(string(Res.string.song_details_transposition)) and hasAnyAncestor(isDialog())).assertExists()
        onNode(hasText(string(Res.string.song_editor_insert_capo)) and hasAnyAncestor(isDialog())).assertExists()
        // The dock's instrument card, with its tuner, stays under the sheet; the sheet has neither of its own.
        onNode(hasText(string(Res.string.instrument_guitar)) and hasAnyAncestor(isDialog())).assertDoesNotExist()
        onNode(hasText(string(Res.string.settings_instrument)) and hasAnyAncestor(isDialog())).assertDoesNotExist()
        onNode(hasContentDescription(string(Res.string.tuner)) and hasAnyAncestor(isDialog())).assertDoesNotExist()
    }
}

/** Under the status bar and the app bar of the test's phone, and well above any song line under them, in dp. */
private const val APP_BAR_BOTTOM = 120f
