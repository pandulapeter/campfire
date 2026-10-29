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

import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals

class EditorArrowKeysTest {

    @Test
    fun `a step skips a letter's points and a surrogate pair whole`() {
        val text = "שָׁל😀x"
        assertEquals(3, nextGraphemeOffset(text, 0))
        assertEquals(0, previousGraphemeOffset(text, 3))
        assertEquals(6, nextGraphemeOffset(text, 4))
        assertEquals(4, previousGraphemeOffset(text, 6))
        assertEquals(text.length, nextGraphemeOffset(text, text.length))
        assertEquals(0, previousGraphemeOffset(text, 0))
    }

    @Test
    fun `a line break of two characters is one step`() {
        val text = "a\r\nb"
        assertEquals(3, nextGraphemeOffset(text, 1))
        assertEquals(1, previousGraphemeOffset(text, 3))
    }

    @Test
    fun `Shift moves only the end and a selection collapses to its end in the direction moved`() {
        val text = "abcdef"
        assertEquals(TextRange(4, 2), arrowKeySelection(text, TextRange(4, 3), isForward = false, isExtending = true))
        assertEquals(TextRange(2), arrowKeySelection(text, TextRange(4, 2), isForward = false, isExtending = false))
        assertEquals(TextRange(4), arrowKeySelection(text, TextRange(4, 2), isForward = true, isExtending = false))
        assertEquals(TextRange(3), arrowKeySelection(text, TextRange(2), isForward = true, isExtending = false))
    }
}
