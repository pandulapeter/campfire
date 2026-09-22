/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro

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
        assertEquals(ChordProSyntax.Directive("title", "Song"), ChordProSyntax.matchDirective("{title: Song}"))
        assertEquals(ChordProSyntax.Directive("soc", null), ChordProSyntax.matchDirective("{ soc }"))
        assertEquals(ChordProSyntax.Directive("title", ""), ChordProSyntax.matchDirective("{title: }"))
        assertEquals(ChordProSyntax.Directive("tag", "a}b"), ChordProSyntax.matchDirective("{tag: a}b}"))
    }

    @Test
    fun `a line that only looks like a directive is content`() {
        listOf("", "{", "}", "{}", "{:}", "{title", "title}", "{ti tle: x}", "{title}}", "{cím: x}", "{Verse 2}", "{verse 2}", "{Refrén 2x}").forEach {
            assertNull(ChordProSyntax.matchDirective(it))
        }
    }

    @Test
    fun `a value may be separated from a known name by whitespace alone`() {
        assertEquals(ChordProSyntax.Directive("title", "Wonderwall"), ChordProSyntax.matchDirective("{title Wonderwall}"))
        assertEquals(ChordProSyntax.Directive("start_of_verse", "Verse 1"), ChordProSyntax.matchDirective("{start_of_verse Verse 1}"))
        assertEquals(ChordProSyntax.Directive("meta", "language en"), ChordProSyntax.matchDirective("{meta language en}"))
        assertEquals(ChordProSyntax.Directive("x_note", "hi"), ChordProSyntax.matchDirective("{x_note hi}"))
        assertEquals(ChordProSyntax.Directive("title", "a: b"), ChordProSyntax.matchDirective("{title a: b}"))
    }

    @Test
    fun `the value start is after the colon or after the whitespace`() {
        assertEquals(7, ChordProSyntax.directiveValueStart("{title: X}"))
        assertEquals(7, ChordProSyntax.directiveValueStart("{title X}"))
        assertNull(ChordProSyntax.directiveValueStart("{soc}"))
        assertNull(ChordProSyntax.directiveValueStart("{Verse 2}"))
    }

    @Test
    fun `a label is the value or its label attribute in either quotes`() {
        assertEquals("Verse 1", ChordProSyntax.label("Verse 1"))
        assertEquals("Verse 1", ChordProSyntax.label("label=\"Verse 1\""))
        assertEquals("Verse 1", ChordProSyntax.label("label='Verse 1'"))
        assertEquals("Solo", ChordProSyntax.label("shape=\"1+4x2+4\" label=\"Solo\""))
        assertNull(ChordProSyntax.label("shape=\"1+4x2+4\""))
        assertNull(ChordProSyntax.label("  "))
        assertNull(ChordProSyntax.label("label=\"\""))
    }

    @Test
    fun `brackets are paired from the left`() {
        assertEquals(listOf(0..3 to "Am", 7..11 to "G/B"), ChordProSyntax.brackets("[Am]la [G/B]la").map { it.range to it.content })
        assertEquals(listOf(0..4 to "[Am"), ChordProSyntax.brackets("[[Am]").map { it.range to it.content })
        assertEquals(listOf(0..3 to "Am"), ChordProSyntax.brackets("[Am] [C").map { it.range to it.content })
    }

    @Test
    fun `crafted lines cost no more than their length`() {
        assertNull(assertLinear { ChordProSyntax.matchDirective("{c:" + " ".repeat(4_000) + "x") })
        assertNull(assertLinear { ChordProSyntax.matchDirective("{title" + " ".repeat(4_000) + "x") })
        assertLinear { ChordProSyntax.label("a=\"b\" ".repeat(20_000) + "c") }
        assertTrue(assertLinear { ChordProSyntax.brackets("[".repeat(100_000)) }.isEmpty())
        assertFalse(ChordProSyntax.hasBrackets("[".repeat(100_000)))
    }

    private fun <T> assertLinear(block: () -> T): T {
        val (result, duration) = measureTimedValue(block)
        assertTrue(duration < 5.seconds, "took $duration")
        return result
    }
}
