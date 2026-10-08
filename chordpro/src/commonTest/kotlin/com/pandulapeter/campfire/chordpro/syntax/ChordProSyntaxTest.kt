/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.syntax

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.model.GridToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

class ChordProSyntaxTest {

    @Test
    fun `a directive is matched by its braces name and colon`() {
        assertEquals(ChordProDirectives.Directive("title", "Song"), ChordProDirectives.matchDirective("{title: Song}"))
        assertEquals(ChordProDirectives.Directive("soc", null), ChordProDirectives.matchDirective("{ soc }"))
        assertEquals(ChordProDirectives.Directive("title", ""), ChordProDirectives.matchDirective("{title: }"))
        assertEquals(ChordProDirectives.Directive("tag", "a}b"), ChordProDirectives.matchDirective("{tag: a}b}"))
    }

    @Test
    fun `a line that only looks like a directive is content`() {
        listOf("", "{", "}", "{}", "{:}", "{title", "title}", "{ti tle: x}", "{title}}", "{cím: x}", "{Verse 2}", "{verse 2}", "{Refrén 2x}").forEach {
            assertNull(ChordProDirectives.matchDirective(it))
        }
    }

    @Test
    fun `a value may be separated from a known name by whitespace alone`() {
        assertEquals(ChordProDirectives.Directive("title", "Wonderwall"), ChordProDirectives.matchDirective("{title Wonderwall}"))
        assertEquals(ChordProDirectives.Directive("start_of_verse", "Verse 1"), ChordProDirectives.matchDirective("{start_of_verse Verse 1}"))
        assertEquals(ChordProDirectives.Directive("meta", "language en"), ChordProDirectives.matchDirective("{meta language en}"))
        assertEquals(ChordProDirectives.Directive("x_note", "hi"), ChordProDirectives.matchDirective("{x_note hi}"))
        assertEquals(ChordProDirectives.Directive("title", "a: b"), ChordProDirectives.matchDirective("{title a: b}"))
    }

    @Test
    fun `a negated selector names the directive it is written on`() {
        assertEquals(ChordProDirectives.Directive("tag", "Folk"), ChordProDirectives.matchDirective("{tag-guitar!: Folk}"))
    }

    @Test
    fun `the value start is after the colon or after the whitespace`() {
        assertEquals(7, ChordProDirectives.directiveValueStart("{title: X}"))
        assertEquals(7, ChordProDirectives.directiveValueStart("{title X}"))
        assertNull(ChordProDirectives.directiveValueStart("{soc}"))
        assertNull(ChordProDirectives.directiveValueStart("{Verse 2}"))
    }

    @Test
    fun `a label is the value or its label attribute in either quotes`() {
        assertEquals("Verse 1", ChordProEnvironments.label("Verse 1"))
        assertEquals("Verse 1", ChordProEnvironments.label("label=\"Verse 1\""))
        assertEquals("Verse 1", ChordProEnvironments.label("label='Verse 1'"))
        assertEquals("Solo", ChordProEnvironments.label("shape=\"1+4x2+4\" label=\"Solo\""))
        assertNull(ChordProEnvironments.label("shape=\"1+4x2+4\""))
        assertNull(ChordProEnvironments.label("  "))
        assertNull(ChordProEnvironments.label("label=\"\""))
    }

    @Test
    fun `brackets are paired from the left`() {
        assertEquals(listOf(0..3 to "Am", 7..11 to "G/B"), ChordProDirectives.brackets("[Am]la [G/B]la").map { it.range to it.content })
        assertEquals(listOf(0..4 to "[Am"), ChordProDirectives.brackets("[[Am]").map { it.range to it.content })
        assertEquals(listOf(0..3 to "Am"), ChordProDirectives.brackets("[Am] [C").map { it.range to it.content })
    }

    @Test
    fun `crafted lines cost no more than their length`() {
        assertNull(assertLinear { ChordProDirectives.matchDirective("{c:" + " ".repeat(4_000) + "x") })
        assertNull(assertLinear { ChordProDirectives.matchDirective("{title" + " ".repeat(4_000) + "x") })
        assertLinear { ChordProEnvironments.label("a=\"b\" ".repeat(20_000) + "c") }
        assertTrue(assertLinear { ChordProDirectives.brackets("[".repeat(100_000)) }.isEmpty())
        assertFalse(ChordProDirectives.hasBrackets("[".repeat(100_000)))
    }

    @Test
    fun `the line separator of a file is the one it is written with`() {
        assertEquals("\r", ChordProLines.lineSeparatorOf("a\rb"))
        assertEquals("\n", ChordProLines.lineSeparatorOf("a\nb"))
        assertEquals("\r\n", ChordProLines.lineSeparatorOf("a\r\nb"))
        assertEquals("\r\n", ChordProLines.lineSeparatorOf("a\rb\r\nc"))
    }

    @Test
    fun `every line starts where the offsets say it does`() {
        listOf("", "a", "a\n", "a\r", "a\r\n", "a\rb\nc").forEach { text ->
            val lines = ChordProLines.splitLines(text)
            val starts = ChordProLines.lineStartOffsets(text)
            assertEquals(lines.size, starts.size, text)
            lines.forEachIndexed { index, line ->
                val end = starts.getOrNull(index + 1) ?: text.length
                assertEquals(line, text.substring(starts[index], end).trimEnd('\r', '\n'), text)
            }
        }
    }

    @Test
    fun `words are separated by any space the platform calls one`() {
        assertEquals(
            listOf(ChordProTokens.Word(0..0, "a"), ChordProTokens.Word(2..2, "b"), ChordProTokens.Word(5..5, "c")),
            ChordProTokens.words("a\u00A0b \u00A0c"),
        )
        listOf("", "   ", "\u00A0").forEach { assertTrue(ChordProTokens.words(it).isEmpty(), it) }
    }

    @Test
    fun `a grid separated by non-breaking spaces is still a grid`() {
        assertEquals(
            listOf(GridToken.Bar("|"), GridToken.Chord("Am"), GridToken.Beat, GridToken.Chord("G"), GridToken.Bar("|")),
            ChordProTokens.parseGridTokens("|\u00A0Am\u00A0.\u00A0G\u00A0|"),
        )
    }

    private fun <T> assertLinear(block: () -> T): T {
        val (result, duration) = measureTimedValue(block)
        assertTrue(duration < 5.seconds, "took $duration")
        return result
    }

    @Test
    fun `the body begins where the parser starts reading a key as a change`() {
        listOf(
            "" to false,
            "# a note" to false,
            "{title: T}" to false,
            "{c-guitar: Intro}" to false,
            "{start_of_verse}" to true,
            "{c: Intro}" to true,
            "la la" to true,
        ).forEach { (line, isBody) ->
            val lines = listOf("{key: }", line, "{key: G}")

            assertEquals(if (isBody) 1 else 3, ChordProHeaderLayout.bodyStartIndex(lines), line)
            assertEquals(if (isBody) null else "G", ChordProParser.parseMetadata(lines.joinToString("\n")).key?.takeIf { it.isNotBlank() }, line)
        }
    }
}
