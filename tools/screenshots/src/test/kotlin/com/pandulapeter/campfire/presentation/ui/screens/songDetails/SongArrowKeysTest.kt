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
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.LayoutDirection
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.setlists
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.RISING_SUN_TITLE
import com.pandulapeter.campfire.screenshots.TestAppearance
import com.pandulapeter.campfire.screenshots.tap
import com.pandulapeter.campfire.screenshots.runAppTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The arrow keys turn to the song of the setlist lying that way in the pager. The setlist has Home on the Range before
 * House of the Rising Sun, so the song before the one opened lies on the left in a left-to-right layout and on the right
 * in a right-to-left one, and nothing lies past it.
 */
@OptIn(ExperimentalTestApi::class)
internal class SongArrowKeysTest {

    @Test
    fun `Left turns to the previous song in a left-to-right layout`() = runAppTest(setlists = listOf(TWO_SONGS_SETLIST)) {
        openRisingSunFromSetlist()
        onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        waitForIdle()
        assertTrue(isTitleOnScreen(PREVIOUS_SONG_TITLE), "Left did not turn to the song before the one opened.")
        assertTrue(!isTitleOnScreen(RISING_SUN_TITLE), "The song opened is still on screen after Left.")
    }

    @Test
    fun `Right turns to the previous song in a right-to-left layout`() = runAppTest(
        setlists = listOf(TWO_SONGS_SETLIST),
        appearance = TestAppearance(layoutDirection = LayoutDirection.Rtl),
    ) {
        openRisingSunFromSetlist()
        onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        waitForIdle()
        assertTrue(isTitleOnScreen(RISING_SUN_TITLE), "Left turned away from the last song, towards nothing on its left.")
        onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()
        assertTrue(isTitleOnScreen(PREVIOUS_SONG_TITLE), "Right did not turn to the song before the one opened.")
        assertTrue(!isTitleOnScreen(RISING_SUN_TITLE), "The song opened is still on screen after Right.")
    }

    private fun AppTestScope.openRisingSunFromSetlist() {
        tap(hasText(string(Res.string.setlists)) and hasClickAction())
        // A setlist's row is numbered, which its text starts with.
        tap(hasText(RISING_SUN_TITLE, substring = true) and hasClickAction())
    }

    /** Whether a text naming the song with [title] lies within the window, which the pages next to the one shown do not. */
    private fun AppTestScope.isTitleOnScreen(title: String): Boolean {
        val window = onRoot().fetchSemanticsNode().boundsInRoot
        return onAllNodes(hasText(title, substring = true), useUnmergedTree = true).fetchSemanticsNodes().any { node ->
            node.boundsInRoot.left >= window.left && node.boundsInRoot.right <= window.right && !node.boundsInRoot.isEmpty
        }
    }
}

private const val PREVIOUS_SONG_TITLE = "Home on the Range"
private const val TWO_SONGS_SETLIST = "campfire_tests-two_songs.setlist.json"
