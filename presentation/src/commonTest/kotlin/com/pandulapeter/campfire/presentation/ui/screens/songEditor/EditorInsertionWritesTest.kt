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

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals

class EditorInsertionWritesTest {

    @Test
    fun `a bracketed insertion goes around the selection inside the line`() {
        val state = TextFieldState("Hello world", TextRange(6, 11))

        state.insert(EditorInsertion(label = "Chord", prefix = "[", suffix = "]", isOwnLine = false))

        assertEquals("Hello [world]", state.text.toString())
        assertEquals(TextRange(7, 12), state.selection)
    }

    @Test
    fun `a directive at a caret in the middle of a line gets a line of its own`() {
        val state = TextFieldState("Hello world", TextRange(5))

        state.insert(EditorInsertion.directive(label = "Comment", name = "comment"))

        assertEquals("Hello\n{comment: }\n world", state.text.toString())
        assertEquals(TextRange(16), state.selection)
    }

    @Test
    fun `a section is written around what is selected`() {
        val state = TextFieldState("La la", TextRange(0, 5))

        state.insert(EditorInsertion.section(label = "Chorus", name = "chorus"))

        assertEquals("{start_of_chorus}\nLa la\n{end_of_chorus}", state.text.toString())
    }

    @Test
    fun `a tab with nothing selected puts the caret after the name of its top string`() {
        val state = TextFieldState("", TextRange(0))

        state.insert(EditorInsertion.tab(label = "Tab", selector = "guitar", staff = listOf("e|---|", "B|---|")))

        assertEquals("{start_of_tab-guitar}\ne|---|\nB|---|\n{end_of_tab}", state.text.toString())
        assertEquals(TextRange("{start_of_tab-guitar}\ne|".length), state.selection)
    }

    @Test
    fun `metadata goes into the header wherever the caret is`() {
        val text = "{title: Song}\n\nLa la"
        val state = TextFieldState(text, TextRange(text.length))

        state.insert(EditorInsertion.metadata(label = "Artist", name = "artist"))

        assertEquals("{title: Song}\n{artist: }\n\nLa la", state.text.toString())
        assertEquals(TextRange("{title: Song}\n{artist: ".length), state.selection)
    }
}
