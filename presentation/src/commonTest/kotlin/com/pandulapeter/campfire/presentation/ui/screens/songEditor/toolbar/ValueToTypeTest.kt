/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor.toolbar

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValueToTypeTest {

    @Test
    fun `a directive written at the caret leaves its value to type`() {
        assertTrue(leavesValueToTypeAfter("Hello world", TextRange(5), EditorInsertion.directive(label = "Comment", name = "comment")))
    }

    @Test
    fun `a chord written at the caret leaves its name to type`() {
        assertTrue(leavesValueToTypeAfter("Hello world", TextRange(6), EditorInsertion(label = "Chord", prefix = "[", suffix = "]", isOwnLine = false)))
    }

    @Test
    fun `a new header line leaves its value to type`() {
        assertTrue(leavesValueToTypeAfter("[C]Hello", TextRange(8), EditorInsertion.metadata(label = "Title", name = "title")))
    }

    @Test
    fun `a header line already there has its value selected to be typed over`() {
        assertTrue(leavesValueToTypeAfter("{title: Hello}\n[C]Hello", TextRange(23), EditorInsertion.metadata(label = "Title", name = "title")))
    }

    @Test
    fun `a section opened at the caret leaves nothing to type`() {
        assertFalse(leavesValueToTypeAfter("Hello", TextRange(5), EditorInsertion.section(label = "Verse", name = "verse")))
    }

    @Test
    fun `a recalled chorus leaves nothing to type`() {
        assertFalse(leavesValueToTypeAfter("Hello", TextRange(5), EditorInsertion.directive(label = "Chorus", name = "chorus", hasValue = false)))
    }

    @Test
    fun `an empty tab staff leaves nothing to type`() {
        assertFalse(leavesValueToTypeAfter("Hello", TextRange(5), EditorInsertion.tab(label = "Tab", selector = null, staff = listOf("e|---|", "B|---|"))))
    }

    @Test
    fun `a selection wrapped in a chord leaves nothing to type`() {
        assertFalse(leavesValueToTypeAfter("Hello world", TextRange(6, 11), EditorInsertion(label = "Chord", prefix = "[", suffix = "]", isOwnLine = false)))
    }

    @Test
    fun `a selection wrapped in a section leaves nothing to type`() {
        assertFalse(leavesValueToTypeAfter("Hello\nworld", TextRange(0, 11), EditorInsertion.section(label = "Verse", name = "verse")))
    }

    private fun leavesValueToTypeAfter(text: String, selection: TextRange, insertion: EditorInsertion): Boolean {
        val state = TextFieldState(text, selection)
        state.insert(insertion)
        return leavesValueToType(
            textBefore = text,
            selectionBefore = selection,
            textAfter = state.text.toString(),
            selectionAfter = state.selection,
        )
    }
}
