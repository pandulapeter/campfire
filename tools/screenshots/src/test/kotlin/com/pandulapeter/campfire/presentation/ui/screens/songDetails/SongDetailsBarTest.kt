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

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_back_to_top
import com.pandulapeter.campfire.presentation.resources.song_details_duration
import com.pandulapeter.campfire.presentation.resources.song_details_previous_page
import com.pandulapeter.campfire.presentation.resources.song_details_previous_section
import com.pandulapeter.campfire.presentation.resources.song_details_scroll_up
import com.pandulapeter.campfire.presentation.resources.songs_actions
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.RISING_SUN_TITLE
import com.pandulapeter.campfire.screenshots.openSong
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.tap
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Song details' app bar: its title opens About the song however far the song has been read, and the way back to the
 * song's top is the menu's, the Home key's, and a long press on the step back button.
 */
@OptIn(ExperimentalTestApi::class)
internal class SongDetailsBarTest {

    @Test
    fun `the title opens About the song however far the song has been read`() = runAppTest {
        openSong()
        stepOn()
        tap(hasText(RISING_SUN_TITLE, substring = true) and hasClickAction())
        onNode(hasText(string(Res.string.song_details_duration), substring = true)).assertExists()
        assertTrue(scroll() > 0f, "The title scrolled the song back to its top.")
    }

    @Test
    fun `the menu, the Home key, and a long press on the step back button take the song back to its top`() = runAppTest {
        openSong()
        stepOn()
        tap(hasContentDescription(string(Res.string.songs_actions)))
        tap(hasText(string(Res.string.song_details_back_to_top)) and hasAnyAncestor(isPopup()))
        waitUntil { scroll() == 0f }
        stepOn()
        onRoot().performKeyInput { pressKey(Key.MoveHome) }
        waitUntil { scroll() == 0f }
        stepOn()
        onNode(stepBackButton()).performTouchInput { longClick() }
        waitUntil { scroll() == 0f }
    }

    /** Steps the song on twice with the pedal's key, and checks that it has left its top. */
    private fun AppTestScope.stepOn() {
        repeat(2) {
            onRoot().performKeyInput { pressKey(Key.DirectionDown) }
            waitForIdle()
        }
        assertTrue(scroll() > 0f, "The song did not leave its top.")
    }

    /** The step buttons' previous one, whichever way it steps, which is the one of them with a long press. */
    private fun AppTestScope.stepBackButton() = SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick) and listOf(
        Res.string.song_details_previous_page,
        Res.string.song_details_previous_section,
        Res.string.song_details_scroll_up,
    ).map { hasContentDescription(string(it)) }.reduce { matcher, other -> matcher or other }

    private fun AppTestScope.scroll() = checkNotNull(songViewport()).scroll()
}
