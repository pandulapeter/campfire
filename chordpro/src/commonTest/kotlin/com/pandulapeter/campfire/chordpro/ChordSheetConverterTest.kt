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

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.TimeSource
import kotlin.time.Duration.Companion.seconds

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
        assertTrue(result.contains("{start_of_verse: Verse 2}"))
        assertTrue(result.contains("{end_of_verse}\n{start_of_chorus: Refr\u00e9n}"))
        assertTrue(result.endsWith("{end_of_chorus}\n{chorus}\n"))
        assertEquals("{comment: Intro}\n[Am] [F] [C] [G]\n", convert("Intro: Am F C G"))
    }

    @Test
    fun headingDecorationIsLeftOutOfSectionNames() {
        val result = convert("[Verse 1]\nC   G\nHello world\n\n[Intro]\nC G\n\n[Chorus]\nAm F\nLa la")
        assertTrue(result.contains("{start_of_verse: Verse 1}"))
        assertTrue(result.contains("{comment: Intro}"))
        assertTrue(result.contains("{start_of_chorus: Chorus}"))
        assertEquals(convert("Verse 1\nC G\nHello world"), convert("Verse 1:\nC G\nHello world"))
        assertEquals(convert("Chorus\nAm F\nLa la"), convert("(Chorus)\nAm F\nLa la"))
        assertEquals("{start_of_verse: Verse 1}\nHello world\n{end_of_verse}\n", convert("Verse 1\nHello world"))
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
    fun unclosedBracketsAreEscapedInLinearTime() {
        val text = "[".repeat(100_000) + "\nhello world"
        val started = TimeSource.Monotonic.markNow()
        val result = convert(text)
        assertTrue(started.elapsedNow() < 5.seconds)
        assertEquals("(".repeat(100_000) + "\nhello world\n", result)
        assertEquals("(a b (C)x\nplain words\nmore words\n", convert("[a b [C]x\nplain words\nmore words"))
    }

    @Test
    fun seededSheetsKeepTheirChordPlacement() {
        val expected = listOf(
            "[D7]today [C]He[Am]llo si[D7]ng [F]sin[G]g wor[C]ld [C]to[Am]day Hello sing Hello\n",
            "[C]Hel[G]lo [D7]today [Am]He[G]llo [Am]sing today sing\n",
            "[G]Hell[G]o [C]worl[G]d [F]sing [D7]today world today\n",
            "[C]sing [C]world\n", "[F]world [C]Hello\n", "[F]sing [C]sin[F]g tod[F]ay sing\n",
            "[Am]sin[C]g [D7]sing [D7]world [Am]Hello [G][D7]Hello [C]si[F]ng sing sing Hello\n",
            "[C]He[Am]llo [F]sing [Am]worl[F]d [Am]sing world Hello\n",
            "[G]toda[C]y [C]worl[C]d [C]today [F]sing [C]wor[G]ld [C]today today world Hello\n",
            "[G]to[D7]day si[Am]ng [G]sin[G]g Hello sing\n",
            "[C]to[D7]day [G]Hell[D7]o [F]wor[D7]ld [C]sing sing Hello today\n",
            "[C]Hel[Am]lo [G]to[D7]day [Am]worl[F]d sin[D7]g sing Hello today\n",
        )
        val random = Random(7)
        repeat(12) { index ->
            val count = random.nextInt(2, 10)
            val chords = List(count) { listOf("Am", "C", "G", "F", "D7")[random.nextInt(5)] + " ".repeat(random.nextInt(1, 5)) }.joinToString("")
            val lyrics = List(count) { listOf("Hello", "world", "sing", "today")[random.nextInt(4)] }.joinToString(" ")
            assertEquals(expected[index], convert("$chords\n$lyrics"))
        }
        val chords = ChordSheet.Line(listOf(span("Am", 0.0, 10.0), span("C", 20.0, 25.0)))
        val lyrics = ChordSheet.Line(listOf(span("Hello", 0.0, 25.0), span("world", 10.0, 35.0), span("today", 5.0, 30.0)))
        assertEquals("[Am]Helloworldtod[C]ay\n", ChordSheetConverter.convert(ChordSheet(listOf(ChordSheet.Page(listOf(chords, lyrics))))).single())
    }

    @Test
    fun longChordRowsAndInlineRunsConvertWithinTheWorkBound() {
        val chordRow = "C  ".repeat(30_000)
        val lyrics = "la ".repeat(30_000).trimEnd()
        var started = TimeSource.Monotonic.markNow()
        assertEquals("[C]la ".repeat(30_000).trimEnd() + "\n", convert("$chordRow\n$lyrics"))
        assertTrue(started.elapsedNow() < 5.seconds)
        val inline = "(C)la ".repeat(60_000).trimEnd()
        started = TimeSource.Monotonic.markNow()
        assertEquals("[C]la ".repeat(60_000).trimEnd() + "\n", convert(inline))
        assertTrue(started.elapsedNow() < 5.seconds)
    }

    @Test
    fun chordsNeverSplitSupplementaryCharacters() {
        assertEquals("[C]Hel😀[G]lo wor\n", convert("C   G\nHel😀lo wor"))
        assertEquals("[A]x😀[G]hello\n", convert("A G\nx😀hello"))
        assertEquals("[C]😀hello😀[G]\n", convert("C       G\n😀hello😀"))
        for (text in listOf("C   G\nHel😀lo wor", "C G\n😀hello😀", "C      G\n😀hello😀")) {
            val result = convert(text)
            for (index in result.indices) {
                if (result[index].isHighSurrogate()) assertTrue(result.getOrNull(index + 1)?.isLowSurrogate() == true)
                if (result[index].isLowSurrogate()) assertTrue(result.getOrNull(index - 1)?.isHighSurrogate() == true)
            }
        }
    }

    @Test
    fun extractedControlsCannotHideChordsButDirectionMarksSurvive() {
        assertEquals("[C]Hello [G]world x\n", convert("\u202eC   G\nHello world\u0007 x"))
        assertEquals("Hello   world next\n", convert("Hello\tworld\u00a0next"))
        assertEquals("שלום\u200f!\u200e\u061c\n", convert("שלום\u200f!\u200e\u061c"))
        assertEquals("Hello world\n", convert("\u0000Hello\u007f \u202aworld\u202c\u2066\u2069"))
    }

    @Test
    fun aCapitalizedLoneChordCanEndAHeadedSection() {
        assertEquals("{comment: Outro}\n[C]\n", convert("[Outro]\nC"))
        assertTrue(convert("[Verse 1]\nC   G\nHello\n\n[Outro]\nC").endsWith("{comment: Outro}\n[C]\n"))
        assertEquals("{comment: Intro}\n[C]\n", convert("Intro: C"))
        assertEquals("{start_of_verse: Verse 1}\nA\nday\n{end_of_verse}\n", convert("Verse 1\nA\nday"))
        assertEquals("{start_of_verse: Verse 1}\na\n{end_of_verse}\n", convert("Verse 1\na"))
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
    fun aTitleWrappedOverSeveralLinesOfItsTypeIsOneTitleAndTheSmallerLineUnderItItsArtist() {
        val lines = listOf("A song with", "a long title").map { ChordSheet.Line(listOf(span(it, 0.0, 24.0, size = 2.0, bold = true))) } +
            ChordSheet.Line(listOf(span("Someone", 0.0, 7.0, size = 1.0))) +
            ChordSheet.ofPlainText("Am    C\nHello world").pages.single().lines
        assertEquals(
            "{title: A song with a long title}\n{artist: Someone}\n\n[Am]Hello [C]world\n",
            ChordSheetConverter.convert(ChordSheet(listOf(ChordSheet.Page(lines)))).single(),
        )
    }

    @Test
    fun aBoldChordOverPlainLyricsOfItsSizeIsAChordWhereNoLineHoldsTwo() {
        fun sheet(vararg lines: ChordSheet.Line) = ChordSheet(listOf(ChordSheet.Page(lines.toList())))
        val wrapped = sheet(
            ChordSheet.Line(listOf(span("Am", 0.0, 12.0, bold = true))),
            ChordSheet.Line(listOf(span("Hello there", 0.0, 50.0))),
            ChordSheet.Line(listOf(span("F", 15.0, 20.0, bold = true))),
            ChordSheet.Line(listOf(span("the world", 0.0, 45.0))),
        )
        assertEquals("[Am]Hello there\nthe [F]world\n", ChordSheetConverter.convert(wrapped).single())
        val index = sheet(
            ChordSheet.Line(listOf(span("A", 0.0, 8.0, size = 14.0, bold = true))),
            ChordSheet.Line(listOf(span("Amazing Grace", 0.0, 60.0))),
            ChordSheet.Line(listOf(span("Auld Lang Syne", 0.0, 60.0))),
        )
        assertEquals("A\nAmazing Grace\nAuld Lang Syne\n", ChordSheetConverter.convert(index).single())
        val word = sheet(ChordSheet.Line(listOf(span("a", 0.0, 5.0, bold = true))), ChordSheet.Line(listOf(span("little song", 0.0, 50.0))))
        assertEquals("a\nlittle song\n", ChordSheetConverter.convert(word).single())
    }

    @Test
    fun derivesPlainTitleAndExpandsTabs() {
        assertTrue(convert("Someone - Song\n\nAm C\nHello world").startsWith("{title: Song}\n{artist: Someone}"))
        assertEquals("[Am]Hello   [C]world\n", convert("Am\tC\nHello   world"))
    }

    private fun span(text: String, start: Double, end: Double, size: Double = 10.0, bold: Boolean = false) =
        ChordSheet.Span(text, start, end, size, isBold = bold)
}
