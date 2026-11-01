/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.convert

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChordSheetCampfireLayoutTest {

    /** A line as the PDF reader hands it over: a span a glyph, [width] apart, from [x]. */
    private fun text(
        value: String,
        x: Double = MARGIN,
        size: Double = BODY,
        bold: Boolean = false,
        italic: Boolean = false,
        mono: Boolean = false,
        width: Double = GLYPH,
    ) = ChordSheet.Line(value.mapIndexed { index, character ->
        ChordSheet.Span(character.toString(), x + index * width, x + (index + 1) * width, size, isBold = bold, isMonospace = mono, isItalic = italic)
    })

    /** A row of chords over a lyric line, each chord at the column of the character it is set on. */
    private fun chords(vararg chords: Pair<String, Int>) = ChordSheet.Line(chords.flatMap { (name, column) ->
        text(name, x = MARGIN + column * GLYPH, bold = true).spans
    })

    private fun convert(vararg lines: ChordSheet.Line, isCampfireExport: Boolean = true) =
        ChordSheetConverter.convert(ChordSheet(listOf(ChordSheet.Page(listOf(text("A song", size = 19.0, bold = true)) + lines)), isCampfireExport = isCampfireExport)).single()

    @Test
    fun `labels open sections named by them, a label over a tab is the tab's, and grids and comments come back as they were written`() {
        assertEquals(
            """
            {title: A song}

            {start_of_tab: Picking pattern}
            {comment_box: Pick slowly.}
              Am
            e|---0---|
            C|---1---|
            {end_of_tab}

            {start_of_solo: Organ solo}
            {start_of_grid}
            | Am | C  |
            {end_of_grid}
            {end_of_solo}

            {start_of_verse: Verse 1}
            [Am]Hello world
            {end_of_verse}

            {comment: Let it ring.}

            """.trimIndent(),
            convert(
                text("Picking pattern", size = LABEL, bold = true),
                text("Pick slowly.", x = MARGIN + 5.0),
                text("  Am", mono = true),
                text("e|---0---|", mono = true),
                text("C|---1---|", mono = true),
                text("Organ solo", size = LABEL, bold = true),
                text("| Am | C  |", bold = true, mono = true),
                text("Verse 1", size = LABEL, bold = true),
                chords("Am" to 0),
                text("Hello world"),
                text("Let it ring.", italic = true),
            ),
        )
    }

    @Test
    fun `the padding under a wide chord is left out and every chord stays on the character it is printed over`() {
        val padding = '\u00A0'
        assertEquals(
            """
            {title: A song}

            [Am]is a [C]house, [D]o[F]ver, [G]said[E7] and sai[A]d [B]
            """.trimIndent() + "\n",
            convert(
                chords("Am" to 0, "C" to 7, "D" to 14, "F" to 17, "G" to 22, "E7" to 26, "A" to 34, "B" to 36),
                text("is a $padding${padding}house, o$padding${padding}ver, said and said"),
            ),
        )
    }

    @Test
    fun `an annotation printed in italics among a row's chords keeps the row a chord row and comes back as an annotation`() {
        val row = ChordSheet.Line(chords("G" to 0, "C" to 5).spans + text("twice", x = MARGIN + 8 * GLYPH, italic = true).spans)
        assertEquals("{title: A song}\n\n[G]Sing [C]it [*twice]\n", convert(row, text("Sing it")))
    }

    @Test
    fun `a lyric row marked as the continuation of the one above is joined back to it, with the chords over it`() {
        val annotation = text("\u2060Riff", italic = true)
        assertEquals(
            "{title: A song}\n\n[G]Headed down south to the land of the [*Riff]pines\n",
            convert(chords("G" to 0), text("Headed down south to the land of the "), annotation, text("\u2060pines")),
        )
        assertEquals("{title: A song}\n\nHeaded down south\nto the pines\n", convert(text("Headed down south"), text("to the pines")))
    }

    @Test
    fun `a playing details row wrapped by a narrow column is read back as the one row it was printed as`() {
        val verse = arrayOf(chords("F#m" to 0, "D" to 6), text("Hello world"))
        val sounding = convert(text("Key: F#m   Capo: 2 (chords", size = LABEL), text("⁠as they", size = LABEL), text("⁠sound)", size = LABEL), *verse)
        assertEquals("{title: A song}\n{key: Em}\n{capo: 2}\n\n[Em]Hello [C]world\n", sounding)
        val tempo = convert(text("Tempo: 96", size = LABEL), text("⁠BPM   Time: 3/4", size = LABEL), *verse)
        assertEquals("{title: A song}\n{tempo: 96}\n{time: 3/4}\n\n[F#m]Hello [D]world\n", tempo)
        // An unmarked row under the details is a lyric of its own, as any other producer's is.
        assertTrue("{capo: 2}\n\nsound)\n" in convert(text("Capo: 2 (chords as they", size = LABEL), text("sound)", size = LABEL), *verse))
    }

    @Test
    fun `a details row longer than the lyrics leaves the labels labels`() {
        val song = convert(
            text("Tonart: A   Taktart: 4/4", size = LABEL),
            text("4x4", size = LABEL, bold = true),
            text("| A7 . . . | D7 . . . |", bold = true, mono = true),
            text("Strophe", size = LABEL, bold = true),
            chords("A7" to 0, "D7" to 13),
            text("Woke up this morning"),
        )
        assertTrue("{start_of_verse: 4x4}\n{start_of_grid}" in song, song)
    }

    @Test
    fun `only a word printed in the chords' type is a chord`() {
        val song = ChordSheetConverter.convert(
            ChordSheet(
                listOf(
                    ChordSheet.Page(
                        listOf(
                            text("A", size = 19.0, bold = true),
                            text("Verse", size = LABEL, bold = true),
                            chords("C" to 0),
                            text("Hello a b c d"),
                            text("a b c d"),
                        ),
                    ),
                ),
                isCampfireExport = true,
            ),
        ).single()
        assertTrue(song.startsWith("{title: A}\n"), song)
        assertTrue("\na b c d\n" in song, song)
    }

    @Test
    fun `an export's title is taken as it is printed, with no page word or artist split off`() {
        fun convertTitled(title: String) = ChordSheetConverter.convert(
            ChordSheet(
                listOf(
                    ChordSheet.Page(
                        listOf(
                            at(0.0, text(title, size = 19.0, bold = true)),
                            at(2.6, text("Verse", size = LABEL, bold = true)),
                            at(4.0, chords("C" to 0, "G" to 6)),
                            at(5.3, text("Hello world")),
                        ),
                    ),
                ),
                isCampfireExport = true,
            ),
        ).single()
        val chords = convertTitled("Wide Chords")
        assertTrue(chords.startsWith("{title: Wide Chords}\n"), chords)
        val dashed = convertTitled("Hey - Jude")
        assertTrue(dashed.startsWith("{title: Hey - Jude}\n") && "{artist" !in dashed, dashed)
    }

    @Test
    fun `the rows right under an export's title, before the heading's gap, are its artist, however many and however long`() {
        fun page(vararg lines: ChordSheet.Line) =
            ChordSheetConverter.convert(ChordSheet(listOf(ChordSheet.Page(lines.toList())), isCampfireExport = true)).single()
        val longer = page(
            at(0.0, text("Song", size = 19.0, bold = true)), at(1.35, text("The Chords")),
            at(3.3, text("Verse", size = LABEL, bold = true)), at(4.8, chords("C" to 0, "G" to 6)), at(6.1, text("Hello world")),
        )
        assertTrue("{artist: The Chords}" in longer, longer)
        val wrapped = page(
            at(0.0, text("Wagon", size = 19.0, bold = true)), at(2.3, text("Wheel", size = 19.0, bold = true)),
            at(3.65, text("Old Crow Medicine")), at(4.85, text("Show")), at(6.8, text("Verse", size = LABEL, bold = true)),
            at(8.3, chords("A" to 0, "E" to 12)), at(9.6, text("Headed down south")),
        )
        assertTrue("{artist: Old Crow Medicine Show}" in wrapped, wrapped)
        val paragraph = page(at(0.0, text("Song", size = 19.0, bold = true)), at(2.6, text("Hello world, a paragraph")), at(3.8, text("And more of it")))
        assertFalse("{artist" in paragraph, paragraph)
        // A first lyric shorter than the title, a heading gap down, is no artist either.
        val short = page(
            at(0.0, text("A rather long title", size = 19.0, bold = true)), at(2.25, text("Hi there")),
            at(3.45, text("And more of it")), at(4.65, chords("C" to 0)), at(5.95, text("Hello world")),
        )
        assertFalse("{artist" in short, short)
    }

    @Test
    fun `a chord row opening with an annotation stays one row over its lyric`() {
        val chorus = MARGIN + 8.0
        assertEquals(
            "{title: A song}\n\n{start_of_chorus: Chorus}\n[*Everybody][D7]Home, [G]home on the [C]range\n{end_of_chorus}\n",
            convert(
                text("Chorus", x = chorus, size = LABEL, bold = true),
                ChordSheet.Line(
                    text("Everybody", x = chorus, italic = true).spans + text("D7", x = chorus + 11 * GLYPH, bold = true).spans +
                        text("G", x = chorus + 17 * GLYPH, bold = true).spans + text("C", x = chorus + 29 * GLYPH, bold = true).spans,
                ),
                text("\u00a0".repeat(11) + "Home, home on the range", x = chorus),
            ),
        )
    }

    @Test
    fun `a comment the column cut inside a word comes back as one comment`() {
        // The export writes the marks into the text of the row's first glyph.
        val cut = text("bilities", italic = true).let { line -> line.copy(spans = line.spans.mapIndexed { index, span -> if (index == 0) span.copy(text = "\u2060\u2060" + span.text) else span }) }
        val song = convert(
            chords("C" to 0), text("Hello"),
            text("Play incomprehensi", italic = true), cut, text("extraordinarily", italic = true),
            chords("G" to 0), text("World"),
        )
        assertTrue("{comment: Play incomprehensibilities extraordinarily}\n[G]World" in song, song)
    }

    @Test
    fun `a wrapped right-to-left row's chord hangs from the letter its right edge meets`() {
        val sheet = ChordSheet(
            listOf(
                ChordSheet.Page(
                    listOf(
                        ChordSheet.Line(listOf(ChordSheet.Span("Am", RIGHT - 12.0, RIGHT, BODY, isBold = true))),
                        ChordSheet.Line(
                            listOf(
                                ChordSheet.Span(
                                    "\u05e9\u05dc\u05d5\u05dd", RIGHT - 24.0, RIGHT, BODY,
                                    pieces = listOf(
                                        ChordSheet.Span("\u05e9", RIGHT - 6.0, RIGHT, BODY), ChordSheet.Span("\u05dc", RIGHT - 12.0, RIGHT - 6.0, BODY),
                                        ChordSheet.Span("\u05d5", RIGHT - 18.0, RIGHT - 12.0, BODY), ChordSheet.Span("\u05dd", RIGHT - 24.0, RIGHT - 18.0, BODY),
                                    ),
                                ),
                            ),
                        ),
                        ChordSheet.Line(listOf(ChordSheet.Span("\u2060G", RIGHT - 18.0, RIGHT - 12.0, BODY, isBold = true))),
                        ChordSheet.Line(
                            listOf(
                                ChordSheet.Span(
                                    "\u2060\u05d8\u05d5\u05d1", RIGHT - 18.0, RIGHT, BODY,
                                    pieces = listOf(
                                        ChordSheet.Span("\u2060\u05d8", RIGHT - 6.0, RIGHT, BODY), ChordSheet.Span("\u05d5", RIGHT - 12.0, RIGHT - 6.0, BODY),
                                        ChordSheet.Span("\u05d1", RIGHT - 18.0, RIGHT - 12.0, BODY),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            isCampfireExport = true,
        )
        assertEquals("[Am]\u05e9\u05dc\u05d5\u05dd \u05d8\u05d5[G]\u05d1", ChordSheetConverter.convert(sheet).single().trim())
    }

    @Test
    fun `a song of choruses is read from its left, its details row no box`() {
        val chorus = MARGIN + 8.0
        val song = convert(
            text("Tonalit\u00e0: C", size = LABEL, width = (chorus + 10 * GLYPH - 5.0 - MARGIN) / 11),
            text("Ritornello", x = chorus, size = LABEL, bold = true),
            ChordSheet.Line(text("C", x = chorus, bold = true).spans + text("F", x = chorus + 8 * GLYPH, bold = true).spans),
            text("Love is all around", x = chorus),
            text("Ritornello", x = chorus, size = LABEL, bold = true),
            ChordSheet.Line(text("D", x = chorus, bold = true).spans + text("G", x = chorus + 8 * GLYPH, bold = true).spans),
            text("Love is all around", x = chorus),
        )
        assertTrue("{key: C}" in song, song)
        assertFalse("comment_box" in song, song)
    }

    @Test
    fun `a details row ending past a right-to-left column's edge leaves its lyrics unboxed`() {
        val lines = listOf(
            text("Tonalitate: G", x = RIGHT + 0.97 - 13 * GLYPH, size = LABEL),
            ChordSheet.Line(text("C", x = RIGHT - 16 * GLYPH - 5.0, bold = true).spans + text("G", x = RIGHT - GLYPH, bold = true).spans),
            ChordSheet.Line(listOf(ChordSheet.Span("\u05e9\u05dc\u05d5\u05dd Hello world", RIGHT - 16 * GLYPH, RIGHT, BODY))),
            ChordSheet.Line(listOf(ChordSheet.Span("Rock and roll \u05e9\u05e8\u05d9\u05dd", RIGHT - 18 * GLYPH, RIGHT, BODY))),
        )
        assertEquals(listOf(null, null, null, null), ChordSheetCampfireLayout.roles(lines, listOf(false, true, false, false), BODY))
    }

    @Test
    fun `another producer's sheet is not read by campfire's layout`() {
        val lines = arrayOf(text("Organ solo", size = LABEL, bold = true), text("| Am | C  |", bold = true, mono = true), chords("Am" to 0), text("Hello world"))
        assertTrue("{start_of_solo: Organ solo}" in convert(*lines))
        assertFalse("{start_of_solo" in convert(*lines, isCampfireExport = false))
    }

    /** A row of a right-to-left song as the reader hands it over: one span over its glyphs, ending [inset] from the right. */
    private fun rtl(value: String, inset: Double = 0.0, width: Double = value.length * GLYPH) = ChordSheet.Line(
        listOf(ChordSheet.Span(value, RIGHT - inset - width, RIGHT - inset, BODY)),
    )

    private fun boxes(vararg lines: ChordSheet.Line) = ChordSheetCampfireLayout.roles(lines.toList(), lines.map { false }, BODY).withIndex()
        .filter { it.value == ChordSheetCampfireLayout.Role.BOX_COMMENT }
        .map { it.index }

    @Test
    fun `a lyric of a song printed from the right is never a boxed comment by where it starts`() {
        assertEquals(
            emptyList(),
            boxes(
                rtl("שלום עולם ומלואו"),
                rtl("שלום עולם"),
                rtl("שלום עולם ומלואו וכו"),
                // Starts 5 points right of the line two above it, which in a song printed from the left would be a box.
                rtl("שלום עולם", width = 9 * GLYPH - 5.0),
                rtl("שלום"),
            ),
        )
    }

    @Test
    fun `a boxed comment of a song printed from the right is found by its right edge, in any letters`() {
        assertEquals(listOf(1), boxes(rtl("שלום עולם ומלואו"), rtl("לאט לאט", inset = 5.0), rtl("שלום עולם")))
        assertEquals(listOf(1), boxes(rtl("שלום עולם ומלואו"), rtl("x2", inset = 5.0), rtl("שלום עולם"), rtl("שלום")))
    }

    @Test
    fun `a chorus of a song printed from the right is no box, and a box inside it is one`() {
        assertEquals(
            listOf(3),
            boxes(rtl("שלום עולם ומלואו"), rtl("שלום עולם"), rtl("שלום עולם ומלואו", inset = 8.0), rtl("לאט לאט", inset = 13.0), rtl("שלום", inset = 8.0)),
        )
    }

    @Test
    fun `each song on a page is judged from its own side`() {
        assertEquals(
            listOf(1, 6),
            boxes(
                text("Hello world"),
                text("Pick slowly.", x = MARGIN + 5.0),
                text("Hello"),
                ChordSheet.Line(listOf(ChordSheet.Span("שיר", RIGHT - 3 * 19.0, RIGHT, 19.0, isBold = true))),
                rtl("שלום עולם ומלואו"),
                rtl("שלום עולם"),
                rtl("x2", inset = 5.0),
                rtl("שלום עולם", width = 9 * GLYPH - 5.0),
            ),
        )
    }

    /** [line] with its text [rows] body sizes under the page's first line, the way the export spaces its rows. */
    private fun at(rows: Double, line: ChordSheet.Line) = line.copy(baseline = TOP + rows * BODY)

    /** The roles of [lines], those holding only bold chords taken for chord rows, as the converter's test says. */
    private fun roles(vararg lines: ChordSheet.Line) = ChordSheetCampfireLayout.roles(
        lines.toList(),
        lines.map { line -> line.spans.any { it.text.isNotBlank() } && line.spans.filter { it.text.isNotBlank() }.all { it.isBold } },
        BODY,
    )

    @Test
    fun `an italic line right under its chords is a lyric, as are its wrapped rows, and an italic line a row under chords alone is a comment`() {
        val comment = ChordSheetCampfireLayout.Role.COMMENT
        assertEquals(
            listOf(null, null, null, null, comment, null, comment, null, comment),
            roles(
                at(0.0, chords("Am" to 0, "G" to 9)),
                at(1.3, text("Ooh ooh baby", italic = true)),
                at(2.75, text("and on", italic = true)),
                at(4.2, chords("Am" to 0, "G" to 3)),
                at(5.65, text("x2", italic = true)),
                at(7.1, text("Hello world")),
                at(8.55, text("Let it ring.", italic = true)),
                text(""),
                at(11.0, text("Somewhere unknown", italic = true)).copy(baseline = null),
            ),
        )
    }

    @Test
    fun `an italic line under chords at the top of the next column is a comment`() {
        assertEquals(ChordSheetCampfireLayout.Role.COMMENT, roles(at(30.0, chords("Am" to 0)), at(0.0, text("x2", italic = true)))[1])
    }

    @Test
    fun `an italic lyric under its chords and a comment under chords alone both come back from campfire's pdf`() {
        assertEquals(
            """
            {title: A song}

            [Am]Ooh ooh [G]baby
            [Am] [G]

            {comment: x2}
            """.trimIndent() + "\n",
            convert(
                at(0.0, chords("Am" to 0, "G" to 8)),
                at(1.3, text("Ooh ooh baby", italic = true)),
                at(2.75, chords("Am" to 0, "G" to 3)),
                at(4.2, text("x2", italic = true)),
            ),
        )
    }

    private companion object {
        const val TOP = 100.0
        const val RIGHT = 400.0
        const val MARGIN = 40.0
        const val GLYPH = 6.0
        const val BODY = 12.0
        const val LABEL = 11.0
    }

    @Test
    fun `a label opens the section the export printed it for, in every language of the app and with any words after it`() {
        // The app's own labels for verse, chorus, bridge, pre-chorus, intro, solo, and outro, in each of its languages.
        val labels = listOf(
            listOf("Verse", "Chorus", "Bridge", "Pre-chorus", "Intro", "Solo", "Outro"),
            listOf("\u05d1\u05d9\u05ea", "\u05e4\u05d6\u05de\u05d5\u05df", "\u05d2\u05e9\u05e8", "\u05d8\u05e8\u05d5\u05dd\u05be\u05e4\u05d6\u05de\u05d5\u05df", "\u05e4\u05ea\u05d9\u05d7\u05d4", "\u05e1\u05d5\u05dc\u05d5", "\u05e1\u05d9\u05d5\u05dd"),
            listOf("Strophe", "Refrain", "Bridge", "Pre-Chorus", "Intro", "Solo", "Outro"),
            listOf("Couplet", "Refrain", "Pont", "Pr\u00e9-refrain", "Intro", "Solo", "Outro"),
            listOf("Estrofa", "Estribillo", "Puente", "Preestribillo", "Intro", "Solo", "Outro"),
            listOf("Strofa", "Ritornello", "Bridge", "Pre-ritornello", "Intro", "Assolo", "Outro"),
            listOf("Strof\u0103", "Refren", "Bridge", "Pre-refren", "Intro", "Solo", "Outro"),
            listOf("Versszak", "Refr\u00e9n", "\u00c1tk\u00f6t\u00e9s", "Pre-refr\u00e9n", "Intro", "Sz\u00f3l\u00f3", "Outro"),
        )
        val types = listOf("verse", "chorus", "bridge", "pre-chorus", "intro", "solo", "outro")
        labels.forEach { language ->
            assertEquals(types, language.map { ChordSheetCampfireLayout.sectionType(it) }, language.toString())
            assertEquals(types.take(4), language.take(4).map { ChordSheetCampfireLayout.sectionType("$it 2") }, language.toString())
        }
        assertEquals("chorus", ChordSheetCampfireLayout.sectionType("Chorus (twice)"))
        assertEquals("bridge", ChordSheetCampfireLayout.sectionType("Bridge \u2014 build"))
        assertEquals("verse", ChordSheetCampfireLayout.sectionType("Riffs and more"))
        assertEquals("solo", ChordSheetCampfireLayout.sectionType("Organ solo"))
    }
}
