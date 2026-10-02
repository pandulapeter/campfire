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
import kotlin.test.assertTrue

class ChordSheetConverterTest {
    private fun convert(text: String) = ChordSheetConverter.convert(ChordSheet.ofPlainText(text)).single()

    @Test
    fun preservesChordProIncludingCollectionsAndLineEndings() {
        for (text in listOf("\ufeff{title: Song}\r\n[A]Hello\r\n{new_song}\r\n{title: Other}\r\n", "[G]Hello\r\n[C]world", "{t: Title}\n")) {
            assertEquals(text, convert(text))
        }
    }

    @Test
    fun mergesAndSnapsChordsWithoutChangingTheirOrder() {
        assertEquals("[Am]Hello [C]world\n", convert("Am     C\nHello world"))
        assertEquals("[Am]Hello [C]world\n", convert(" Am  C\nHello world"))
        assertEquals("[Am]Hel[C]lo world\n", convert("Am C\nHello world"))
        assertEquals("[Am]Hello [C]\n", convert("Am            C\nHello"))
    }

    @Test
    fun oneChordIsAmbiguousUntilAnotherLineMakesTheContextClear() {
        assertEquals("A\nlong time ago\n", convert("A\nlong time ago"))
        assertEquals("A long time ago\nAm I\n", convert("A long time ago\nAm I"))
        assertEquals("[Am] [F]\n\n[A]long time ago\n", convert("Am F\n\nA\nlong time ago"))
        assertEquals("Do Re Mi\n1 4 5\n", convert("Do Re Mi\n1 4 5"))
    }

    @Test
    fun recognizesGermanAndLowercaseChordsAndKeepsFurniture() {
        assertEquals("[am] [H7] | % x2 N.C.\n", convert("am H7 | % x2 N.C."))
        assertEquals("| [Am] [F] | [C] [G] |\n", convert("|Am F|C G|"))
        assertFalse(ChordSheetConverter.isChordLine("Am I"))
        assertTrue(ChordSheetConverter.isChordLine("F\u266fm B\u266d"))
        assertEquals("[F#m] [Bb]\n", convert("F\u266fm B\u266d"))
    }

    @Test
    fun sectionsMetadataAndRecallsBecomeDirectives() {
        val text = "Capo: 3rd fret\nHangnem: G\n120 BPM\nTime: 4/4\nEl\u0151ad\u00f3: Someone\n\u00a9 1900\nVerse 2:\nAm C\nHello world\n[Refr\u00e9n]\nAm F\nSing along\n\nRef."
        val result = convert(text)
        assertTrue(result.contains("{capo: 3}\n{key: G}\n{tempo: 120}\n{time: 4/4}\n{artist: Someone}\n{copyright: 1900}"))
        assertTrue(result.contains("{start_of_verse: Verse 2:}"))
        assertTrue(result.contains("{end_of_verse}\n{start_of_chorus: [Refr\u00e9n]}"))
        assertTrue(result.endsWith("{end_of_chorus}\n{chorus}\n"))
        assertEquals("{comment: Intro}\n[Am] [F] [C] [G]\n", convert("Intro: Am F C G"))
    }

    @Test
    fun lyricsThatStartLikeALabelStayLyrics() {
        assertEquals("[Am]By the [C]rivers of Babylon\n", convert("Am     C\nBy the rivers of Babylon"))
        assertEquals("[Am]Time after [C]time\n", convert("Am         C\nTime after time"))
        assertEquals("[Am]Key to my [C]heart\n", convert("Am         C\nKey to my heart"))
        assertTrue(convert("Capo 2\nKey G\nTime 4/4\nAm C\nHello world").startsWith("{capo: 2}\n{key: G}\n{time: 4/4}\n"))
        assertTrue(convert("By: Someone\nAm C\nHello world").startsWith("{artist: Someone}\n"))
    }

    @Test
    fun chordProWithFewChordsOrOnlyRecallsIsStillChordPro() {
        val sparse = "Verse one\nno chords here\n[G]Only this line\nhas a chord\nand that is all\n"
        assertEquals(sparse, convert(sparse))
        val recall = "First [G] line\nsecond line\nthird line\n{chorus}\n"
        assertEquals(recall, convert(recall))
        assertEquals("A footnote (C) here\nand more\nand more\n", convert("A footnote [C] here\nand more\nand more"))
    }

    @Test
    fun letterSpacingDoesNotSplitWords() {
        val spaced = ChordSheet.Line(listOf(span("T", 0.0, 6.7, 12.0), span("h", 7.7, 14.4, 12.0), span("i", 15.4, 18.4, 12.0), span("s", 19.4, 25.4, 12.0), span("i", 30.0, 33.0, 12.0), span("s", 34.0, 40.0, 12.0)))
        assertEquals("This is\n", ChordSheetConverter.convert(ChordSheet(listOf(ChordSheet.Page(listOf(spaced))))).single())
    }

    @Test
    fun tablatureNeedsAtLeastTwoStaffLines() {
        assertEquals("{start_of_tab}\ne|---0---|\nB|---1---|\n{end_of_tab}\n", convert("e|---0---|\nB|---1---|"))
        assertEquals("e|---0---|\n", convert("e|---0---|"))
    }

    @Test
    fun parenthesizedChordsNeedAMajority() {
        assertEquals("[Am] Today [C] the day (repeat)\n", convert("(Am) Today (C) the day (repeat)"))
        assertEquals("(Am) Today (repeat)\n", convert("(Am) Today (repeat)"))
        assertEquals("(softly) hello\n", convert("(softly) hello"))
    }

    @Test
    fun ordinaryProseCannotAccidentallyBecomeMarkup() {
        assertEquals("\uff03 heading\n(unknown: value)\n(something) else\n", convert("# heading\n{unknown: value}\n[something] else"))
        assertEquals("office flower\nnext line\n", convert("o\ufb03ce \ufb02ower\u00ad\u200b\nnext\u00a0line"))
        assertEquals("hello\n\nworld\n", convert("hello\n\n\n\nworld\n"))
        assertEquals("\uff03 hello (yes)\n", ChordProLiteralText.escape("# hello [yes]\n"))
    }

    @Test
    fun mapsProportionalSpansAndCoincidentChordsToCharacters() {
        val chords = ChordSheet.Line(listOf(span("Am", 100.0, 112.0), span("C", 115.0, 120.0), span("G", 115.0, 120.0)))
        val lyric = ChordSheet.Line(listOf(span("W", 100.0, 110.0), span("i", 110.0, 112.0), span("d", 112.0, 117.0), span("e", 117.0, 122.0)))
        assertEquals("[Am]Wi[C][G]de\n", ChordSheetConverter.convert(ChordSheet(listOf(ChordSheet.Page(listOf(chords, lyric))))).single())
    }

    @Test
    fun readsStyledInlineChords() {
        val lyric = ChordSheet.Line(listOf(span("Today ", 0.0, 30.0), span("Am", 30.0, 40.0, bold = true), span(" we sing", 40.0, 80.0)))
        assertEquals("Today [Am] we sing\n", ChordSheetConverter.convert(ChordSheet(listOf(ChordSheet.Page(listOf(lyric))))).single())
        val glyphs = ChordSheet.Line(listOf(span("Today ", 0.0, 30.0), span("A", 30.0, 35.0, bold = true), span("m", 35.0, 40.0, bold = true), span(" we sing", 40.0, 80.0)))
        assertEquals("Today [Am] we sing\n", ChordSheetConverter.convert(ChordSheet(listOf(ChordSheet.Page(listOf(glyphs))))).single())
    }

    @Test
    fun styledPageTitlesSplitSongbooksButContinuationPagesStayTogether() {
        fun page(title: String?, words: String) = ChordSheet.Page(
            listOfNotNull(title?.let { ChordSheet.Line(listOf(span(it, 0.0, 100.0, size = 20.0)), isHeading = true) }) +
                ChordSheet.ofPlainText("Am C\n$words").pages.single().lines,
        )
        val sheet = ChordSheet(listOf(page("First", "First words"), page(null, "More words"), page("Second", "Other words")))
        val result = ChordSheetConverter.convert(sheet)
        assertEquals(2, result.size)
        assertTrue(result[0].startsWith("{title: First}"))
        assertTrue(result[0].contains("e words"))
        assertTrue(result[1].startsWith("{title: Second}"))
        assertEquals(result, ChordSheetConverter.convert(sheet))
    }

    @Test
    fun derivesPlainTitleAndExpandsTabs() {
        assertTrue(convert("Someone - Song\n\nAm C\nHello world").startsWith("{title: Song}\n{artist: Someone}"))
        assertEquals("[Am]Hello   [C]world\n", convert("Am\tC\nHello   world"))
    }

    private fun span(text: String, start: Double, end: Double, size: Double = 10.0, bold: Boolean = false) =
        ChordSheet.Span(text, start, end, size, isBold = bold)
}
