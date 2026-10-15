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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PastedLinesTest {

    @Test
    fun `typing and a one line paste are not pasted lines`() {
        assertNull(pastedLinesOf(listOf(4 to "a")))
        assertNull(pastedLinesOf(listOf(4 to "\n")))
        assertNull(pastedLinesOf(listOf(4 to "Hello world")))
        assertNull(pastedLinesOf(listOf(4 to "Hello world\n\n")))
    }

    @Test
    fun `a paste of two lines or more is recorded where it starts`() {
        assertEquals(PastedLines(start = 7, text = "Am  C\nHello world"), pastedLinesOf(listOf(7 to "Am  C\nHello world")))
    }

    @Test
    fun `the last of several insertions is the one recorded`() {
        assertEquals(PastedLines(start = 20, text = "G\nla"), pastedLinesOf(listOf(0 to "C\nla", 20 to "G\nla")))
    }

    @Test
    fun `a pasted chord sheet is converted and the song prettified, the caret after the paste`() {
        val paste = "Chorus:\nG       D\nSing it loud\n"
        val text = "{title: Song}\n[C]Hello\n" + paste
        val (formatted, caret) = assertNotNull(formattedAfterPaste(text, PastedLines(start = text.length - paste.length, text = paste)))
        assertEquals("{title: Song}\n\n[C]Hello\n\n{start_of_chorus: Chorus}\n[G]Sing it [D]loud\n{end_of_chorus}\n", formatted)
        assertEquals(formatted.length, caret)
    }

    @Test
    fun `pasted ChordPro is not converted but the song is still prettified`() {
        val paste = "{start_of_verse}\n[G]Hi\n{end_of_verse}\n"
        val text = "{title: Song}\n" + paste
        val (formatted, _) = assertNotNull(formattedAfterPaste(text, PastedLines(start = text.length - paste.length, text = paste)))
        assertEquals("{title: Song}\n\n{start_of_verse}\n[G]Hi\n{end_of_verse}\n", formatted)
    }

    @Test
    fun `a song that is already in shape is left as the paste made it`() {
        val text = "{title: Song}\n\n[G]One\n[C]Two\n"
        assertNull(formattedAfterPaste(text, PastedLines(start = 15, text = "[G]One\n[C]Two\n")))
    }

    @Test
    fun `a long document is not formatted on a paste`() {
        val text = "Am     C\nHello world\n" + "x".repeat(LONG_DOCUMENT_LENGTH)
        assertNull(formattedAfterPaste(text, PastedLines(start = 0, text = "Am     C\nHello world\n")))
    }
}
