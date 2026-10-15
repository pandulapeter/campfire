/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.forms

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.close
import com.pandulapeter.campfire.presentation.resources.create
import com.pandulapeter.campfire.presentation.resources.songs_create_song
import com.pandulapeter.campfire.presentation.resources.songs_new_song
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.runAppTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
internal class NewSongDialogTest {

    @Test
    fun `a cancelled new song writes nothing`() = runAppTest {
        val before = songFileNames()
        openNewSong()
        onAllNodes(hasSetTextAction()).onFirst().performTextInput(TITLE)
        onNodeWithContentDescription(string(Res.string.close)).performClick()
        waitForIdle()
        onNodeWithText(string(Res.string.create)).assertDoesNotExist()
        // A write goes to the disk after the sheet is gone, so a second song created the same way is what tells that the
        // first one would have arrived by now.
        openNewSong()
        onAllNodes(hasSetTextAction()).onFirst().performTextInput(OTHER_TITLE)
        onNodeWithText(string(Res.string.create)).performClick()
        waitForLibrary { songFileNames().size > before.size }
        assertEquals(before + "another_song_from_a_test.cho", songFileNames())
    }

    @Test
    fun `a double-tapped create writes one file`() = runAppTest {
        val before = songFileNames()
        openNewSong()
        onAllNodes(hasSetTextAction()).onFirst().performTextInput(TITLE)
        onNodeWithText(string(Res.string.create)).performTouchInput { doubleClick() }
        waitForLibrary { songFileNames().size > before.size }
        // The song opens in the editor once it is written, which is as long as a second write would take as well.
        waitUntil(timeoutMillis = EDITOR_TIMEOUT_MILLIS) { onAllNodesWithText(TITLE).fetchSemanticsNodes().isNotEmpty() }
        waitForIdle()
        assertEquals(before + "new_song_from_a_test.cho", songFileNames())
    }

    @Test
    fun `closing a new song's editor lands on the song rather than on the list`() = runAppTest {
        val before = songFileNames()
        openNewSong()
        onAllNodes(hasSetTextAction()).onFirst().performTextInput(TITLE)
        onNodeWithText(string(Res.string.create)).performClick()
        waitForLibrary { songFileNames().size > before.size }
        waitUntil(timeoutMillis = EDITOR_TIMEOUT_MILLIS) { onAllNodesWithContentDescription(string(Res.string.close)).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithContentDescription(string(Res.string.close)).performClick()
        waitForIdle()
        // The song list's New song button is only there on the list; the song's own screen shows its title.
        onNodeWithContentDescription(string(Res.string.songs_new_song)).assertDoesNotExist()
        onAllNodesWithText(TITLE).onFirst().assertExists()
    }

    private fun AppTestScope.openNewSong() {
        onNodeWithContentDescription(string(Res.string.songs_new_song)).performClick()
        onNodeWithText(string(Res.string.songs_create_song)).performClick()
    }
}

private const val TITLE = "New Song from a Test"
private const val OTHER_TITLE = "Another Song from a Test"
private const val EDITOR_TIMEOUT_MILLIS = 10_000L
