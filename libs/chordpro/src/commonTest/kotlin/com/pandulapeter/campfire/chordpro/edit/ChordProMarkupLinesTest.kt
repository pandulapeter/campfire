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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChordProMarkupLinesTest {

    private val song = "{title: Song}\n[C]Hello\n\n{sog}\n| C . | G . |\n{eog}\n{start_of_tab}\ne|--0--|\n{end_of_tab}\n" +
        "{start_of_abc}\n[CEG]\n{end_of_abc}\n# note\n"

    /** The text [range] covers in [text], or null. */
    private fun covered(text: String, range: IntRange?) = range?.let { text.substring(it.first, it.last + 1) }

    /** The range markup is read in on the only line of [line]. */
    private fun rangeOf(line: String) = ChordProMarkupLines.markupRanges(line).single()

    @Test
    fun `a lyric line is read whole and a blank line as an empty range`() {
        val ranges = ChordProMarkupLines.markupRanges(song)

        assertEquals("[C]Hello", covered(song, ranges[1]))
        val blank = ranges[2]
        assertTrue(blank != null && blank.isEmpty() && blank.first == song.indexOf("\n\n") + 1)
    }

    @Test
    fun `grid, tab, and delegated lines, other directives, and source comments are read nowhere`() {
        val ranges = ChordProMarkupLines.markupRanges(song)

        listOf(0, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12).forEach { assertNull(ranges[it], "line $it") }
    }

    @Test
    fun `a comment is read in its value only`() {
        assertEquals("Play <b>slowly</b>", covered("{c: Play <b>slowly</b>}", rangeOf("{c: Play <b>slowly</b>}")))
        assertEquals("x", covered("{highlight: x}", rangeOf("{highlight: x}")))
        assertEquals("note", covered("{start_of_tab}\n{c: note}", ChordProMarkupLines.markupRanges("{start_of_tab}\n{c: note}")[1]))
    }

    @Test
    fun `an empty comment value is an empty range at the brace, and a comment with no value is read nowhere`() {
        assertEquals(4 until 4, rangeOf("{c: }"))
        assertNull(rangeOf("{c}"))
    }

    @Test
    fun `leading whitespace counts into the value's offsets`() {
        val text = "Words\n  {c: note}"

        assertEquals("note", covered(text, ChordProMarkupLines.markupRanges(text)[1]))
    }

    @Test
    fun `a selector name is not a comment, and in delegated notation only a comment with a colon is`() {
        assertNull(rangeOf("{c-guitar: x}"))
        val abc = "{start_of_abc}\n{c note}\n{c: note}\n{end_of_abc}"
        val ranges = ChordProMarkupLines.markupRanges(abc)

        assertNull(ranges[1])
        assertEquals("note", covered(abc, ranges[2]))
    }

    @Test
    fun `a Campfire 3 heading is read nowhere, but the same comment in a section or in italics is`() {
        assertNull(rangeOf("{c: Chorus}"))
        val verse = "{start_of_verse}\n{c: Chorus}"
        assertEquals("Chorus", covered(verse, ChordProMarkupLines.markupRanges(verse)[1]))
        assertEquals("Chorus", covered("{ci: Chorus}", rangeOf("{ci: Chorus}")))
    }

    @Test
    fun `CRLF and bare CR line endings shift the ranges by their extra characters`() {
        val lf = "One\n{c: two}\nThree"

        listOf("\r\n", "\r").forEach { separator ->
            val text = lf.replace("\n", separator)
            assertEquals(
                ChordProMarkupLines.markupRanges(lf).map { covered(lf, it) },
                ChordProMarkupLines.markupRanges(text).map { covered(text, it) },
            )
        }
        assertEquals(listOf(0 until 3, 9 until 12, 15 until 20), ChordProMarkupLines.markupRanges("One\r\n{c: two}\r\nThree"))
    }

    @Test
    fun `a long song has a range entry for every line`() {
        val text = buildString { while (length < 60_000) append("[C]Hello there\n{c: note}\n") }

        assertEquals(text.count { it == '\n' }, ChordProMarkupLines.markupRanges(text).size)
    }
}
