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

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performSemanticsAction
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.KEYBOARD_HEIGHT
import com.pandulapeter.campfire.screenshots.TestWindow
import com.pandulapeter.campfire.screenshots.openEditor
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.textLayout
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The editor's controls taking the lines a phone has for the song's text: on the smallest phone, with the keyboard down
 * as the editor opens and up while it is typed into, the text field shows at least [MIN_VISIBLE_LINES] of its own lines
 * between the controls above it and the window's bottom or the keyboard. Five lines is the least that still reads as a
 * place to write a song, a verse and the chords over it, rather than a strip under the controls.
 */
@OptIn(ExperimentalTestApi::class)
internal class EditorRoomTest {

    @Test
    fun `the editor leaves a phone five lines of the song with the keyboard down and up`() = runAppTest(window = TestWindow.PHONE) {
        openEditor()
        assertRoomForText(case = "With the keyboard down")
        onNode(hasSetTextAction()).performSemanticsAction(SemanticsActions.RequestFocus)
        keyboardHeight = KEYBOARD_HEIGHT
        waitForIdle()
        assertRoomForText(case = "With the keyboard up")
    }

    /** Checks that the field's part the window shows over the keyboard is at least [MIN_VISIBLE_LINES] of its lines tall. */
    private fun AppTestScope.assertRoomForText(case: String) {
        val field = onNode(hasSetTextAction()).fetchSemanticsNode()
        val layout = textLayout(hasSetTextAction())
        val lineHeight = layout.getLineBottom(0) - layout.getLineTop(0)
        val shown = field.boundsInRoot.intersect(Rect(0f, 0f, TestWindow.PHONE.width.value, TestWindow.PHONE.height.value - keyboardHeight.value))
        val lines = shown.height / lineHeight
        assertTrue(lines >= MIN_VISIBLE_LINES, "$case, the editor shows ${"%.1f".format(lines)} lines of the song ($shown, lines $lineHeight apart).")
    }
}

private const val MIN_VISIBLE_LINES = 5
