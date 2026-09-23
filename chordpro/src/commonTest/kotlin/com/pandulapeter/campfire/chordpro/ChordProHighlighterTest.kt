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

import com.pandulapeter.campfire.chordpro.ChordProHighlighter.TokenType
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import kotlin.test.Test
import kotlin.test.assertEquals

class ChordProHighlighterTest {

    /** The text each token covers, which is what a reader of a highlighting bug actually wants to see. */
    private fun spans(text: String) = ChordProHighlighter.tokenize(text).map { it.type to text.substring(it.start, it.end) }

    @Test
    fun `directive is split into its name and its value`() {
        assertEquals(
            listOf(TokenType.DIRECTIVE_NAME to "{title:", TokenType.DIRECTIVE_VALUE to " Song", TokenType.DIRECTIVE_NAME to "}"),
            spans("{title: Song}"),
        )
    }

    @Test
    fun `the closing brace of a directive with a value is coloured like the opening one`() {
        // The value is the only part of the line that is not the directive itself, so the brace that ends it belongs
        // with the brace that starts it.
        assertEquals(
            listOf(
                TokenType.DIRECTIVE_NAME to "{start_of_prechorus:",
                TokenType.DIRECTIVE_VALUE to " Pre-Chorus",
                TokenType.DIRECTIVE_NAME to "}",
            ),
            spans("{start_of_prechorus: Pre-Chorus}"),
        )
    }

    @Test
    fun `the chords of a comment or a label are coloured as chords, and nothing else in it`() {
        assertEquals(
            listOf(
                TokenType.DIRECTIVE_NAME to "{c:",
                TokenType.DIRECTIVE_VALUE to " Intro: ",
                TokenType.CHORD to "[G]",
                TokenType.DIRECTIVE_VALUE to " [*softly] [Chorus x2] [] ",
                TokenType.CHORD to "[F#m]",
                TokenType.DIRECTIVE_VALUE to " ",
                TokenType.CHORD to "[a]",
                TokenType.DIRECTIVE_NAME to "}",
            ),
            spans("{c: Intro: [G] [*softly] [Chorus x2] [] [F#m] [a]}"),
        )
        assertEquals(
            listOf(
                TokenType.DIRECTIVE_NAME to "{soc:",
                TokenType.DIRECTIVE_VALUE to " ",
                TokenType.CHORD to "[Am]",
                TokenType.DIRECTIVE_NAME to "}",
            ),
            spans("{soc: [Am]}"),
        )
    }

    @Test
    fun `the chords of a comment's text are the ones the transposition moves`() {
        val text = "Outro: [D] [*softly] [A] [Chorus x2] [bm] [b]"

        assertEquals(listOf("[D]", "[A]", "[b]"), ChordProHighlighter.chordsOfShownText(text).map { text.substring(it.start, it.end) })
    }

    @Test
    fun `the brackets of a setting are not chords`() {
        assertEquals(
            listOf(TokenType.DIRECTIVE_NAME to "{title:", TokenType.DIRECTIVE_VALUE to " Song [G]", TokenType.DIRECTIVE_NAME to "}"),
            spans("{title: Song [G]}"),
        )
    }

    @Test
    fun `a directive with no colon is split after its name`() {
        assertEquals(
            listOf(TokenType.DIRECTIVE_NAME to "{title ", TokenType.DIRECTIVE_VALUE to "Song", TokenType.DIRECTIVE_NAME to "}"),
            spans("{title Song}"),
        )
        assertEquals(
            listOf(TokenType.DIRECTIVE_NAME to "{title:", TokenType.DIRECTIVE_VALUE to " a: b", TokenType.DIRECTIVE_NAME to "}"),
            spans("{title: a: b}"),
        )
    }

    @Test
    fun `the brackets of an abc block are not chords`() {
        assertEquals(
            listOf(TokenType.CHORD to "[C]"),
            spans("{start_of_abc}\n[CEG]\n{end_of_abc}\n[C]").filter { it.first == TokenType.CHORD },
        )
    }

    @Test
    fun `directive without a value is all name`() {
        assertEquals(listOf(TokenType.DIRECTIVE_NAME to "{start_of_chorus}"), spans("{start_of_chorus}"))
    }

    @Test
    fun `directive with an empty value is all name`() {
        // There is no value text to colour differently yet, so the whole thing reads as one directive.
        assertEquals(listOf(TokenType.DIRECTIVE_NAME to "{key: }"), spans("{key: }"))
    }

    @Test
    fun `chords and annotations are told apart`() {
        assertEquals(
            listOf(TokenType.CHORD to "[Am]", TokenType.ANNOTATION to "[*softly]", TokenType.CHORD to "[G/B]"),
            spans("[Am]word [*softly] more [G/B]end"),
        )
    }

    @Test
    fun `an annotation is an annotation whatever the spaces inside its brackets`() {
        assertEquals(
            listOf(TokenType.CHORD to "[Am]", TokenType.ANNOTATION to "[ *softly]", TokenType.ANNOTATION to "[*a ]"),
            spans("[Am]word [ *softly] more [*a ] end"),
        )
    }

    @Test
    fun `an empty bracket is not a chord`() {
        assertEquals(listOf(TokenType.CHORD to "[Am]"), spans("[] [ ] [Am]"))
    }

    @Test
    fun `a chord with spaces inside its brackets is still a chord`() {
        assertEquals(listOf(TokenType.CHORD to "[ Am ]"), spans("[ Am ]word"))
    }

    @Test
    fun `the highlighter and the parser agree about every bracket of a line`() {
        listOf("[Am]a [ *x]b [] c [ G/B ]d", "[*a ] [  *N.C.] la", "[ ] [C]", "no brackets").forEach { line ->
            val highlighted = ChordProHighlighter.tokenize(line).map { it.type == TokenType.ANNOTATION }
            val parsed = ChordProParser.parse(line).blocks
                .filterIsInstance<ChordProBlock.Section>().flatMap { it.lines }
                .filterIsInstance<ChordProLine.Lyrics>().flatMap { it.chords }
                .map { it.isAnnotation }

            assertEquals(parsed, highlighted, line)
        }
    }

    @Test
    fun `hash lines are comments, wherever they are indented to`() {
        assertEquals(listOf(TokenType.COMMENT to "  # not sung [Am]"), spans("  # not sung [Am]"))
    }

    @Test
    fun `brackets inside a tab are left alone`() {
        val text = "{start_of_tab}\ne|--[3]--|\n{end_of_tab}\n[Am]after"
        assertEquals(
            listOf(
                TokenType.DIRECTIVE_NAME to "{start_of_tab}",
                TokenType.DIRECTIVE_NAME to "{end_of_tab}",
                TokenType.CHORD to "[Am]",
            ),
            spans(text),
        )
    }

    @Test
    fun `the brackets above a staff are chords`() {
        val text = "{start_of_tab}\n[Am]      [C]\ne|--0--1--|\n{end_of_tab}"
        assertEquals(
            listOf(
                TokenType.DIRECTIVE_NAME to "{start_of_tab}",
                TokenType.CHORD to "[Am]",
                TokenType.CHORD to "[C]",
                TokenType.DIRECTIVE_NAME to "{end_of_tab}",
            ),
            spans(text),
        )
    }

    @Test
    fun `a delegate block's braces and hash lines are not tokens`() {
        val text = "{start_of_ly}\n{ c d e }\n#(x)\n{end_of_ly}"
        assertEquals(
            listOf(
                TokenType.DIRECTIVE_NAME to "{start_of_ly}",
                TokenType.DIRECTIVE_NAME to "{end_of_ly}",
            ),
            spans(text),
        )
    }

    @Test
    fun `offsets are absolute across lines`() {
        val text = "one\n[Am]two"
        assertEquals(listOf(ChordProHighlighter.Token(TokenType.CHORD, 4, 8)), ChordProHighlighter.tokenize(text))
    }

    @Test
    fun `an unfinished line still highlights what it has`() {
        // Whatever is being typed has to look like what it is becoming, so a missing brace is not a reason to stop.
        assertEquals(listOf(TokenType.CHORD to "[Am]"), spans("{title: half typed\n[Am]word"))
    }

    @Test
    fun `windows line endings do not shift the offsets`() {
        assertEquals(
            listOf(
                TokenType.DIRECTIVE_NAME to "{title:",
                TokenType.DIRECTIVE_VALUE to " Song",
                TokenType.DIRECTIVE_NAME to "}",
                TokenType.CHORD to "[Am]",
            ),
            spans("{title: Song}\r\n[Am]word"),
        )
    }

    @Test
    fun `old Mac line endings do not hide the directives`() {
        assertEquals(spans("{title: A}\n{c: x}\n[C]hello"), spans("{title: A}\r{c: x}\r[C]hello"))
        assertEquals(
            listOf(
                TokenType.DIRECTIVE_NAME to "{title:",
                TokenType.DIRECTIVE_VALUE to " A",
                TokenType.DIRECTIVE_NAME to "}",
                TokenType.DIRECTIVE_NAME to "{c:",
                TokenType.DIRECTIVE_VALUE to " x",
                TokenType.DIRECTIVE_NAME to "}",
                TokenType.CHORD to "[C]",
            ),
            spans("{title: A}\r{c: x}\r[C]hello"),
        )
    }

    @Test
    fun `brackets inside a tab of a CR-only file are left alone`() {
        assertEquals(
            listOf(
                TokenType.DIRECTIVE_NAME to "{start_of_tab}",
                TokenType.DIRECTIVE_NAME to "{end_of_tab}",
                TokenType.CHORD to "[Am]",
            ),
            spans("{start_of_tab}\re|--[3]--|\r{end_of_tab}\r[Am]after"),
        )
    }

    @Test
    fun `a file mixing its line endings is highlighted line by line`() {
        assertEquals(
            listOf(
                TokenType.DIRECTIVE_NAME to "{title:",
                TokenType.DIRECTIVE_VALUE to " A",
                TokenType.DIRECTIVE_NAME to "}",
                TokenType.DIRECTIVE_NAME to "{c:",
                TokenType.DIRECTIVE_VALUE to " x",
                TokenType.DIRECTIVE_NAME to "}",
                TokenType.CHORD to "[C]",
            ),
            spans("{title: A}\r\n{c: x}\r[C]la\nlo"),
        )
    }

    @Test
    fun `empty text has nothing to highlight`() {
        assertEquals(emptyList(), ChordProHighlighter.tokenize(""))
    }
}
