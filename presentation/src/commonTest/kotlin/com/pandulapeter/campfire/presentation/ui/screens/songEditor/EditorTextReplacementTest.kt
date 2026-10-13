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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.insert
import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalFoundationApi::class)
class EditorTextReplacementTest {

    private fun typedInto(text: String) = TextFieldState(text).apply { edit { insert(0, "z") } }

    @Test
    fun `replacing matches writes over every one and leaves the caret at the first`() {
        val state = TextFieldState("a-b-a-b")
        state.replaceMatches(starts = listOf(0, 4), length = 1, replacement = "xy")
        assertEquals("xy-b-xy-b", state.text.toString())
        assertEquals(TextRange(0), state.selection)
    }

    @Test
    fun `replacing several matches in a short document keeps the history before it`() {
        val state = typedInto("a-b-a-b")
        state.replaceMatches(starts = listOf(1, 5), length = 1, replacement = "xy")
        state.undoState.undo()
        assertEquals("za-b-a-b", state.text.toString())
        assertTrue(state.undoState.canUndo)
    }

    @Test
    fun `replacing several matches in a long document starts the history over`() {
        val state = typedInto("ab ".repeat(20_000))
        val before = state.text.toString()
        state.replaceMatches(starts = listOf(1, 4), length = 2, replacement = "xy")
        state.undoState.undo()
        assertEquals(before, state.text.toString())
        assertFalse(state.undoState.canUndo)
    }

    @Test
    fun `replacing a single match in a long document keeps the history before it`() {
        val state = typedInto("ab ".repeat(20_000))
        val before = state.text.toString()
        state.replaceMatches(starts = listOf(1), length = 2, replacement = "xy")
        state.undoState.undo()
        assertEquals(before, state.text.toString())
        assertTrue(state.undoState.canUndo)
    }
}
