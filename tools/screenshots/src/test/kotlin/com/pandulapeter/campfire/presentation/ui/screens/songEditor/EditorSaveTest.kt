/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.back
import com.pandulapeter.campfire.presentation.resources.close
import com.pandulapeter.campfire.presentation.resources.create
import com.pandulapeter.campfire.presentation.resources.save
import com.pandulapeter.campfire.presentation.resources.song_editor_discard
import com.pandulapeter.campfire.presentation.resources.songs_actions
import com.pandulapeter.campfire.presentation.resources.songs_create_song
import com.pandulapeter.campfire.presentation.resources.songs_edit_song
import com.pandulapeter.campfire.presentation.resources.songs_new_song
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.HOUSE_OF_THE_RISING_SUN
import com.pandulapeter.campfire.screenshots.demoSongText
import com.pandulapeter.campfire.screenshots.runAppTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
internal class EditorSaveTest {

    @Test
    fun `the editor's Save writes the typed line into the song file`() = runAppTest {
        val before = otherSongs()
        openEditor()
        typeLine()
        onNodeWithContentDescription(string(Res.string.save)).performClick()
        waitForLibrary { TYPED_LINE in houseSong() }
        assertSavedWithTypedLine()
        assertEquals(before, otherSongs())
    }

    @Test
    fun `Ctrl+S saves like the Save button`() = runAppTest {
        val before = otherSongs()
        openEditor()
        typeLine()
        onRoot().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.S) } }
        waitForLibrary { TYPED_LINE in houseSong() }
        assertSavedWithTypedLine()
        assertEquals(before, otherSongs())
    }

    @Test
    fun `discarding the unsaved changes leaves the file as it was`() = runAppTest {
        val songsBefore = songFileNames()
        openEditor()
        typeLine()
        onNodeWithContentDescription(string(Res.string.close)).performClick()
        onNode(hasText(string(Res.string.song_editor_discard)) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        waitForIdle()
        // A write goes to the disk off the main thread, so a song created afterwards is what tells that a save of the
        // discarded text would have arrived by now.
        onNodeWithContentDescription(string(Res.string.back)).performClick()
        waitForIdle()
        onNodeWithContentDescription(string(Res.string.songs_new_song)).performClick()
        onNodeWithText(string(Res.string.songs_create_song)).performClick()
        onAllNodes(hasSetTextAction())[0].performTextInput(NEW_SONG_TITLE)
        onNodeWithText(string(Res.string.create)).performClick()
        waitForLibrary { songFileNames().size > songsBefore.size }
        assertEquals(demoSongText(HOUSE_OF_THE_RISING_SUN), houseSong())
    }

    /** Opens the house song's editor from Songs, through its details screen's actions. */
    private fun AppTestScope.openEditor() {
        tap(hasText(RISING_SUN_TITLE) and hasClickAction())
        tap(hasContentDescription(string(Res.string.songs_actions)))
        tap(hasText(string(Res.string.songs_edit_song)) and hasClickAction())
    }

    private fun AppTestScope.tap(matcher: SemanticsMatcher) {
        onNode(matcher).performTouchInput { click() }
        waitForIdle()
    }

    /** Types [TYPED_LINE] wherever the field puts it: which line it lands on is not what is tested. */
    private fun AppTestScope.typeLine() {
        onNode(hasSetTextAction()).performTextInput(TYPED_LINE)
        waitForIdle()
    }

    private fun AppTestScope.houseSong() = File(songsFolder, HOUSE_OF_THE_RISING_SUN).readText()

    /** Every song file but the house song, with its text. */
    private fun AppTestScope.otherSongs() = (songFileNames() - HOUSE_OF_THE_RISING_SUN).associateWith { File(songsFolder, it).readText() }

    private fun AppTestScope.assertSavedWithTypedLine() =
        assertEquals(demoSongText(HOUSE_OF_THE_RISING_SUN), houseSong().replaceFirst(TYPED_LINE, ""))
}

private const val RISING_SUN_TITLE = "House of the Rising Sun"
private const val TYPED_LINE = "\nA line typed by a test"
private const val NEW_SONG_TITLE = "Song from an Editor Test"
