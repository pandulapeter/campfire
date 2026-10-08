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
import com.pandulapeter.campfire.chordpro.model.ChordProMetadata
import com.pandulapeter.campfire.chordpro.syntax.MetadataKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

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
    fun `any end of an environment ends a grid`() {
        assertEquals(
            listOf(TokenType.CHORD to "Am", TokenType.CHORD to "[Am]"),
            spans("{start_of_grid}\n| Am |\n{end_of_verse}\n[Am]La").filter { it.first == TokenType.CHORD },
        )
    }

    @Test
    fun `any end of an environment ends a tab`() {
        assertEquals(
            listOf(TokenType.CHORD to "[3]"),
            spans("{start_of_tab}\ne|--[3]--|\n{end_of_chorus}\ne|--[3]--|").filter { it.first == TokenType.CHORD },
        )
    }

    @Test
    fun `any end of an environment ends an abc block`() {
        assertEquals(
            listOf(TokenType.CHORD to "[C]"),
            spans("{start_of_abc}\n[CEG]\n{end_of_verse}\n[C]").filter { it.first == TokenType.CHORD },
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
    fun `the chord cells of a grid are chords, and its margins, bars and beats are not`() {
        val text = "{start_of_grid}\nA |: Am . C~G | / % :| x2\n{end_of_grid}\nAm"
        assertEquals(
            listOf(
                TokenType.DIRECTIVE_NAME to "{start_of_grid}",
                TokenType.CHORD to "Am",
                TokenType.CHORD to "C~G",
                TokenType.DIRECTIVE_NAME to "{end_of_grid}",
            ),
            spans(text),
        )
    }

    @Test
    fun `the chord cells of an indented grid line are found where they are written`() {
        val text = "{sog}\n  | Am  . |\n{eog}"
        val chord = ChordProHighlighter.tokenize(text).single { it.type == TokenType.CHORD }
        assertEquals("Am", text.substring(chord.start, chord.end))
        assertEquals(text.indexOf("Am"), chord.start)
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
    fun `a directive whose value cannot be read is marked whole`() {
        listOf(
            "{time: 5/7}",
            "  {meta: time three four}",
            "{tempo: fast}",
            "{capo: second}",
            "{capo: -1}",
            "{duration: about 4 min}",
            "{transpose: up}",
            "{meta: cover not an address}",
            "{meta: link ftp://example.com}",
            "{language: und}",
        ).forEach { line ->
            assertEquals(listOf(TokenType.INVALID to line), spans(line), line)
        }
    }

    @Test
    fun `a directive whose value can be read is not marked`() {
        listOf(
            "{time: 6/8}",
            "{time: C|}",
            "{meta: time 3/4}",
            "{tempo: ♩ = 96}",
            "{capo: 2}",
            "{duration: 4:28}",
            "{key: F#m}",
            "{key: a}",
            "{key: Bb-Dur}",
            "{key: Dm (capo 2)}",
            "{key: a-moll}",
            "{key: Esz-dúr}",
            "{key: D dorian}",
            "{transpose: -3f}",
            "{transpose}",
            "{time}",
            "{key: }",
            "{meta: lang}",
            "{meta: cover https://example.com/cover.jpg}",
            "{meta: link https://example.com Lesson}",
            "{language: en-US}",
            "{title: 5/7}",
            "{unknown: whatever}",
        ).forEach { line ->
            assertEquals(false, spans(line).any { it.first == TokenType.INVALID }, line)
        }
    }

    @Test
    fun `a directive inside an environment handed to another program is not marked`() {
        assertEquals(
            false,
            spans("{start_of_abc}\n{time: whatever}\n{end_of_abc}").any { it.first == TokenType.INVALID },
        )
    }

    @Test
    fun `a second line of what a song only says once is marked whole`() {
        val duplicates = { text: String -> spans(text).filter { it.first == TokenType.DUPLICATE }.map { it.second } }
        assertEquals(listOf("{t: B}"), duplicates("{title: A}\n{artist: X}\n{t: B}"))
        assertEquals(listOf("{year: 1999}", "  {meta: year 2000}"), duplicates("{year: 1998}\n{year: 1999}\n  {meta: year 2000}"))
        assertEquals(listOf("{key: G}"), duplicates("{key: }\n\nla\n{key: G}"))
        assertEquals(emptyList(), duplicates("{key: }\n{key: G}\n\nla"))
        assertEquals(listOf("{title: }"), duplicates("{title: }\n{title: A}\n{title: }"))
        assertEquals(listOf("{meta: cover https://b.com/b.jpg}"), duplicates("{meta: cover https://a.com/a.jpg}\n{meta: cover https://b.com/b.jpg}"))
    }

    @Test
    fun `what a song may say again is not marked as a second line`() {
        listOf(
            "{tag: A}\n{tag: B}\n{meta: tag C}",
            "{meta: language en}\n{language: hu}",
            "{meta: link https://a.com}\n{meta: link https://b.com}",
            "{tempo: 90}\n{time: 3/4}\nla\n{tempo: 120}\n{time: 4/4}",
            "{define: C base-fret 1 frets x 3 2 0 1 0}\n{define: C base-fret 1 frets x 3 2 0 1 0}",
            "{title: A}\n{start_of_abc}\n{title: B}\n{end_of_abc}",
        ).forEach { text ->
            assertEquals(false, spans(text).any { it.first == TokenType.DUPLICATE }, text)
        }
    }

    @Test
    fun `an unreadable line leaves its kind free for the next one`() {
        assertEquals(
            listOf(TokenType.INVALID to "{capo: second}", TokenType.DIRECTIVE_NAME to "{capo:", TokenType.DIRECTIVE_VALUE to " 2", TokenType.DIRECTIVE_NAME to "}"),
            spans("{capo: second}\n{capo: 2}"),
        )
    }

    @Test
    fun `empty text has nothing to highlight`() {
        assertEquals(emptyList(), ChordProHighlighter.tokenize(""))
    }

    /**
     * Every spelling the parser reads for each metadata kind, as the only line of a song: `{meta: name value}` for all of
     * them, and the standalone directive under the long name and every short one for all but the cover and the link,
     * which are only ever read as `{meta}` items. The language's short name is a `{meta}` key as well.
     */
    private val metadataSpellings = MetadataKind.entries.associate { kind ->
        val names = listOf(kind.longName) + kind.aliases
        val standalone = if (kind == MetadataKind.COVER || kind == MetadataKind.LINK) emptyList() else names
        val meta = if (kind == MetadataKind.LANGUAGE) names else listOf(kind.longName)
        kind.longName to standalone.map { name -> { value: String -> "{$name: $value}" } } +
            meta.map { name -> { value: String -> "{meta: $name $value}" } }
    }

    private val metadataValues = listOf(
        "", "fast", "-1", "3/5", "1:2:3", "abc", "120", "3/4", "2", "3:30", "en", "https://example.com/cover.jpg",
    )

    /** What the song comes out with for [kind], read the way the app reads it, or null where it says nothing usable. */
    private fun ChordProMetadata.readValue(kind: String): Any? = when (kind) {
        "title" -> title
        "subtitle" -> subtitle
        "artist" -> artist
        "composer" -> composer
        "lyricist" -> lyricist
        "album" -> album
        "year" -> year
        "key" -> key
        "capo" -> capo
        "tempo" -> ChordProTempo.parse(tempo)
        "time" -> ChordProTime.parse(time)
        "duration" -> ChordProDuration.parse(duration)
        "tag" -> tags.firstOrNull()
        "language" -> languages.firstOrNull()
        "cover" -> coverArt
        "link" -> links.firstOrNull()
        else -> error("Unknown kind $kind")
    }?.takeIf { it.toString().isNotEmpty() }

    @Test
    fun `a metadata line is marked unreadable exactly where the song comes out without what it says`() {
        metadataSpellings.forEach { (kind, spellings) ->
            spellings.forEach { spelling ->
                metadataValues.forEach { value ->
                    val line = spelling(value)
                    val isInvalid = ChordProHighlighter.tokenize(line).any { it.type == TokenType.INVALID }
                    val isDropped = value.isNotEmpty() && ChordProParser.parse(line).metadata.readValue(kind) == null
                    assertEquals(isDropped, isInvalid, "$kind: $line")
                }
            }
        }
    }

    @Test
    fun `a standalone cover or link directive is read by neither the parser nor the highlighter`() {
        listOf("{cover: https://example.com/cover.jpg}", "{link: https://example.com}", "{cover: abc}", "{link: abc}").forEach { line ->
            assertFalse(ChordProHighlighter.tokenize(line).any { it.type == TokenType.INVALID }, line)
            val metadata = ChordProParser.parse(line).metadata
            assertNull(metadata.coverArt, line)
            assertEquals(emptyList(), metadata.links, line)
        }
    }
}
