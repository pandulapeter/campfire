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

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChordProHeaderTest {

    @Test
    fun `a directive is written into the header rather than at the caret`() {
        val text = "{title: T}\n\n{start_of_verse}\nThe first line\n{end_of_verse}"

        assertEquals("{title: T}\n{artist: }\n\n{start_of_verse}\nThe first line\n{end_of_verse}", text.insert("artist"))
    }

    @Test
    fun `a header written in the app's order stays in it`() {
        val text = "{title: T}\n{artist: A}\n{capo: 2}\n\nThe first line"

        assertEquals("{title: T}\n{artist: A}\n{key: }\n{capo: 2}\n\nThe first line", text.insert("key"))
    }

    @Test
    fun `a header the user has arranged their own way is left as it is`() {
        val text = "{artist: A}\n{title: T}\n\nThe first line"

        assertEquals("{artist: A}\n{title: T}\n{album: }\n\nThe first line", text.insert("album"))
    }

    @Test
    fun `a directive that comes before everything the song declares opens the header`() {
        val text = "{artist: A}\n\nThe first line"

        assertEquals("{title: }\n{artist: A}\n\nThe first line", text.insert("title"))
    }

    @Test
    fun `the short spelling of a directive is what it is ordered by`() {
        val text = "{t: T}\n{capo: 2}\n\nThe first line"

        assertEquals("{t: T}\n{year: }\n{capo: 2}\n\nThe first line", text.insert("year"))
    }

    @Test
    fun `a repeatable directive is written after the last one of its kind`() {
        val text = "{title: T}\n{tag: slow}\n{tag: campfire}\n\nThe first line"

        assertEquals("{title: T}\n{tag: slow}\n{tag: campfire}\n{tag: }\n\nThe first line", text.insert("tag"))
    }

    @Test
    fun `a language is written after the ones the song already declares, whichever spelling they use`() {
        val text = "{title: T}\n{meta: lang hu}\n{capo: 2}\n\nThe first line"

        assertEquals(
            "{title: T}\n{meta: lang hu}\n{meta: language }\n{capo: 2}\n\nThe first line",
            text.insert("language", prefix = "{meta: language "),
        )
    }

    @Test
    fun `a song that opens with its lyrics gets the directive above them`() {
        assertEquals("{title: }\nThe first line", "The first line".insert("title"))
    }

    @Test
    fun `a file that ends with its header takes the line break with the directive`() {
        assertEquals("{title: T}\n{artist: }", "{title: T}".insert("artist"))
    }

    @Test
    fun `a file that ends with a line break does not grow another one`() {
        assertEquals("{title: T}\n{artist: }\n", "{title: T}\n".insert("artist"))
    }

    @Test
    fun `an empty file is a header of one directive`() {
        assertEquals("{title: }\n", "".insert("title"))
    }

    @Test
    fun `the caret lands between the two halves of the directive`() {
        val insertion = ChordProHeader.insert("{title: T}\n\nThe first line", name = "artist", prefix = "{artist: ", suffix = "}")

        assertEquals("{title: T}\n{artist: ".length, insertion.caretOffset)
    }

    @Test
    fun `a directive is written into a CRLF file with its line endings and at the right offset`() {
        val text = "{title: T}\r\n{capo: 2}\r\n\r\nThe first line\r\n"
        val insertion = ChordProHeader.insert(text, name = "artist", prefix = "{artist: ", suffix = "}")

        assertEquals("{title: T}\r\n".length, insertion.offset)
        assertEquals("{title: T}\r\n{artist: ".length, insertion.caretOffset)
        assertEquals("{title: T}\r\n{artist: }\r\n{capo: 2}\r\n\r\nThe first line\r\n", text.insert("artist"))
    }

    @Test
    fun `a file mixing its line endings gets the directive at the start of a line`() {
        assertEquals("{title: T}\n{year: }\r\n{capo: 2}\r\nThe first line", "{title: T}\n{capo: 2}\r\nThe first line".insert("year"))
    }

    @Test
    fun `a CRLF file that ends with its header takes a CRLF with the directive`() {
        assertEquals("{title: T}\r\n{artist: A}\r\n{key: }", "{title: T}\r\n{artist: A}".insert("key"))
    }

    @Test
    fun `a directive added to a CR-only file is written with its line ending`() {
        assertEquals("{title: T}\r{artist: }\r{capo: 2}\r\rThe first line\r", "{title: T}\r{capo: 2}\r\rThe first line\r".insert("artist"))
    }

    @Test
    fun `a directive the song already declares has its value selected rather than a second one written`() {
        val text = "{title: Song}\n{artist: Singer}\n\n[C]The first line"

        assertEquals(text to (8..12), text.write("title", caret = text.length))
    }

    @Test
    fun `every spelling of a declared directive is found, wherever it stands`() {
        assertEquals("{t: T}\n{st: S}" to (12..13), "{t: T}\n{st: S}".write("subtitle"))
        assertEquals("{meta: key G}" to (11..12), "{meta: key G}".write("key"))
        assertEquals("[C]A line\n{year: 1970}" to (17..21), "[C]A line\n{year: 1970}".write("year"))
    }

    @Test
    fun `a declared directive with no value yet is the line typed into`() {
        assertEquals("{title: }\n\nThe first line" to (8..8), "{title}\n\nThe first line".write("title"))
    }

    @Test
    fun `a selection becomes the value of a directive the song lacks`() {
        assertEquals("{title: Song}\n\nSong" to (8..12), "\nSong".write("title", value = "Song", caret = 1))
    }

    @Test
    fun `a selection replaces the value of a directive the song declares`() {
        val text = "{title: Old}\n{artist: A}\n\nNew title"

        assertEquals("{title: New title}\n{artist: A}\n\nNew title" to (8..17), text.write("title", value = "New title"))
        assertEquals("{meta: key G}" to (11..12), "{meta: key C}".write("key", value = "G"))
    }

    @Test
    fun `a selection is written as one line without braces`() {
        assertEquals("{title: A B}\n" to (8..11), "".write("title", value = " A\r\n{B} "))
        assertEquals("{title: T}" to (8..9), "{title: T}".write("title", value = " \n "))
    }

    @Test
    fun `a repeatable directive is written again, with the selection as its value`() {
        assertEquals("{tag: Folk}\n{tag: Slow}\nSlow" to (18..22), "{tag: Folk}\nSlow".write("tag", value = "Slow"))
    }

    @Test
    fun `a changeable directive takes the selection as the value of the line it writes or selects`() {
        val text = "{tempo: 120}\n\n[C]The first line\n[G]The second line"

        assertEquals("{tempo: 90}\n\n[C]The first line\n[G]The second line" to (8..10), text.write("tempo", value = "90", caret = 0))
        assertEquals(
            "{tempo: 120}\n\n[C]The first line\n{tempo: 90}\n[G]The second line" to (40..42),
            text.write("tempo", value = "90", caret = text.indexOf("second")),
        )
    }

    @Test
    fun `the tags, the languages and the links of a song are the repeatable directives`() {
        assertEquals(setOf("tag", "language", "link"), ChordProHeader.repeatableMetadata)
    }

    @Test
    fun `a changeable directive the header lacks is written into the header wherever the caret is`() {
        val text = "{title: T}\n\n[C]The first line"

        assertEquals("{title: T}\n{tempo: }\n\n[C]The first line" to (19..19), text.insertChangeable("tempo", caret = text.length))
    }

    @Test
    fun `an empty header line of a changeable directive is the one typed into`() {
        val text = "{title: T}\n{tempo}\n\n[C]The first line"

        assertEquals("{title: T}\n{tempo: }\n\n[C]The first line" to (19..19), text.insertChangeable("tempo", caret = text.length))
    }

    @Test
    fun `a changeable directive is written above the caret's line in the body`() {
        val text = "{tempo: 120}\n\n[C]The first line\n[G]The second line"

        assertEquals(
            "{tempo: 120}\n\n[C]The first line\n{tempo: }\n[G]The second line" to (40..40),
            text.insertChangeable("tempo", caret = text.indexOf("second")),
        )
        assertEquals(
            "{tempo: 120}\n\n[C]The first line\n[G]The second line\n{time: }\n" to (58..58),
            "$text\n".let { it.insertChangeable("time", caret = it.length, header = "{time: 4/4}\n") },
        )
    }

    @Test
    fun `a changeable directive with the caret in the header selects the header's value`() {
        val text = "{meta: tempo 120}\n\n[C]The first line"

        assertEquals(text to (13..16), text.insertChangeable("tempo", caret = 3))
        assertEquals("{tempo: 120}" to (8..11), "{tempo: 120}".insertChangeable("tempo", caret = 12))
    }

    @Test
    fun `a changeable directive with the caret on the first line of the song selects the header's value`() {
        val text = "{title: T}\n{tempo: 120}\n\n[C]The first line"

        assertEquals(text to (19..22), text.insertChangeable("tempo", caret = text.indexOf("[C]")))
        assertEquals(text to (19..22), text.insertChangeable("tempo", caret = text.length))
    }

    @Test
    fun `a changeable directive is only written below the first line inside an opening environment`() {
        val text = "{tempo: 120}\n\n{start_of_verse}\n[C]a\n[G]b\n{end_of_verse}"

        assertEquals(text to (8..11), text.insertChangeable("tempo", caret = text.indexOf("{start_of_verse}")))
        assertEquals(text to (8..11), text.insertChangeable("tempo", caret = text.indexOf("[C]a")))
        assertEquals(
            "{tempo: 120}\n\n{start_of_verse}\n[C]a\n{tempo: }\n[G]b\n{end_of_verse}" to (44..44),
            text.insertChangeable("tempo", caret = text.indexOf("[G]b")),
        )
    }

    @Test
    fun `a chorus recall is a line of the song and a comment is not`() {
        val recall = "{tempo: 120}\n\n{chorus}\n[C]a"
        val comment = "{tempo: 120}\n\n{c: Slowly}\n[C]a"

        assertEquals("{tempo: 120}\n\n{chorus}\n{tempo: }\n[C]a" to (31..31), recall.insertChangeable("tempo", caret = recall.indexOf("[C]a")))
        assertEquals(recall to (8..11), recall.insertChangeable("tempo", caret = recall.indexOf("{chorus}")))
        assertEquals(comment to (8..11), comment.insertChangeable("tempo", caret = comment.indexOf("[C]a")))
    }

    @Test
    fun `a changeable directive written into the body is read as a change after the song has started`() {
        val placements = listOf(
            "{title: T}\n{tempo: 120}\n\n[C]The first line" to "[C]",
            "{tempo: 120}\n\n{start_of_verse}\n[C]a\n[G]b\n{end_of_verse}" to "{start_of_verse}",
            "{tempo: 120}\n\n{start_of_verse}\n[C]a\n[G]b\n{end_of_verse}" to "[C]a",
            "{tempo: 120}\n\n{start_of_verse}\n[C]a\n[G]b\n{end_of_verse}" to "[G]b",
            "{tempo: 120}\n\n{chorus}\n[C]a" to "{chorus}",
            "{tempo: 120}\n\n{chorus}\n[C]a" to "[C]a",
            "{tempo: 120}\n\n{c: Slowly}\n[C]a" to "[C]a",
        )
        placements.forEach { (text, line) ->
            val insertion = ChordProHeader.insertChangeable(text, name = "tempo", caretOffset = text.indexOf(line), prefix = "{tempo: ", suffix = "}")
            if (insertion.text.isNotEmpty()) {
                val inserted = text.replaceRange(insertion.offset, insertion.offset + insertion.replacedLength, insertion.text)
                val blocks = ChordProParser.parse(inserted.replaceRange(insertion.caretOffset, insertion.caretOffset, "90")).blocks
                assertTrue(blocks.any { it is ChordProBlock.Timing && it.tempo == "90" }, "$text at $line")
                assertFalse(blocks.first() is ChordProBlock.Timing, "$text at $line")
            }
        }
    }

    @Test
    fun `a body line of a changeable directive does not draw a new one out of the header`() {
        val text = "{title: T}\n\n[C]a\n{tempo: 90}\n[C]b"

        assertEquals("{title: T}\n{tempo: }\n\n[C]a\n{tempo: 90}\n[C]b", text.insert("tempo"))
    }

    /** The text after the insertion, and the selection after it as a range of offsets, its end exclusive as in a field. */
    private fun String.insertChangeable(name: String, caret: Int, header: String = ""): Pair<String, IntRange> {
        val text = header + this
        val insertion = ChordProHeader.insertChangeable(text, name = name, caretOffset = header.length + caret, prefix = "{$name: ", suffix = "}")
        val result = text.replaceRange(insertion.offset, insertion.offset + insertion.replacedLength, insertion.text).removePrefix(header)
        return result to (insertion.caretOffset - header.length..insertion.selectionEnd - header.length)
    }

    private fun String.write(name: String, value: String = "", caret: Int = length): Pair<String, IntRange> {
        val insertion = ChordProHeader.write(this, name = name, caretOffset = caret, prefix = "{$name: ", suffix = "}", value = value)
        return replaceRange(insertion.offset, insertion.offset + insertion.replacedLength, insertion.text) to (insertion.caretOffset..insertion.selectionEnd)
    }

    private fun String.insert(name: String, prefix: String = "{$name: ", suffix: String = "}"): String {
        val insertion = ChordProHeader.insert(this, name = name, prefix = prefix, suffix = suffix)
        return replaceRange(insertion.offset, insertion.offset, insertion.text)
    }

    @Test
    fun `a chord definition goes after the last one of the header, or at its end`() {
        val line = "{define: G frets 3 2 0 0 0 3}"
        val bare = "{title: X}\n{artist: Y}\n{tag: Z}\n\n[G]la"
        val first = ChordProHeader.insertDefinition(bare, line)
        assertEquals("{title: X}\n{artist: Y}\n{tag: Z}\n{define: G frets 3 2 0 0 0 3}\n\n[G]la", bare.substring(0, first.offset) + first.text + bare.substring(first.offset))
        assertEquals(first.offset + "{define: ".length, first.caretOffset)
        val defined = "{title: X}\n{define: C frets x 3 2 0 1 0}\n{key: G}\n[G]la"
        val second = ChordProHeader.insertDefinition(defined, line)
        assertEquals("{title: X}\n{define: C frets x 3 2 0 1 0}\n{define: G frets 3 2 0 0 0 3}\n{key: G}\n[G]la", defined.substring(0, second.offset) + second.text + defined.substring(second.offset))
        val empty = ChordProHeader.insertDefinition("", line)
        assertEquals(line, empty.text)
        val noBreak = ChordProHeader.insertDefinition("{title: X}", line)
        assertEquals("\n$line", noBreak.text)
    }
}
