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
import com.pandulapeter.campfire.data.formats.document.PdfLineLayout.Positioned
import kotlin.test.Test
import kotlin.test.assertEquals

class PdfChordDiagramsTest {

    @Test
    fun `a row of one long word takes time in proportion to its length`() {
        assertTakesTimeInProportion(
            wholeSize = 160_000,
            inputOf = { size -> List(size) { Positioned(ChordSheet.Span("a", it * 6.0, it * 6.0 + 6.0, 10.0), 0.0) } },
        ) { word ->
            assertEquals(word.size, PdfChordDiagrams.withoutDiagrams(word).size)
        }
    }

    @Test
    fun `a column of diagrams beside the song is left out of its rows`() {
        val song = listOf(
            text("C", x = 60.0, y = 80.0, size = 11.0),
            text("Oh Danny boy", x = 40.0, y = 94.0, size = 11.0),
            text("F", x = 60.0, y = 160.0, size = 11.0),
            text("From glen to glen", x = 40.0, y = 174.0, size = 11.0),
        )
        val diagrams = listOf(
            text("C", x = 530.0, y = 80.0, size = 11.0),
            text("1", x = 545.0, y = 105.0, size = 6.0),
            text("2", x = 529.0, y = 113.0, size = 6.0),
            text("3", x = 521.0, y = 121.0, size = 6.0),
            text("F", x = 530.0, y = 158.0, size = 11.0),
            text("1", x = 513.0, y = 180.0, size = 6.0),
            text("1", x = 553.0, y = 180.0, size = 6.0),
        )
        assertEquals(song.flatten(), PdfChordDiagrams.withoutDiagrams((song + diagrams).flatten()))
    }

    @Test
    fun `a second column of the song beside it stays`() {
        val glyphs = listOf(
            text("C", x = 60.0, y = 80.0, size = 11.0),
            text("Oh Danny boy", x = 40.0, y = 94.0, size = 11.0),
            text("G", x = 330.0, y = 80.0, size = 11.0),
            text("The summer's gone", x = 310.0, y = 94.0, size = 11.0),
            text("Am", x = 330.0, y = 120.0, size = 11.0),
            text("4", x = 330.0, y = 134.0, size = 6.0),
        ).flatten()
        assertEquals(glyphs, PdfChordDiagrams.withoutDiagrams(glyphs))
    }

    /** [value]'s glyphs, each 0.6 of [size] wide, from [x] on the baseline [y]. */
    private fun text(value: String, x: Double, y: Double, size: Double) = value.mapIndexed { index, character ->
        val start = x + index * size * 0.6
        Positioned(ChordSheet.Span(character.toString(), start, start + size * 0.6, size), y)
    }
}
