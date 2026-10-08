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

class ChordProLineScannerTest {

    private fun scan(text: String) = ChordProLineScanner.scan(text).toList()

    @Test
    fun `a hash line is a source comment outside a delegated environment only`() {
        val lines = scan("# note\n{start_of_abc}\n# abc\n{end_of_abc}\n# again")
        assertEquals(listOf(true, false, false, false, true), lines.map { it.isSourceComment })
        assertNull(lines[0].directive)
        assertTrue(lines[2].isDelegated)
    }

    @Test
    fun `braces inside a delegated environment are not a directive unless written with a colon`() {
        val lines = scan("{start_of_ly}\n{ c d e }\n{title: X}\n{end_of_ly}")
        assertNull(lines[1].directive)
        assertEquals(ChordProDirectives.Directive("title", "X"), lines[2].directive)
        assertEquals("end_of_ly", lines[3].directive?.name)
        assertEquals(listOf(null, "ly", "ly", "ly"), lines.map { it.environment })
        assertEquals(listOf("ly", "ly", "ly", null), lines.map { it.environmentAfter })
    }

    @Test
    fun `the same braces outside it are a directive`() {
        assertEquals("c", scan("{ c d e }").single().directive?.name)
    }

    @Test
    fun `any end of an environment closes the one that is open`() {
        val lines = scan("{start_of_tab}\ne|---|\n{end_of_verse}\nlyrics")
        assertEquals(listOf(null, "tab", "tab", null), lines.map { it.environment })
    }

    @Test
    fun `a start inside a delegated environment written with a colon moves it on`() {
        val lines = scan("{start_of_abc}\n{start_of_verse: V}\nline")
        assertEquals(listOf(null, "abc", "verse"), lines.map { it.environment })
        assertFalse(lines[2].isDelegated)
        assertTrue(lines[1].isDelegated)
        assertFalse(lines[1].isDelegatedAfter)
    }

    @Test
    fun `short names and capitals open the lowercase environment`() {
        val lines = scan("{SOT}\nx\n{eot}\n{Start_Of_Grid}\n| C |")
        assertEquals(listOf(null, "tab", "tab", null, "grid"), lines.map { it.environment })
    }

    @Test
    fun `an unclosed delegated environment takes the rest of the text`() {
        val lines = scan("{start_of_svg}\n<svg>\n{ c d e }\n# not a comment")
        assertTrue(lines.drop(1).all { it.isDelegated })
        assertFalse(lines.last().isSourceComment)
    }

    @Test
    fun `every line ending splits alike`() {
        listOf("a\r\nb\r\nc", "a\rb\rc", "a\nb\nc").forEach { text ->
            assertEquals(listOf(0 to "a", 1 to "b", 2 to "c"), scan(text).map { it.index to it.raw })
        }
    }

    @Test
    fun `lines are trimmed for matching and kept raw`() {
        val line = scan("  {title: X}  ").single()
        assertEquals("  {title: X}  ", line.raw)
        assertEquals("{title: X}", line.trimmed)
        assertEquals("title", line.directive?.name)
    }
}
