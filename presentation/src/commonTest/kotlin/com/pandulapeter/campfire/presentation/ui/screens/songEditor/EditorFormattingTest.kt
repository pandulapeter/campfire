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

    /**
     * The text after toggling [tag] over the part of [text] between the two [marker]s, with the new selection marked the
     * same way. Tabs and grids are written with `|`, so their tests mark the selection with another character.
     */
    private fun toggled(text: String, tag: String = "b", marker: Char = '|'): String {
        val start = text.indexOf(marker)
        val end = text.indexOf(marker, start + 1) - 1
        val plain = text.replace(marker.toString(), "")
        val toggle = formattingToggle(plain, start, end, tag)
        val result = plain.replaceRange(toggle.start, toggle.end, toggle.text)
        return result.substring(0, toggle.selectionStart) + marker + result.substring(toggle.selectionStart, toggle.selectionEnd) + marker +
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

    @Test
    fun `a selection over a tab formats only the lyrics after it`() {
        assertEquals(
            "^{start_of_tab}\ne|--0--|\n{end_of_tab}\n<b>Words</b>^",
            toggled("^{start_of_tab}\ne|--0--|\n{end_of_tab}\nWords^", marker = '^'),
        )
    }

    @Test
    fun `a grid row and a word inside a tab line are left alone`() {
        assertEquals("{sog}\n^| C . | G . |\n^{eog}", toggled("{sog}\n^| C . | G . |\n^{eog}", marker = '^'))
        assertEquals("{start_of_tab}\ne|--^0^--|\n{end_of_tab}", toggled("{start_of_tab}\ne|--^0^--|\n{end_of_tab}", marker = '^'))
    }

    @Test
    fun `a selection of several lines formats a comment's value, but never a Campfire 3 heading`() {
        assertEquals("|<b>One</b>\n{c: <b>note</b>}|", toggled("|One\n{c: note}|"))
        assertEquals("|<b>One</b>\n{c: Verse 1}|", toggled("|One\n{c: Verse 1}|"))
    }

    @Test
    fun `lines ended by a bare CR are formatted one by one`() {
        assertEquals("|<b>One</b>\r<b>Two</b>|", toggled("|One\rTwo|"))
    }
}
