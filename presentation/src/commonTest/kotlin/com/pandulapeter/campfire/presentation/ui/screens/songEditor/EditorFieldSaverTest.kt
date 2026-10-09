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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorFieldSaverTest {

    private fun saver(recovered: () -> TextFieldState?) = EditorFieldSaver(
        retain = {},
        retained = { null },
        recovered = recovered,
        fileText = { "file" },
    )

    @Test
    fun `a long document restored without its text takes the recovered draft`() {
        val field = saver(recovered = { TextFieldState("draft") }).restore(listOf(true))
        assertEquals("draft", field.textFieldState.text.toString())
        assertFalse(field.isDraftLost)
    }

    @Test
    fun `a long document with no recovered draft opens on the file and says the draft is lost`() {
        val field = saver(recovered = { null }).restore(listOf(true))
        assertEquals("file", field.textFieldState.text.toString())
        assertTrue(field.isDraftLost)
    }

    @Test
    fun `a short document restores its own text and caret over a recovered draft`() {
        val field = saver(recovered = { TextFieldState("draft") }).restore(listOf("short", 1, 2))
        assertEquals("short", field.textFieldState.text.toString())
        assertEquals(TextRange(1, 2), field.textFieldState.selection)
        assertFalse(field.isDraftLost)
    }
}
