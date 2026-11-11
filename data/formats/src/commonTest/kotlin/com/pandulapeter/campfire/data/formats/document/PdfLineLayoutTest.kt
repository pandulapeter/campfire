/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.formats.document

import com.pandulapeter.campfire.chordpro.convert.ChordSheet
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class PdfLineLayoutTest {

    @Test
    fun `a superscript belongs to the word it follows, never a line of its own`() = runTest {
        val content = "BT /F1 20 Tf 1 0 0 1 50 700 Tm ([Capo 2) Tj /F1 13 Tf 1 0 0 1 134 707 Tm (nd) Tj " +
            "/F1 20 Tf 1 0 0 1 149.6 700 Tm (]) Tj 1 0 0 1 50 670 Tm (H) Tj /F1 13 Tf 1 0 0 1 62 666 Tm (2) Tj " +
            "/F1 20 Tf 1 0 0 1 69.8 670 Tm (O) Tj 1 0 0 1 50 640 Tm (Lyric line) Tj ET"
        val lines = PdfTextExtractor.extract(PdfTestWriter.song(content)).pages.single().lines

        assertEquals(listOf("[Capo 2nd]", "H2O", "Lyric line"), lines.map { line -> line.spans.joinToString("") { it.text } })
    }

    @Test
    fun `thousands of tiny runs beside a few lines take time in proportion to them`() {
        assertTakesTimeInProportion(quarterSize = 2_500) { size ->
            val glyphs = (0 until 5).flatMap { line ->
                val y = line * 2_000.0
                listOf(PdfLineLayout.Positioned(ChordSheet.Span("H", 0.0, 600.0, 1_000.0), y)) +
                    List(size) { PdfLineLayout.Positioned(ChordSheet.Span("r", 600.0 + it * 0.06, 600.06 + it * 0.06, 0.1), y + 1.0 + it * 0.023) }
            }
            PdfLineLayout.rows(glyphs) {}
        }
    }

    @Test
    fun `two columns are read one after the other`() = runTest {
        val left = (1..6).map { Placed(50, 700 - it * 12, "Left $it") }
        val right = (1..6).map { Placed(320, 700 - it * 12, "Right $it") }

        assertEquals((1..6).map { "Left $it" } + (1..6).map { "Right $it" }, lines(left + right))
    }

    @Test
    fun `each line names its column, and a title over both columns names none`() = runTest {
        val title = Placed(50, 740, "T".repeat(55))
        val left = (1..6).map { Placed(50, 700 - it * 12, "Left $it") }
        val right = (1..6).map { Placed(320, 700 - it * 12, "Right $it") }
        val lines = PdfTextExtractor.extract(PdfTestWriter.song(content(listOf(title) + left + right))).pages.single().lines
            .filter { line -> line.spans.any { it.text.isNotBlank() } }

        assertEquals(listOf<Int?>(null) + List(6) { 0 } + List(6) { 1 }, lines.map { it.column })
    }

    @Test
    fun `a right column shorter than the left one is still read after it`() = runTest {
        val left = (1..6).map { Placed(50, 700 - it * 12, "Left $it") }
        val right = (1..2).map { Placed(320, 700 - it * 12, "Right $it") }

        assertEquals((1..6).map { "Left $it" } + (1..2).map { "Right $it" }, lines(left + right))
    }

    @Test
    fun `a title over both columns is read before them`() = runTest {
        val title = Placed(50, 740, "T".repeat(55))
        val left = (1..6).map { Placed(50, 700 - it * 12, "Left $it") }
        val right = (1..6).map { Placed(320, 700 - it * 12, "Right $it") }

        assertEquals(listOf(title.text) + left.map { it.text } + right.map { it.text }, lines(listOf(title) + left + right))
    }

    @Test
    fun `a header line over the gutter does not move the gutter into a column's long chord row`() = runTest {
        val header = listOf(Placed(50, 760, "Song"), Placed(280, 740, "Key: C"))
        val chords = listOf(50 to "C", 110 to "G", 170 to "C/B", 230 to "Am", 270 to "G").map { (x, chord) -> Placed(x, 640, chord) }
        val left = (1..8).filter { it != 5 }.map { Placed(50, 700 - it * 12, "Left lyric line number $it of the song") }
        val right = (1..8).map { Placed(320, 700 - it * 12, "Right column line $it") }

        assertEquals(
            listOf("Song", "Key: C") + left.take(4).map { it.text } + "C G C/B Am G" + left.drop(4).map { it.text } + right.map { it.text },
            lines(header + chords + left + right).map { it.replace(Regex(" +"), " ") },
        )
    }

    @Test
    fun `a title and credit centred over the gutter but clear of the left column are read before both columns`() = runTest {
        val header = listOf(Placed(300, 760, "Title of the song"), Placed(320, 745, "By Someone"))
        val left = (1..6).map { Placed(50, 700 - it * 12, "Left lyric $it") }
        val right = (1..6).map { Placed(420, 700 - it * 12, "Right lyric $it") }

        assertEquals((header + left + right).map { it.text }, lines(header + left + right))
    }

    @Test
    fun `chord rows with wide gaps over lyrics spanning the page are one column`() = runTest {
        val rows = (0 until 3).flatMap { verse ->
            val y = 700 - verse * 24
            listOf(Placed(50, y, "Am"), Placed(330, y, "G"), Placed(50, y - 12, "l".repeat(58)))
        }

        assertEquals(List(3) { listOf("Am G", "l".repeat(58)) }.flatten(), lines(rows).map { it.replace(Regex(" +"), " ") })
    }

    @Test
    fun `a chord row with a wide gap is one line when only the lines crossing that gap have text right of it`() = runTest {
        val placed = listOf(
            Placed(40, 740, "Song"),
            Placed(40, 724, "Key: F#m"),
            Placed(40, 708, "Capo: 2, chords as they"),
            Placed(40, 694, "sound"),
            Placed(40, 670, "Verse"),
            Placed(40, 654, "F#m Bm"),
            Placed(120, 654, "F#m E"),
            Placed(40, 640, "Hello world and more"),
            Placed(40, 624, "F#m"),
            Placed(40, 610, "here"),
            Placed(290, 40, "1 / 1"),
        )

        assertEquals(
            listOf("Song", "Key: F#m", "Capo: 2, chords as they", "sound", "Verse", "F#m Bm F#m E", "Hello world and more", "F#m", "here"),
            lines(placed).map { it.replace(Regex(" +"), " ") }.filter { it != "1 / 1" },
        )
    }

    @Test
    fun `a heading wider than the first column's lyrics is read before a short second column that starts beside it`() = runTest {
        val heading = listOf(
            Placed(40, 740, "Everything At Once", size = 22),
            Placed(40, 720, "The Testers", size = 13),
            Placed(40, 704, "Key: Bb  Capo: 3  Tempo: 96  Time: 6/8", size = 13),
        )
        val left = (1..10).map { Placed(40, 680 - it * 24, "Lyric line $it", size = 13) }
        val right = listOf(745, 728, 700, 686, 672).zip(listOf("Solo", "Am F C G", "Outro", "C G C", "Fade out")) { y, text -> Placed(430, y, text, size = 13) }
        val pageNumber = Placed(300, 40, "1 / 1")

        assertEquals((heading + left + right).map { it.text }, lines(heading + left + right + pageNumber).filter { it != "1 / 1" })
    }

    @Test
    fun `a chord far left of a right-aligned page whose lines are each one span is not read as a column`() = runTest {
        val placed = listOf(
            Placed(500, 740, "Title line", isWhole = true),
            Placed(518, 722, "Key: Am"),
            Placed(530, 700, "Verse"),
            Placed(400, 684, "G"),
            Placed(548, 684, "Em"),
            Placed(396, 670, "A lyric that is long enough", isWhole = true),
            Placed(530, 654, "C"),
            Placed(554, 654, "G"),
            Placed(464, 640, "A shorter lyric line", isWhole = true),
            Placed(548, 624, "E7"),
            Placed(482, 610, "The last lyric", isWhole = true),
            Placed(290, 40, "1 / 1"),
        )

        assertEquals(listOf("Title line", "Key: Am", "Verse", "G Em", "A lyric that is long enough", "C G", "A shorter lyric line", "E7", "The last lyric"), lines(placed).map { it.replace(Regex(" +"), " ") }.filter { it != "1 / 1" })
    }

    @Test
    fun `a page number centred across the gutter does not join a lyric ending at the column's edge to the line beside it`() = runTest {
        val left = (1..8).map { Placed(40, 700 - it * 24, if (it == 4) "L".repeat(35) else "l$it".padEnd(30, 'l')) }
        val right = (1..8).map { Placed(290, 700 - it * 24, "r$it".padEnd(30, 'r')) }
        val footer = Placed(254, 40, "Page 2 / 3", size = 6)

        assertEquals((left + right).map { it.text }, lines(left + right + footer).filter { it != "Page 2 / 3" })
    }

    @Test
    fun `a title over a short second column does not hide that column`() = runTest {
        val title = Placed(40, 740, "T".repeat(43))
        val left = (1..25).map { Placed(40, 720 - it * 12, "l$it".padEnd(12, 'l')) }
        val right = (1..5).map { Placed(200, 720 - it * 12, "r$it".padEnd(17, 'r')) }

        assertEquals((listOf(title) + left + right).map { it.text }, lines(listOf(title) + left + right))
    }

    @Test
    fun `a page count above the bottom margin band is still left out`() = runTest {
        val placed = (1..6).map { Placed(50, 700 - it * 14, "Lyric line $it") } + Placed(290, 80, "1 / 1", size = 9)

        assertEquals((1..6).map { "Lyric line $it" }, lines(placed))
    }

    @Test
    fun `a time signature as the lowest line above the bottom margin band stays`() = runTest {
        val placed = (1..6).map { Placed(50, 700 - it * 14, "Lyric line $it") } + Placed(290, 80, "3/4")

        assertEquals((1..6).map { "Lyric line $it" } + "3/4", lines(placed))
    }

    @Test
    fun `chord diagrams over the song are left out while a chord row in small type stays`() = runTest {
        val names = listOf("C", "Am", "F", "G7").mapIndexed { index, name -> Placed(60 + index * 120, 740, name, size = 8) }
        val frets = listOf("x 3 2 0 1 0", "x 0 2 2 1 0", "1 3 3 2 1 1", "3 2 0 0 0 1").mapIndexed { index, frets ->
            Placed(50 + index * 120, 700, frets, size = 6)
        }
        val song = listOf(
            Placed(50, 660, "Verse", size = 11),
            Placed(50, 646, "C        Am", size = 9),
            Placed(50, 632, "Lyric line one", size = 12),
            Placed(50, 618, "F        G7", size = 9),
            Placed(50, 604, "Lyric line two", size = 12),
        )

        assertEquals(song.map { it.text.replace(Regex(" +"), " ") }, lines(names + frets + song).map { it.replace(Regex(" +"), " ").trim() })
    }

    @Test
    fun `a small chord-only outro over a page number is kept`() = runTest {
        assertContains(lines(outroOver(Placed(290, 60, "3", size = 8))).map { it.replace(Regex(" +"), " ").trim() }, "G C D G")
    }

    @Test
    fun `a small chord-only outro over a page number between dashes is kept`() = runTest {
        assertContains(lines(outroOver(Placed(280, 60, "- 3 -", size = 8))).map { it.replace(Regex(" +"), " ").trim() }, "G C D G")
    }

    @Test
    fun `a small chord row over a small count row is kept`() = runTest {
        val placed = listOf(
            Placed(50, 660, "G   C   D   G", size = 9),
            Placed(50, 648, "1 & 2 & 3 & 4 &", size = 9),
            Placed(50, 632, "Lyric line one", size = 12),
            Placed(50, 618, "Lyric line two", size = 12),
        )

        assertContains(lines(placed).map { it.replace(Regex(" +"), " ").trim() }, "G C D G")
    }

    /** A song in 12 pt ending in a 9 pt chord-only row, with [footer] in 8 pt far below it. */
    private fun outroOver(footer: Placed) = listOf(
        Placed(50, 700, "Lyric line one", size = 12),
        Placed(50, 686, "Lyric line two", size = 12),
        Placed(50, 672, "Lyric line three", size = 12),
        Placed(50, 652, "G   C   D   G", size = 9),
        footer,
    )

    /**
     * A string shown in Courier, 0.6 of its [size] a glyph, with its baseline starting at [x], [y]; one [isWhole] is
     * carried by an `ActualText`, which makes it one span, as Campfire's export writes a right-to-left run.
     */
    private data class Placed(val x: Int, val y: Int, val text: String, val size: Int = 10, val isWhole: Boolean = false)

    private fun content(placed: List<Placed>) = placed.joinToString(" ") {
        val shown = "BT /F1 ${it.size} Tf 1 0 0 1 ${it.x} ${it.y} Tm (${it.text}) Tj ET"
        if (it.isWhole) "/Span << /ActualText (${it.text}) >> BDC $shown EMC" else shown
    }

    private suspend fun lines(placed: List<Placed>): List<String> = PdfTextExtractor.extract(PdfTestWriter.song(content(placed))).pages.single().lines
        .map { line -> line.spans.joinToString("") { it.text } }
        .filter { it.isNotEmpty() }
}
