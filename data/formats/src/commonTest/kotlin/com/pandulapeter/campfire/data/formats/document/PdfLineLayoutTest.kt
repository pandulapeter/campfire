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

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PdfLineLayoutTest {

    @Test
    fun `two columns are read one after the other`() = runTest {
        val left = (1..6).map { Placed(50, 700 - it * 12, "Left $it") }
        val right = (1..6).map { Placed(320, 700 - it * 12, "Right $it") }

        assertEquals((1..6).map { "Left $it" } + (1..6).map { "Right $it" }, lines(left + right))
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
    fun `chord rows with wide gaps over lyrics spanning the page are one column`() = runTest {
        val rows = (0 until 3).flatMap { verse ->
            val y = 700 - verse * 24
            listOf(Placed(50, y, "Am"), Placed(330, y, "G"), Placed(50, y - 12, "l".repeat(58)))
        }

        assertEquals(List(3) { listOf("Am G", "l".repeat(58)) }.flatten(), lines(rows).map { it.replace(Regex(" +"), " ") })
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

    /**
     * A string shown in Courier, 0.6 of its [size] a glyph, with its baseline starting at [x], [y]; one [isWhole] is
     * carried by an `ActualText`, which makes it one span, as Campfire's export writes a right-to-left run.
     */
    private data class Placed(val x: Int, val y: Int, val text: String, val size: Int = 10, val isWhole: Boolean = false)

    private suspend fun lines(placed: List<Placed>): List<String> {
        val content = placed.joinToString(" ") {
            val shown = "BT /F1 ${it.size} Tf 1 0 0 1 ${it.x} ${it.y} Tm (${it.text}) Tj ET"
            if (it.isWhole) "/Span << /ActualText (${it.text}) >> BDC $shown EMC" else shown
        }
        return PdfTextExtractor.extract(PdfTestWriter.song(content)).pages.single().lines
            .map { line -> line.spans.joinToString("") { it.text } }
            .filter { it.isNotEmpty() }
    }
}
