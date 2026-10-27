/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.edit

import com.pandulapeter.campfire.chordpro.model.ChordProLine.Lyrics.Formatting.Style
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChordProFormattingTagsTest {

    /** [text] with [style] taken off the tags around the part between the two `|`s, the content marked again, or null. */
    private fun around(text: String, style: Style = Style.BOLD) = marked(text) { plain, start, end ->
        ChordProFormattingTags.around(plain, start, end, style, plain.indices)
    }

    /** [around] for tags at the edges of the marked part. */
    private fun inside(text: String, style: Style = Style.BOLD) = marked(text) { plain, start, end -> ChordProFormattingTags.inside(plain, start, end, style) }

    private fun marked(text: String, removal: (String, Int, Int) -> ChordProFormattingTags.Removal?): String? {
        val start = text.indexOf('|')
        val end = text.indexOf('|', start + 1) - 1
        val plain = text.replace("|", "")
        val result = removal(plain, start, end) ?: return null
        val edited = plain.replaceRange(result.start, result.end, result.text)
        val contentEnd = result.contentStart + result.contentLength
        return edited.substring(0, result.contentStart) + '|' + edited.substring(result.contentStart, contentEnd) + '|' + edited.substring(contentEnd)
    }

    @Test
    fun `a tag that is the style itself goes with its closing tag, in any spelling`() {
        assertEquals("a |b| c", around("a <B>|b|</B> c"))
        assertEquals("a |b| c", inside("a |<strong>b</strong>| c"))
        assertEquals("|b|", around("<em>|b|</em>", Style.ITALIC))
    }

    @Test
    fun `a span loses only what sets the style, and goes once nothing is left in it`() {
        assertEquals("|b|", around("<span weight=\"bold\">|b|</span>"))
        assertEquals("<span foreground='red'>|b|</span>", around("<span weight=\"bold\" foreground='red'>|b|</span>"))
        assertEquals("<span style=\"italic\">|b|</span>", around("<span font_weight=700 style=\"italic\">|b|</span>"))
        assertEquals("<span font_desc=\"Sans 12\">|b|</span>", around("<span font_desc=\"Sans Bold 12\">|b|</span>"))
        assertEquals("<span font_desc=\"Sans\">|b|</span>", around("<span font_desc=\"Bold Sans\">|b|</span>"))
        assertEquals("|b|", around("<span font_desc=\"Bold\">|b|</span>"))
    }

    @Test
    fun `a CSS style loses the declaration that sets the style`() {
        assertEquals("<span style=\"color: red\">|b|</span>", around("<span style=\"font-weight: bold; color: red\">|b|</span>"))
        assertEquals("<span style=\"color: red; \">|b|</span>", around("<span style=\"color: red; text-decoration: underline\">|b|</span>", Style.UNDERLINE))
        assertEquals("|b|", inside("|<span style=\"font-style: oblique;\">b</span>|", Style.ITALIC))
    }

    @Test
    fun `tags that do not set the style, or do not pair, are not a removal`() {
        assertNull(around("<span weight=\"normal\">|b|</span>"))
        assertNull(around("<i>|b|</i>"))
        assertNull(around("<b>|b|</strong>"))
        assertNull(inside("|<b>b</b> c|"))
        assertNull(around("<b>a |b|</b>"))
    }

    @Test
    fun `tags side by side are not a pair, and nested ones of one name are`() {
        assertNull(inside("|<span weight=\"bold\">a</span> <span color=\"red\">b</span>|"))
        assertNull(inside("|<b>a</b> <b>b</b>|"))
        assertNull(around("<b>|a</b> <b>b|</b>"))
        assertNull(inside("|<b>a</strong> <strong>b</b>|"))
        assertEquals("|a <strong>b</strong> c|", inside("|<b>a <strong>b</strong> c</b>|"))
        assertEquals("|a <span color=\"red\">b</span> c|", inside("|<span weight=\"bold\">a <span color=\"red\">b</span> c</span>|"))
    }
}
