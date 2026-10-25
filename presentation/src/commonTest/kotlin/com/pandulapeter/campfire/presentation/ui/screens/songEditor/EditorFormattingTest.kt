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

class EditorFormattingTest {

    /** The text after toggling [tag] over the part of [text] between the two `|`s, with the new selection marked the same way. */
    private fun toggled(text: String, tag: String = "b"): String {
        val start = text.indexOf('|')
        val end = text.indexOf('|', start + 1) - 1
        val plain = text.replace("|", "")
        val toggle = formattingToggle(plain, start, end, tag)
        val result = plain.replaceRange(toggle.start, toggle.end, toggle.text)
        return result.substring(0, toggle.selectionStart) + "|" + result.substring(toggle.selectionStart, toggle.selectionEnd) + "|" +
            result.substring(toggle.selectionEnd)
    }

    @Test
    fun `a selection is wrapped in the tags and stays selected`() {
        assertEquals("There <b>|is a|</b> house", toggled("There |is a| house"))
    }

    @Test
    fun `toggling again takes the tags off, whether they stand around the selection or inside it`() {
        assertEquals("There |is a| house", toggled("There <b>|is a|</b> house"))
        assertEquals("There |is a| house", toggled("There |<B>is a</B>| house"))
    }

    @Test
    fun `with nothing selected the caret lands inside an empty pair, which a second toggle takes away`() {
        assertEquals("a <i>||</i>b", toggled("a ||b", tag = "i"))
        assertEquals("a ||b", toggled("a <i>||</i>b", tag = "i"))
    }

    @Test
    fun `another style's tags around the selection are left alone`() {
        assertEquals("<i><u>|word|</u></i>", toggled("<i>|word|</i>", tag = "u"))
    }

    @Test
    fun `a selection of several lines formats each lyric line, leaving directives and whitespace outside`() {
        val formatted = toggled("|{start_of_verse}\n  [Am]One line \n\n# note\nTwo|")

        assertEquals("|{start_of_verse}\n  <b>[Am]One line</b> \n\n# note\n<b>Two</b>|", formatted)
        assertEquals("|{start_of_verse}\n  [Am]One line \n\n# note\nTwo|", toggled(formatted))
    }

    @Test
    fun `lines of which only some are formatted are all formatted`() {
        assertEquals("|<b>one</b>\n<b>two</b>|", toggled("|<b>one</b>\ntwo|"))
    }
}
