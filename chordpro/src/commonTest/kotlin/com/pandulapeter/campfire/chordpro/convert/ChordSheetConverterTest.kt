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

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.edit.ChordProLiteralText
import com.pandulapeter.campfire.chordpro.edit.ChordProPrettifier
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.CommentPlacement
import com.pandulapeter.campfire.chordpro.model.SectionType
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class ChordSheetConverterTest {
    private fun convert(text: String) = ChordSheetConverter.convert(ChordSheet.ofPlainText(text)).single()

    @Test
    fun `preserves ChordPro including collections and line endings`() {
        for (text in listOf("\ufeff{title: Song}\r\n[A]Hello\r\n{new_song}\r\n{title: Other}\r\n", "[G]Hello\r\n[C]world", "{t: Title}\n")) {
            assertEquals(text, convert(text))
        }
    }

    @Test
    fun `a heading the parser does not know still ends the lyrics before it`() {
        val result = convert("Hello\nworld\nInterlude\nC   G")

        assertTrue("world\n\n{comment: Interlude}" in result, result)
        val comment = ChordProParser.parse(ChordProPrettifier.prettify(result)).blocks
            .filterIsInstance<ChordProBlock.Comment>()
            .single { it.text == "Interlude" }
        assertEquals(CommentPlacement.BETWEEN_SECTIONS, comment.placement)
    }

    @Test
    fun `merges and snaps chords without changing their order`() {
        assertEquals("[Am]Hello [C]world\n", convert("Am     C\nHello world"))
        assertEquals("[Am]Hello [C]world\n", convert(" Am  C\nHello world"))
        assertEquals("[Am]Hel[C]lo world\n", convert("Am C\nHello world"))
        assertEquals("[Am]Hello [C]\n", convert("Am            C\nHello"))
    }

    @Test
    fun `one chord is ambiguous until another line makes the context clear`() {
        assertEquals("A\nlong time ago\n", convert("A\nlong time ago"))
        assertEquals("A long time ago\nAm I\n", convert("A long time ago\nAm I"))
        assertEquals("[Am] [F]\n\n[A]long time ago\n", convert("Am F\n\nA\nlong time ago"))
        // Latin chords, which the import brings into the standard notation afterwards.
        assertEquals("[Lam]1 4 [Re]5 [Mi]\n", convert("Lam Re Mi\n1 4 5"))
    }

    @Test
    fun `a row of bare Latin notes is a lyric unless the sheet is Latin`() {
        assertEquals("La La La\nque bonita\n", convert("La La La\nque bonita"))
        assertEquals("[Am]Hel[C]lo world\nLa La La\nque bonita\n", convert("Am C\nHello world\nLa La La\nque bonita"))
        assertEquals("[Do]can[Sol]ta [Lam]\n[La]que [La]bo[La]nita\n", convert("Do Sol Lam\ncanta\nLa La La\nque bonita"))
        // Chords set over the syllables they fall on stand apart, which a sung line never does.
        assertEquals("[Do]Cielito li[Fa]ndo, de la [Sol]sierra\n", convert("Do        Fa        Sol\nCielito lindo, de la sierra"))
    }

    @Test
    fun `recognizes German and lowercase chords and keeps furniture`() {
        assertEquals("[am] [H7] | % x2 N.C.\n", convert("am H7 | % x2 N.C."))
        assertEquals("| [Am] [F] | [C] [G] |\n", convert("|Am F|C G|"))
        assertFalse(ChordSheetConverter.isChordLine("Am I"))
        assertTrue(ChordSheetConverter.isChordLine("F\u266fm B\u266d"))
        assertEquals("[F#m] [Bb]\n", convert("F\u266fm B\u266d"))
    }

    @Test
    fun `sections metadata and recalls become directives`() {
        val text = "Capo: 3rd fret\nHangnem: G\n120 BPM\nTime: 4/4\nEl\u0151ad\u00f3: Someone\n\u00a9 1900\nVerse 2:\nAm C\nHello world\n[Refr\u00e9n]\nAm F\nSing along\n\nRef."
        val result = convert(text)
        assertTrue(result.contains("{capo: 3}\n{key: G}\n{tempo: 120}\n{time: 4/4}\n{artist: Someone}\n{copyright: 1900}"))
        assertTrue(result.contains("{start_of_verse: Verse 2}"))
        assertTrue(result.contains("{end_of_verse}\n{start_of_chorus: Refr\u00e9n}"))
        assertTrue(result.endsWith("{end_of_chorus}\n{chorus}\n"))
        assertEquals("{comment: Intro}\n[Am] [F] [C] [G]\n", convert("Intro: Am F C G"))
    }

    @Test
    fun `a later tempo or time is a change written in place`() {
        val text = "Tempo: 120\nTime: 4/4\nVerse 1\nAm C\nHello world\n" + "Am C\nla la\n".repeat(8) + "Tempo: 90   Time: 3/4\nChorus\nAm F\nSing along"
        val result = convert(text)
        assertTrue(result.startsWith("{tempo: 120}\n{time: 4/4}\n"), result)
        assertTrue(result.contains("{end_of_verse}\n{tempo: 90}\n{time: 3/4}\n{start_of_chorus: Chorus}"), result)
        val song = ChordProParser.parse(result)
        assertEquals(listOf(ChordProBlock.Timing("90", "3/4")), song.blocks.filterIsInstance<ChordProBlock.Timing>())
        assertFalse(convert("Verse 1\nAm C\nHello world\n" + "Am C\nla la\n".repeat(8) + "Time: after time\nAm C\nla la").contains("{time"))
    }

    @Test
    fun `the row campfires PDF heads a song with is read as its values`() {
        val text = "Shapes\nSomeone\nKey: B   Transposition: +2   Capo: 2   Tempo: 96 BPM   Time: 3/4\nVerse 1\nB E\nHello world"
        val result = convert(text)
        assertTrue(result.contains("{key: B}\n{capo: 2}\n{tempo: 96}\n{time: 3/4}\n"), result)
        assertFalse(result.contains("Transposition"), result)
        assertTrue(convert("Hangnem: B   Transzpon\u00e1l\u00e1s: -3   Temp\u00f3: 96 BPM   \u00dctemmutat\u00f3: 3/4\nVerse 1\nB E\nHello world")
            .startsWith("{key: B}\n{tempo: 96}\n{time: 3/4}\n"))
        // Further down only a change of tempo or time is a row of values; a key there is not the song's.
        assertFalse(convert("Verse 1\nAm C\nHello world\n" + "Am C\nla la\n".repeat(8) + "Key: A   Capo: 2\nAm C\nla la").contains("{key"))
    }

    @Test
    fun `heading decoration is left out of section names`() {
        val result = convert("[Verse 1]\nC   G\nHello world\n\n[Intro]\nC G\n\n[Chorus]\nAm F\nLa la")
        assertTrue(result.contains("{start_of_verse: Verse 1}"))
        assertTrue(result.contains("{comment: Intro}"))
        assertTrue(result.contains("{start_of_chorus: Chorus}"))
        assertEquals(convert("Verse 1\nC G\nHello world"), convert("Verse 1:\nC G\nHello world"))
        assertEquals(convert("Chorus\nAm F\nLa la"), convert("(Chorus)\nAm F\nLa la"))
        assertEquals("{start_of_verse: Verse 1}\nHello world\n{end_of_verse}\n", convert("Verse 1\nHello world"))
    }

    @Test
    fun `lyrics that start like a label stay lyrics`() {
        assertEquals("[Am]By the [C]rivers of Babylon\n", convert("Am     C\nBy the rivers of Babylon"))
        assertEquals("[Am]Time after [C]time\n", convert("Am         C\nTime after time"))
        assertEquals("[Am]Key to my [C]heart\n", convert("Am         C\nKey to my heart"))
        assertTrue(convert("Capo 2\nKey G\nTime 4/4\nAm C\nHello world").startsWith("{capo: 2}\n{key: G}\n{time: 4/4}\n"))
        assertTrue(convert("By: Someone\nAm C\nHello world").startsWith("{artist: Someone}\n"))
    }

    @Test
    fun `a header labelled with semicolons names the song, its album, and the year its credit ends in, each once`() {
        assertEquals(
            "{artist: Someone}\n{title: Hello}\n{album: Best of}\n{year: 1972}\n\n[Am]Hello [C]world\n",
            convert("Artist; Someone\nSong; Hello\nAlbum; Best of\nWords & music by Someone 1972\n\nAm     C\nHello world"),
        )
        assertTrue(convert("Title: Hello\nBy: Someone\nAm     C\nHello world").startsWith("{title: Hello}\n{artist: Someone}\n\n"))
    }

    @Test
    fun `an artist label keeps a name ending in a number whole and takes a year off only after a comma`() {
        val band = convert("Artist: The 1975\nTitle: Somebody Else\n\nAm     C\nHello world")
        assertTrue(band.startsWith("{artist: The 1975}"), band)
        assertFalse("{year:" in band, band)
        val hungarian = convert("El\u0151ad\u00f3: Valaki 1972\nTitle: Hello\n\nAm     C\nHello world")
        assertTrue("{artist: Valaki 1972}" in hungarian, hungarian)
        assertFalse("{year:" in hungarian, hungarian)
        val comma = convert("Artist: David Bowie, 1972\nTitle: Hello\n\nAm     C\nHello world")
        assertTrue("{artist: David Bowie}" in comma && "{year: 1972}" in comma, comma)
        val credit = convert("Title: Hello\nBy: Someone 1972\n\nAm     C\nHello world")
        assertTrue("{artist: Someone}" in credit && "{year: 1972}" in credit, credit)
    }

    @Test
    fun `a repeat sign glued to a chord is a mark of its own and a double bar line stays one`() {
        val intro = convert("Intro\n\n||:Am |    | C    :||\n\nVerse 1\nAm   C\nHello world")
        assertTrue("\n||: [Am] | | [C] :||\n" in intro, intro)
        val outro = convert("Verse 1\nAm   C\nHello world\n\nOutro\n\nAm | F ||")
        assertTrue("\n[Am] | [F] ||\n" in outro, outro)
    }

    @Test
    fun `a lyric indented under its first chord starts at that chord`() {
        assertEquals("[Am]Hello [C]world\n", convert("Am     C\n  Hello world"))
        assertEquals("[Am]\"Let the [C]children\n", convert("Am         C\n   \"Let the children"))
        // Laid out in glyphs, the indent has positions of its own, and two chords played before the words keep it.
        fun glyphs(text: String) = ChordSheet.Line(text.mapIndexed { index, character -> span(character.toString(), index * 6.0, index * 6.0 + 6.0) })
        val sheet = ChordSheet(listOf(ChordSheet.Page(listOf(glyphs("Am    C"), glyphs("          Hello world")))))
        assertEquals("[Am]      [C]    Hello world\n", ChordSheetConverter.convert(sheet).single())
    }

    @Test
    fun `chord shapes listed one a line become definitions, a chord listed with and without a star neither`() {
        val result = convert(
            "Am C\nHello world\n\nBb(#11)/A = x03330\nC: x32010\nD7 (x-x-0-2-1-2)\nF 2 0 1 0\nBb  = 688766\nBb* = 013331\nDm 2010\nLove 202000",
        )
        assertTrue(
            result.startsWith(
                "{define: Bb(#11)/A base-fret 1 frets x 0 3 3 3 0}\n{define: C base-fret 1 frets x 3 2 0 1 0}\n" +
                    "{define: D7 base-fret 1 frets x x 0 2 1 2}\n{define: F base-fret 1 frets 2 0 1 0}\n\n",
            ),
            result,
        )
        assertTrue(result.endsWith("Bb  = 688766\nBb* = 013331\nDm 2010\nLove 202000\n"), result)
        assertTrue(convert("Am C\nHello world\n\nEm* = 022000").startsWith("{define: Em base-fret 1 frets 0 2 2 0 0 0}\n"))
    }

    @Test
    fun `ChordPro with few chords or only recalls is still ChordPro`() {
        val sparse = "Verse one\nno chords here\n[G]Only this line\nhas a chord\nand that is all\n"
        assertEquals(sparse, convert(sparse))
        val recall = "First [G] line\nsecond line\nthird line\n{chorus}\n"
        assertEquals(recall, convert(recall))
        assertEquals("A footnote (C) here\nand more\nand more\n", convert("A footnote [C] here\nand more\nand more"))
    }

    @Test
    fun `unclosed brackets are escaped in linear time`() {
        val text = "[".repeat(100_000) + "\nhello world"
        val started = TimeSource.Monotonic.markNow()
        val result = convert(text)
        assertTrue(started.elapsedNow() < 5.seconds)
        assertEquals("(".repeat(100_000) + "\nhello world\n", result)
        assertEquals("(a b (C)x\nplain words\nmore words\n", convert("[a b [C]x\nplain words\nmore words"))
    }

    @Test
    fun `seeded sheets keep their chord placement`() {
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
    fun `long chord rows and inline runs convert within the work bound`() {
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
    fun `chords never split supplementary characters`() {
        assertEquals("[C]Hel😀[G]lo wor\n", convert("C   G\nHel😀lo wor"))
        assertEquals("[Am]x😀[G]hello\n", convert("Am G\nx😀hello"))
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
    fun `extracted controls cannot hide chords but direction marks survive`() {
        assertEquals("[C]Hello [G]world x\n", convert("\u202eC   G\nHello world\u0007 x"))
        assertEquals("Hello   world next\n", convert("Hello\tworld\u00a0next"))
        assertEquals("שלום\u200f!\u200e\u061c\n", convert("שלום\u200f!\u200e\u061c"))
        assertEquals("Hello world\n", convert("\u0000Hello\u007f \u202aworld\u202c\u2066\u2069"))
    }

    @Test
    fun `a capitalized lone chord can end a headed section`() {
        assertEquals("{comment: Outro}\n[C]\n", convert("[Outro]\nC"))
        assertTrue(convert("[Verse 1]\nC   G\nHello\n\n[Outro]\nC").endsWith("{comment: Outro}\n[C]\n"))
        assertEquals("{comment: Intro}\n[C]\n", convert("Intro: C"))
        assertEquals("{start_of_verse: Verse 1}\nA\nday\n{end_of_verse}\n", convert("Verse 1\nA\nday"))
        assertEquals("{start_of_verse: Verse 1}\na\n{end_of_verse}\n", convert("Verse 1\na"))
    }

    @Test
    fun `letter spacing does not split words`() {
        val spaced = ChordSheet.Line(listOf(span("T", 0.0, 6.7, 12.0), span("h", 7.7, 14.4, 12.0), span("i", 15.4, 18.4, 12.0), span("s", 19.4, 25.4, 12.0), span("i", 30.0, 33.0, 12.0), span("s", 34.0, 40.0, 12.0)))
        assertEquals("This is\n", ChordSheetConverter.convert(ChordSheet(listOf(ChordSheet.Page(listOf(spaced))))).single())
    }

    @Test
    fun `a row of chords over a staff stays in the tab, its columns kept`() {
        assertEquals(
            "{start_of_tab}\nAm        C\ne|---0---|---3---|\nB|---1---|---1---|\n{end_of_tab}\n",
            convert("Am        C\ne|---0---|---3---|\nB|---1---|---1---|"),
        )
    }

    @Test
    fun `tablature needs at least two staff lines`() {
        assertEquals("{start_of_tab}\ne|---0---|\nB|---1---|\n{end_of_tab}\n", convert("e|---0---|\nB|---1---|"))
        assertEquals("e|---0---|\n", convert("e|---0---|"))
    }

    @Test
    fun `parenthesized chords need a majority`() {
        assertEquals("[Am] Today [C] the day (repeat)\n", convert("(Am) Today (C) the day (repeat)"))
        assertEquals("(Am) Today (repeat)\n", convert("(Am) Today (repeat)"))
        assertEquals("(softly) hello\n", convert("(softly) hello"))
    }

    @Test
    fun `ordinary prose cannot accidentally become markup`() {
        assertEquals("\uff03 heading\n(unknown: value)\n(something) else\n", convert("# heading\n{unknown: value}\n[something] else"))
        assertEquals("office flower\nnext line\n", convert("o\ufb03ce \ufb02ower\u00ad\u200b\nnext\u00a0line"))
        assertEquals("hello\n\nworld\n", convert("hello\n\n\n\nworld\n"))
        assertEquals("\uff03 hello (yes)\n", ChordProLiteralText.escape("# hello [yes]\n"))
    }

    @Test
    fun `maps proportional spans and coincident chords to characters`() {
        val chords = ChordSheet.Line(listOf(span("Am", 100.0, 112.0), span("C", 115.0, 120.0), span("G", 115.0, 120.0)))
        val lyric = ChordSheet.Line(listOf(span("W", 100.0, 110.0), span("i", 110.0, 112.0), span("d", 112.0, 117.0), span("e", 117.0, 122.0)))
        assertEquals("[Am]Wi[C][G]de\n", ChordSheetConverter.convert(ChordSheet(listOf(ChordSheet.Page(listOf(chords, lyric))))).single())
    }

    @Test
    fun `reads styled inline chords`() {
        val lyric = ChordSheet.Line(listOf(span("Today ", 0.0, 30.0), span("Am", 30.0, 40.0, bold = true), span(" we sing", 40.0, 80.0)))
        assertEquals("Today [Am] we sing\n", ChordSheetConverter.convert(ChordSheet(listOf(ChordSheet.Page(listOf(lyric))))).single())
        val glyphs = ChordSheet.Line(listOf(span("Today ", 0.0, 30.0), span("A", 30.0, 35.0, bold = true), span("m", 35.0, 40.0, bold = true), span(" we sing", 40.0, 80.0)))
        assertEquals("Today [Am] we sing\n", ChordSheetConverter.convert(ChordSheet(listOf(ChordSheet.Page(listOf(glyphs))))).single())
    }

    @Test
    fun `styled page titles split songbooks but continuation pages stay together`() {
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
    fun `a page opening with a bold chorus continues a song whose title is set larger`() {
        val title = ChordSheet.Line(listOf(span("Song", 0.0, 8.0, size = 2.0)))
        val sheet = ChordSheet(
            listOf(
                ChordSheet.Page(listOf(title) + ChordSheet.ofPlainText("Am    C\nHello world").pages.single().lines),
                ChordSheet.Page(bold(ChordSheet.ofPlainText("Sing the chorus\nAm    C\nSing it again").pages.single().lines)),
            ),
        )
        val result = ChordSheetConverter.convert(sheet).single()
        assertTrue(result.startsWith("{title: Song}"))
        assertTrue(result.contains("Sing the chorus"))
    }

    @Test
    fun `bold titles of the body's size split a songbook but a page opening with a bold chorus does not`() {
        fun page(lead: String, words: String, isChorus: Boolean) = ChordSheet.Page(
            bold(ChordSheet.ofPlainText(lead).pages.single().lines) +
                ChordSheet.ofPlainText("Am    C\n$words").pages.single().lines.let { if (isChorus) bold(it) else it },
        )
        val sheet = ChordSheet(
            listOf(
                page("First", "First words", isChorus = false),
                page("Sing along", "Sing it again", isChorus = true),
                page("Second", "Other words", isChorus = false),
            ),
        )
        val result = ChordSheetConverter.convert(sheet)
        assertEquals(2, result.size)
        assertTrue(result[0].startsWith("{title: First}"))
        assertTrue(result[0].contains("Sing along"))
        assertTrue(result[1].startsWith("{title: Second}"))
    }

    @Test
    fun `a page repeating the song's title continues the song and leaves the repetition out`() {
        fun sheet(header: ChordSheet.Line) = ChordSheet(
            listOf(
                ChordSheet.Page(
                    listOf(ChordSheet.Line(listOf(span("Let It Be", 0.0, 18.0, size = 2.0)))) +
                        ChordSheet.ofPlainText("Am    C\nHello world").pages.single().lines,
                ),
                ChordSheet.Page(listOf(header) + ChordSheet.ofPlainText("G     F\nMore words").pages.single().lines),
            ),
        )
        val marked = ChordSheetConverter.convert(sheet(ChordSheet.Line(listOf(span("Let It Be (cont.)", 0.0, 17.0, size = 1.0))))).single()
        assertFalse(marked.contains("cont"))
        assertTrue(marked.contains("[G]More [F]words"), marked)
        val repeated = ChordSheetConverter.convert(sheet(ChordSheet.Line(listOf(span("Let It Be", 0.0, 18.0, size = 2.0))))).single()
        assertEquals(1, Regex("Let It Be").findAll(repeated).count())
        val sung = ChordSheetConverter.convert(sheet(ChordSheet.Line(listOf(span("Let it be", 0.0, 9.0, size = 1.0))))).single()
        assertTrue(sung.contains("\nLet it be\n"), sung)
    }

    @Test
    fun `a title wrapped over several lines of its type is one title and the smaller line under it its artist`() {
        val lines = listOf("A song with", "a long title").map { ChordSheet.Line(listOf(span(it, 0.0, 24.0, size = 2.0, bold = true))) } +
            ChordSheet.Line(listOf(span("Someone", 0.0, 7.0, size = 1.0))) +
            ChordSheet.ofPlainText("Am    C\nHello world").pages.single().lines
        assertEquals(
            "{title: A song with a long title}\n{artist: Someone}\n\n[Am]Hello [C]world\n",
            ChordSheetConverter.convert(ChordSheet(listOf(ChordSheet.Page(lines)))).single(),
        )
    }

    @Test
    fun `a bold chord over plain lyrics of its size is a chord where no line holds two`() {
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
    fun `a lyric of Campfire's own export reads back unchanged, a word its markup set in bold included`() {
        fun glyphs(text: String, size: Double, bold: Boolean = false) = ChordSheet.Line(
            text.mapIndexed { index, character -> span(character.toString(), index * size * 0.6, (index + 1) * size * 0.6, size, bold) },
        )
        val page = ChordSheet.Page(
            listOf(
                glyphs("House of the Rising Sun", 19.0, bold = true),
                glyphs("Animals", 11.0),
                glyphs("Verse", 11.0, bold = true),
                glyphs("There is a house in New Orleans", 12.0),
            ),
        )
        val result = ChordSheetConverter.convert(ChordSheet(listOf(page))).single()
        assertTrue(result.startsWith("{title: House of the Rising Sun}\n{artist: Animals}\n"), result)
        assertTrue("\nThere is a house in New Orleans\n" in result, result)
        assertFalse('[' in result, result)
    }

    @Test
    fun `a right-to-left row of Campfire's own export takes each chord at the character it hangs from`() {
        // "שלום world" laid out from the right: the Hebrew word at the right edge, the Latin one left of it, each glyph
        // where the export drew it, the row's text in its reading order over them.
        val glyphs = listOf(
            span("\u05e9", 92.0, 100.0), span("\u05dc", 86.0, 92.0), span("\u05d5", 83.0, 86.0), span("\u05dd", 77.0, 83.0),
            span(" ", 74.0, 77.0), span("w", 44.0, 52.0), span("o", 52.0, 58.0), span("r", 58.0, 62.0), span("l", 62.0, 65.0),
            span("d", 65.0, 74.0),
        )
        val row = ChordSheet.Line(listOf(span(glyphs.joinToString("") { it.text }, 44.0, 100.0).copy(pieces = glyphs)))
        val chords = ChordSheet.Line(listOf(span("G", 44.0, 52.0, bold = true), span("C", 75.5, 83.0, bold = true), span("Am", 88.5, 100.0, bold = true)))
        val page = ChordSheet.Page(listOf(ChordSheet.Line(listOf(span("\u05e9\u05d9\u05e8", 70.0, 100.0, size = 19.0, bold = true))), chords, row))
        val result = ChordSheetConverter.convert(ChordSheet(listOf(page), isCampfireExport = true)).single()
        assertTrue("[Am]\u05e9\u05dc\u05d5[C]\u05dd [G]world" in result, result)
    }

    @Test
    fun `a right-to-left plain text sets each chord where a right-to-left display shows it, not at its character's index`() {
        // "הנה מה טוב ומה נעים" under "Dm         Am": shown from the right, the chords and the spaces between them are one
        // left-to-right run, so Am stands over the first word and Dm over the fourth, the other way round from their
        // indices in the row.
        val lyric = "\u05d4\u05e0\u05d4 \u05de\u05d4 \u05d8\u05d5\u05d1 \u05d5\u05de\u05d4 \u05e0\u05e2\u05d9\u05dd"
        val result = convert("Dm         Am\n$lyric")
        assertTrue("[Am]\u05d4\u05e0\u05d4 \u05de\u05d4 \u05d8\u05d5\u05d1 [Dm]\u05d5\u05de\u05d4" in result, result)
    }

    @Test
    fun `a Latin word's chord in a right-to-left plain text stands at the word's left edge`() {
        // "שבת אחים (Psalm 133)" shown from the right: "(Psalm 133)" left of the Hebrew words, E typed over its P.
        val result = convert("E                Am\n\u05e9\u05d1\u05ea \u05d0\u05d7\u05d9\u05dd (Psalm 133)")
        assertTrue("[Am]\u05e9\u05d1\u05ea \u05d0\u05d7\u05d9\u05dd ([E]Psalm 133)" in result, result)
    }

    @Test
    fun `derives plain title and expands tabs`() {
        assertTrue(convert("Someone - Song\n\nAm C\nHello world").startsWith("{title: Song}\n{artist: Someone}"))
        assertEquals("[Am]Hello   [C]world\n", convert("Am\tC\nHello   world"))
    }

    private fun span(text: String, start: Double, end: Double, size: Double = 10.0, bold: Boolean = false) =
        ChordSheet.Span(text, start, end, size, isBold = bold)

    /** The [lines] with every line that is not a chord row set in bold, the way many sheets set a chorus. */
    private fun bold(lines: List<ChordSheet.Line>) = lines.map { line ->
        if (ChordSheetConverter.isChordLine(line.spans.joinToString(" ") { it.text })) line else line.copy(spans = line.spans.map { it.copy(isBold = true) })
    }

    @Test
    fun `a bracketed heading with a singer after its colon is the section it names`() {
        val result = convert("[Verse 1: Some Artist]\nC     G\nHello world\n\n[Pre chorus]\nAm     F\nAlmost there\n\n[Chorus: A & B]\nC     G\nSing along\n\n[Chorus x2]")
        assertTrue("{start_of_verse: Verse 1: Some Artist}" in result, result)
        assertTrue("{end_of_verse}" in result, result)
        assertTrue("{start_of_bridge: Pre chorus}" in result, result)
        assertTrue("{end_of_bridge}\n{start_of_chorus: Chorus: A & B}" in result, result)
        assertTrue(result.endsWith("{end_of_chorus}\n{chorus}\n"), result)
    }

    @Test
    fun `an unknown bracketed heading is a heading, not a lyric`() {
        for (heading in listOf("Instrumental break", "Vamp", "Riff")) {
            val result = convert("[Verse 1]\nC     G\nHello world\n\n[$heading]\nG   D")
            assertTrue(result.endsWith("{comment: $heading}\n[G] [D]\n"), result)
        }
        assertEquals("(Riff)\nsome words here\n", convert("[Riff]\nsome words here"))
    }

    @Test
    fun `common English heading words are sections and never take a chord row`() {
        for (heading in listOf("Hook", "Tag", "Coda", "Ending", "Break", "Chorus (x2)", "Instrumental break")) {
            val result = convert("Verse 1\nC     G\nHello world\n\n$heading\nG D Em C")
            assertTrue("[G] [D] [Em] [C]" in result, result)
            assertFalse("[G]$heading" in result || "{start_of_verse: Verse 1}\n[C]Hello [G]world\n\n$heading" in result, result)
        }
        assertTrue("{start_of_chorus: Hook}" in convert("Verse 1\nC     G\nHello world\n\nHook\nG D Em C\nSing it"))
        assertTrue("{start_of_chorus: Chorus (x2)}" in convert("Verse 1\nC     G\nHello world\n\nChorus (x2)\nG D Em C\nSing it"))
    }

    @Test
    fun `heading words of the European chord sites are sections`() {
        val sheets = mapOf(
            "Strophe 1" to "verse", "Strofa 1" to "verse", "Zwrotka 1" to "verse", "Couplet 1" to "verse", "Estrofa" to "verse",
            "1. versszak" to "verse", "Vsz." to "verse", "Ritornello" to "chorus", "Refren" to "chorus", "Refrein" to "chorus",
            "Refr\u00e3o:" to "chorus", "Coro:" to "chorus", "Estribillo" to "chorus", "R:" to "chorus", "Puente" to "bridge",
            "Pr\u00e9-Refr\u00e3o" to "bridge", "Brug" to "bridge", "\u00c1tk\u00f6t\u00e9s" to "bridge",
        )
        for ((heading, type) in sheets) {
            val result = convert("$heading\nC     G\nHello world")
            assertTrue(result.startsWith("{start_of_$type: ${heading.removeSuffix(":")}}\n[C]Hello [G]world\n{end_of_$type}"), result)
        }
        convert("Parte 1 de 3\nC G\n\nVerse 1\nC     G\nHello world").let { assertTrue(it.startsWith("{comment: Parte 1 de 3}\n[C] [G]"), it) }
        convert("Refr\u00e3o: C G Am F").let { assertTrue("{start_of_chorus: Refr\u00e3o}\n[C] [G] [Am] [F]" in it, it) }
        convert("1. versszak: G D").let { assertTrue("{start_of_verse: 1. versszak}\n[G] [D]" in it, it) }
    }

    @Test
    fun `Hungarian R and Vsz headings close the chorus before them`() {
        val result = convert("Vsz.\nC     G\nHello world\n\nRef.:\nAm     F\nSing along\n\nVsz.\nC     G\nSecond verse\n\nR:\nAm     F\nSing along")
        val sections = ChordProParser.parse(ChordProPrettifier.prettify(result)).blocks.filterIsInstance<ChordProBlock.Section>()
        assertEquals(listOf(SectionType.Verse, SectionType.Chorus, SectionType.Verse, SectionType.Chorus), sections.map { it.type }, result)
        assertEquals(1, sections[2].lines.size, result)
    }

    @Test
    fun `a heading closes the section before it`() {
        val result = convert("Verse 1\nC     G\nHello world\nInterlude\nC   G")
        assertTrue("{end_of_verse}\n\n{comment: Interlude}" in result, result)
    }

    @Test
    fun `a lyric that starts with a heading word stays a lyric`() {
        val result = convert("Verse 1\nC     G\nHello world\nChorus of angels sing\nTag along with me\nBreak my heart again")
        assertTrue("[C]Hello [G]world\nChorus of angels sing\nTag along with me\nBreak my heart again\n{end_of_verse}" in result, result)
    }

    @Test
    fun `a label's chords are their own line, never set into the lyric under it`() {
        assertEquals(
            "{start_of_verse: Verse 1}\n[G] [D] [Em] [C]\nThe river runs beside the road\n{end_of_verse}\n",
            convert("Verse 1: G D Em C\nThe river runs beside the road"),
        )
        assertEquals("{start_of_chorus: Chorus}\n[C] [G] [D]\nOh riverlight\n{end_of_chorus}\n", convert("Chorus: C G D\nOh riverlight"))
    }

    @Test
    fun `a bracketed label followed by chords is a heading and its chords`() {
        assertEquals("{comment: Intro}\n[G] [D] [Em] [C]\n", convert("[Intro] G D Em C"))
        assertEquals("{start_of_chorus: Chorus}\n[C] [G]\nSing along\n{end_of_chorus}\n", convert("[Chorus] C G\nSing along"))
    }

    @Test
    fun `a title ending in Chord keeps it`() {
        assertTrue(convert("The Lost Chord\n\nAm     C\nHello world").startsWith("{title: The Lost Chord}\n\n"))
        assertTrue(
            convert("The Lost Chord Chords by The Invented Band\n\nAm     C\nHello world")
                .startsWith("{title: The Lost Chord}\n{artist: The Invented Band}\n\n")
        )
    }

    @Test
    fun `a UG title line names the title and the artist`() {
        val chords = "\n\nAm     C\nHello world"
        assertTrue(convert("Riverlight Chords by The Invented Band$chords").startsWith("{title: Riverlight}\n{artist: The Invented Band}\n\n"))
        assertTrue(convert("Riverlight chords$chords").startsWith("{title: Riverlight}\n\n"))
        assertTrue(convert("Riverlight Chords (ver 2) by Someone$chords").startsWith("{title: Riverlight}\n{artist: Someone}\n\n"))
        assertTrue(convert("The Invented Band - Riverlight Chords$chords").startsWith("{title: Riverlight}\n{artist: The Invented Band}\n\n"))
        assertTrue(convert("Stand by Me$chords").startsWith("{title: Stand by Me}\n\n"))
        assertTrue(convert("Hallelujah (Live)$chords").startsWith("{title: Hallelujah (Live)}\n\n"))
    }

    @Test
    fun `a title above metadata lines is still the title`() {
        val result = convert("Riverlight Chords by Someone\nCapo: 2nd fret\nKey: G\n\n[Verse 1]\nAm     C\nHello world")
        val header = ChordProParser.parse(result).metadata
        assertEquals("Riverlight", header.title, result)
        assertEquals("Someone", header.artist, result)
        assertEquals(2, header.capo, result)
        assertEquals("G", header.key, result)
    }

    @Test
    fun `a title, its credit, and a blank line before the chords`() {
        assertTrue(convert("Riverlight\nby The Invented Band\n\nAm     C\nHello world").startsWith("{title: Riverlight}\n{artist: The Invented Band}\n\n"))
        assertTrue(convert("Down by the Riverlight\nSomeone\n\nAm     C\nHello world").startsWith("{title: Down by the Riverlight}\n{artist: Someone}\n\n"))
        assertTrue(convert("Riverlight\nAm     C\nHello world").startsWith("{title: Riverlight}\n\n[Am]Hello [C]world"))
    }

    @Test
    fun `a caller that knows the title has the first lines read as lines`() {
        val text = "Hum along softly now\nG        D\nWalking down the road"

        val result = ChordSheetConverter.convert(ChordSheet.ofPlainText(text), guessesTitle = false).single()

        assertFalse("{title" in result, result)
        assertTrue(result.startsWith("Hum along softly now\n[G]Walking [D]down the road"), result)
        assertTrue(convert(text).startsWith("{title: Hum along softly now}"))
    }

    @Test
    fun `marked chord rows are set as marked`() {
        assertEquals(
            "[G]Walking down the road\n[Do]Sing[Re]ing\nG   D",
            ChordSheetConverter.setChordRows("G\nWalking down the road\nDo  Re\nSinging\nG   D", setOf(0, 2)),
        )
    }

    @Test
    fun `a lyrics-only paste with headings gets its title`() {
        val result = convert("Riverlight\n\n[Verse 1]\nThe river runs\nbeside the road\nall the way\nback home")
        assertTrue(result.startsWith("{title: Riverlight}\n\n{start_of_verse: Verse 1}\nThe river runs"), result)
    }

    @Test
    fun `a chord summary line is not a title`() {
        assertFalse(convert("Chords used: G D Em C\n\n[Verse]\nAm     C\nHello world").contains("{title"))
        assertFalse(convert("Chords\n\nAm     C\nHello world").contains("{title"))
    }

    @Test
    fun `a lyric line that looks like a title is left alone without structure`() {
        assertEquals("Riverlight\n\nThe river runs\nbeside the road\nall the way\nback home\n", convert("Riverlight\n\nThe river runs\nbeside the road\nall the way\nback home"))
    }

    @Test
    fun `capo in Roman numerals and spelled-out capo lines become the header`() {
        for (line in listOf("Capo: II", "Capo on 2nd fret", "Capo II", "Capo: IV fret", "Kapo 2")) {
            assertTrue(convert("$line\nAm     C\nHello world").startsWith("{capo: ${if ("IV" in line) 4 else 2}}\n"), line)
        }
        assertTrue(convert("Cejilla: 3\nAm     C\nHello world").startsWith("{capo: 3}\n"))
        assertTrue(convert("Tempo: 120bpm\nAm     C\nHello world").startsWith("{tempo: 120}\n"))
        assertTrue(convert("BPM: 96\nAm     C\nHello world").startsWith("{tempo: 96}\n"))
    }

    @Test
    fun `key lines of the European chord sites become the header`() {
        for ((line, key) in listOf("Tom: G" to "G", "Tono: Sol" to "Sol", "Tonart: H" to "H", "Key of G" to "G", "Tonalit\u00e9: Sol" to "Sol")) {
            val result = convert("$line\nAm     C\nHello world")
            assertTrue(result.startsWith("{key: $key}\n"), result)
        }
    }

    @Test
    fun `a metadata line whose value cannot be read stays a line of text`() {
        for (line in listOf("Capo: see below", "Capo: I don't use one")) {
            val result = convert("Verse 1\n$line\nAm     C\nHello world")
            assertTrue(result.startsWith("{start_of_verse: Verse 1}\n$line\n"), result)
            assertFalse("{capo" in result, result)
        }
        assertEquals("[Am]Hello [C]world\n", convert("No capo\nAm     C\nHello world"))
    }

    @Test
    fun `a lyric that starts with a key word is not metadata`() {
        for (line in listOf("Key of my heart", "Tono y fuerza")) {
            assertEquals("{start_of_verse: Verse 1}\n$line\n[Am]Hello [C]world\n{end_of_verse}\n", convert("Verse 1\n$line\nAm     C\nHello world"))
        }
    }

    @Test
    fun `a chord row may end in a parenthesized playing note`() {
        assertEquals("[G]I walked alone [D]until the dawn (let ring)\n", convert("G              D               (let ring)\nI walked alone until the dawn"))
        assertEquals("[G]Hello [D]world\n[Am]Hold on (hold)\n", convert("G     D\nHello world\nAm      (hold)\nHold on"))
        assertEquals("[G]One [D]two [Em]three [C]four (palm mute)\n", convert("G   D   Em    C     (palm mute)\nOne two three four"))
        assertEquals("{comment: Outro}\n[Am] [*hold]\n", convert("[Outro]\nAm (hold)"))
        assertTrue(convert("Verse 1\nC     G\nHello world\n\nOutro\nG (fade out)").endsWith("{comment: Outro}\n[G] [*fade out]\n"))
    }

    @Test
    fun `the playing note of a chord row over nothing is an annotation on that row`() {
        assertEquals("[G] [D] [*let ring]\n", convert("G   D   (let ring)"))
        assertEquals("[G] [D] [*let (ring)]\n", convert("G   D   (let [ring])"))
        val line = (ChordProParser.parse(convert("G   D   (let ring)")).blocks.single() as ChordProBlock.Section).lines.single() as ChordProLine.Lyrics
        assertTrue(line.text.isBlank())
    }

    @Test
    fun `a starred chord is the chord`() {
        val result = convert("Em             C*\nThe river runs beside the road\n\n*C played with open G")
        assertTrue(result.startsWith("[Em]The river runs [C]beside the road\n"), result)
        assertTrue(result.endsWith("*C played with open G\n"), result)
    }

    @Test
    fun `trailing dots are furniture`() {
        assertEquals("[G]Hello [D]world\n", convert("G     D   ...\nHello world"))
        assertEquals("[G] [D] ...\n", convert("G  D ..."))
    }

    @Test
    fun `isChordLine stays strict about stars and notes`() {
        assertFalse(ChordSheetConverter.isChordLine("Em C*"))
        assertFalse(ChordSheetConverter.isChordLine("G D (let ring)"))
    }

    @Test
    fun `a parenthesized note does not make the sheet one of parenthesized chords`() {
        val result = convert("G              D               (let ring)\nI walked alone until the dawn\nand then (Am) I sang\nwith (my) friends (all) night")
        assertTrue("and then (Am) I sang" in result, result)
    }

    @Test
    fun `an unaligned chord row is written above a much longer lyric`() {
        assertEquals("[G] [D] [Em] [C]\nThe river runs beside the old mill road\n", convert("G D Em C\nThe river runs beside the old mill road"))
        assertEquals("[Am]Hel[C]lo world\n", convert("Am C\nHello world"))
    }

    @Test
    fun `a measured chord row stays merged over a long lyric`() {
        val chords = ChordSheet.Line(listOf(span("G", 0.0, 6.0), span(" ", 6.0, 9.0), span("C", 60.0, 66.0), span(" ", 66.0, 69.0), span("G", 150.0, 156.0)))
        val lyric = ChordSheet.Line(listOf(span("Amazing grace how sweet the sound", 0.0, 198.0)))
        val result = ChordSheetConverter.convert(ChordSheet(listOf(ChordSheet.Page(listOf(chords, lyric))))).single()
        assertTrue(result.startsWith("[G]Amazing"), result)
        assertEquals(3, Regex("\\[[GC]]").findAll(result).count(), result)
        assertFalse("[G] [C] [G]" in result, result)
    }

    @Test
    fun `a paste with a blank line after every line is read as single-spaced`() {
        val result = convert("G     D\n\nHello world\n\nEm   C\n\nSing along\n\n\nG      D\n\nSecond verse")
        assertEquals("[G]Hello [D]world\n[Em]Sing [C]along\n\n[G]Second [D]verse\n", result)
    }

    @Test
    fun `a lettered list in prose is not a chord row`() {
        assertEquals("Options:\nA B C D\nPick one of them.\n", convert("Options:\nA B C D\nPick one of them."))
        assertTrue("[A] [B] [C] [D]\nPick one of them." in convert("Am     F\nHello world\nA B C D\nPick one of them."))
    }

    @Test
    fun `the lines a lyrics site injects into a copy are left out`() {
        val result = convert("[Verse 1]\nThe river runs\nbeside the road\n\nYou might also like\n\nall the way home\n\n[Chorus]\nSing it loud\nsing it long\n12Embed")
        assertFalse("You might also like" in result, result)
        assertFalse("Embed" in result, result)
        assertTrue(result.endsWith("{start_of_chorus: Chorus}\nSing it loud\nsing it long\n{end_of_chorus}\n"), result)
        assertFalse("\n\n\n" in result, result)
    }

    @Test
    fun `a poem that ends in Embed is left alone`() {
        assertEquals("The words we keep\nthe lines we write\nthe things we hold\nEmbed\n", convert("The words we keep\nthe lines we write\nthe things we hold\nEmbed"))
    }

    @Test
    fun `a share count glued to the last lyric is cut off`() {
        assertTrue(convert("[Chorus]\nSing it to the end12Embed").contains("Sing it to the end\n{end_of_chorus}"))
        assertTrue(convert("[Chorus]\nSing it to the end.Embed").contains("Sing it to the end.\n{end_of_chorus}"))
        assertTrue(convert("[Chorus]\nThis is what we Embed").contains("This is what we Embed\n{end_of_chorus}"))
    }

    @Test
    fun `a fragment's chord rows are set into the lyrics under them`() {
        assertEquals("[Am]Hello [C]world\nsecond line", ChordSheetConverter.convertFragment("Am     C\nHello world\nsecond line"))
    }

    @Test
    fun `a fragment's chord row with no lyrics under it is bracketed on its own line`() {
        assertEquals("[G] [D] [Em]\n\n[Am]Hello [C]world\n", ChordSheetConverter.convertFragment("G  D  Em\n\nAm     C\nHello world\n"))
    }

    @Test
    fun `a fragment's headings become sections and its first line is no title`() {
        assertEquals(
            "My Song Title\n{start_of_verse: Verse 1}\n[Am]Hello [C]world\n{end_of_verse}",
            ChordSheetConverter.convertFragment("My Song Title\nVerse 1:\nAm     C\nHello world"),
        )
        assertEquals(
            "{start_of_chorus: Chorus}\n[C] [G] [D]\nSing it loud\n{end_of_chorus}",
            ChordSheetConverter.convertFragment("Chorus: C G D\nSing it loud"),
        )
    }

    @Test
    fun `a fragment's closing line break comes after the section it opened`() {
        assertEquals(
            "{start_of_chorus: Chorus}\n[G]Sing it [D]loud\n{end_of_chorus}\n",
            ChordSheetConverter.convertFragment("Chorus:\nG       D\nSing it loud\n"),
        )
    }

    @Test
    fun `a fragment's metadata lines stay lines of text`() {
        assertEquals("Capo 2\n[Am]Hello [C]world", ChordSheetConverter.convertFragment("Capo 2\nAm     C\nHello world"))
    }

    @Test
    fun `a fragment's prose cannot become markup`() {
        assertEquals("[G]Sing [C]along\n(a note) {stays}", ChordSheetConverter.convertFragment("G    C\nSing along\n[a note] {stays}"))
    }

    @Test
    fun `a fragment that is ChordPro already or has no chord row is not converted`() {
        assertEquals(null, ChordSheetConverter.convertFragment("[Am]Hello [C]world\nsecond line"))
        assertEquals(null, ChordSheetConverter.convertFragment("{start_of_verse}\nHello\n{end_of_verse}"))
        assertEquals(null, ChordSheetConverter.convertFragment("A long time ago\nin a galaxy far away"))
    }

    @Test
    fun `a fragment of lyrics with bracketed headings is converted though it has no chords`() {
        assertEquals(
            "{start_of_verse: Verse}\nAragorn and Boromir\nWhy so afraid\n\n{end_of_verse}\n{start_of_chorus: Chorus}\nAragorn and Boromir\n" +
                "{end_of_chorus}\n{start_of_verse: Verse 2}\nYour friends will wait\n{end_of_verse}",
            ChordSheetConverter.convertFragment("[Verse]\nAragorn and Boromir\nWhy so afraid\n\n[Chorus]\nAragorn and Boromir\n[Verse 2]\nYour friends will wait"),
        )
        assertEquals(null, ChordSheetConverter.convertFragment("Verse:\nA long time ago\nin a galaxy far away"))
    }

    @Test
    fun `a fragment in Latin or German notation keeps its chords as written`() {
        assertEquals("[Lam]1 4 [Re]5 [Mi]", ChordSheetConverter.convertFragment("Lam Re Mi\n1 4 5"))
        assertEquals("[am]Hello [H7]world", ChordSheetConverter.convertFragment("am     H7\nHello world"))
    }

    @Test
    fun `a fragment's tabs and wide chord rows are placed by their columns`() {
        assertEquals("[Am]Hello   world [C]", ChordSheetConverter.convertFragment("Am\t\t\tC\nHello\tworld"))
        assertEquals("[Am]Hello [C]", ChordSheetConverter.convertFragment("Am            C\nHello"))
    }

    @Test
    fun `section labels printed in a margin column open the sections they label`() {
        val sheet = listOf(
            "        G            C",
            "V1      Amazing grace how sweet",
            "        D              G",
            "        the sound that saved",
            "        C            G",
            "Ch1     Oh happy day when Jesus",
            "        D              G",
            "        washed my sins away",
            "A2      Swing low sweet chariot",
        ).joinToString("\n")
        assertEquals(
            "{start_of_verse: V1}\n[G]Amazing grace [C]how sweet\n[D]the sound that [G]saved\n{end_of_verse}\n" +
                "{start_of_chorus: Ch1}\n[C]Oh happy day [G]when Jesus\n[D]washed my sins [G]away\n{end_of_chorus}\n" +
                "{start_of_verse: A2}\nSwing low sweet chariot\n{end_of_verse}\n",
            convert(sheet),
        )
    }

    @Test
    fun `a chord row reaching into the margin and a lone word left of the body stay where they are`() {
        assertEquals("[G]Amazing [C]grace\n[D]how [G]sweet\n", convert("G         C\n   Amazing grace\nD      G\n   how sweet"))
        assertEquals("Oh     happy day\nwhen Jesus washed\n", convert("Oh     happy day\n       when Jesus washed"))
    }

    @Test
    fun `a note printed before or between a row's chords is an annotation where it stands`() {
        assertEquals("[*x]Now my [C]heart [G]cries out\n", convert("(x)    C     G\nNow my heart cries out"))
        assertEquals("[G]Because [*to v:]of the Lord [D]Jesus\n", convert("G       (to v:)     D\nBecause of the Lord Jesus"))
        assertEquals("[*x] [C] [G]\n", convert("(x)  C   G"))
    }

    @Test
    fun `dashes filling a chord's width at the end of a lyric are left out, sung ones kept`() {
        assertEquals("[Am]tonight [G]\n", convert("Am       G\ntonight-----"))
        assertEquals("[Am]Oh [C]tonight-----\n", convert("Am C\nOh tonight-----"))
        assertEquals("[Am]Ah - - [G]-\n", convert("Am     G\nAh - - -"))
    }

    @Test
    fun `a copyright line ends up in the header wherever the page prints it`() {
        val body = (1..10).joinToString("\n") { "Am      G\nLine $it of the song" }
        val result = convert("Song\n\n$body\n\u00a9 2024 Someone")
        assertTrue(result.startsWith("{title: Song}\n{copyright: 2024 Someone}\n"), result)
        assertFalse("\u00a9" in result, result)
    }

    @Test
    fun `the part printed beside the title and the line centred under it are the subtitle`() {
        fun line(size: Double, vararg parts: Pair<String, Double>, bold: Boolean = false) = ChordSheet.Line(
            parts.flatMap { (text, x) -> text.mapIndexed { index, c -> span(c.toString(), x + index * size * 0.6, x + (index + 1) * size * 0.6, size, bold) } },
        )
        fun convert(vararg lines: ChordSheet.Line) = ChordSheetConverter.convert(ChordSheet(listOf(ChordSheet.Page(lines.toList())))).single()
        val beside = convert(
            line(18.0, "Because of Jesus" to 235.0, "B29" to 542.0, bold = true),
            line(12.0, "G" to 61.0, "D" to 140.0),
            line(12.0, "You made a covenant with me" to 61.0),
        )
        assertTrue(beside.startsWith("{title: Because of Jesus}\n{subtitle: B29}\n\n[G]You made a"), beside)
        val centred = convert(
            line(20.0, "Santa Baby" to 258.0, bold = true),
            line(12.0, "Philip Springer, lyrics by Joan Javits" to 181.0),
            line(12.0, "Db6" to 64.0, "Fm7" to 136.0),
            line(12.0, "Santa baby, just slip a sable" to 64.0),
        )
        assertTrue(centred.startsWith("{title: Santa Baby}\n{subtitle: Philip Springer, lyrics by Joan Javits}\n\n[Db6]Santa"), centred)
        val flush = convert(
            line(20.0, "Santa Baby" to 64.0, bold = true),
            line(12.0, "Philip Springer, lyrics by Joan Javits" to 64.0),
            line(12.0, "Db6" to 64.0, "Fm7" to 136.0),
            line(12.0, "Santa baby, just slip a sable" to 64.0),
        )
        assertFalse("{subtitle" in flush, flush)
    }
}
