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

    /** A string shown in 10-point Courier, 6 points a glyph, with its baseline starting at [x], [y]. */
    private data class Placed(val x: Int, val y: Int, val text: String)

    private suspend fun lines(placed: List<Placed>): List<String> {
        val content = "BT /F1 10 Tf " + placed.joinToString(" ") { "1 0 0 1 ${it.x} ${it.y} Tm (${it.text}) Tj" } + " ET"
        return PdfTextExtractor.extract(PdfTestWriter.song(content)).pages.single().lines
            .map { line -> line.spans.joinToString("") { it.text } }
            .filter { it.isNotEmpty() }
    }
}
