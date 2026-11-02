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
import kotlin.test.assertTrue

class ChordSheetChordPlacementTest {

    @Test
    fun `a chord set a rounding before a glyph's start is sung on that glyph, after a hyphen too`() {
        val lyric = "Dwelt a miner, forty-niner, and his daughter, Clementine."
        val lyricLine = glyphs(lyric)
        val chordLine = ChordSheet.Line(
            listOf(
                chord("C", lyricLine, lyric.indexOf("niner")),
                chord("G7", lyricLine, lyric.indexOf("daughter")),
                chord("C", lyricLine, lyric.indexOf("tine")),
            ),
        )
        val result = ChordSheetConverter.convert(ChordSheet(listOf(ChordSheet.Page(listOf(chordLine, lyricLine)))), guessesTitle = false).single()

        assertTrue("forty-[C]niner, and his [G7]daughter, Clemen[C]tine." in result, result)
    }

    @Test
    fun `chords held with dashes are a chord row, the dashes no part of it`() {
        val sheet = "C-------C-------\n\nG       Am--Am7  C--------\nGive me one more chance now"
        val result = ChordSheetConverter.convert(ChordSheet.ofPlainText(sheet), guessesTitle = false).single()

        assertTrue(result.startsWith("[C] [C]\n\n[G]Give me [Am]one [Am7]more [C]chance now"), result)
    }

    /** [text] drawn glyph by glyph in a proportional type, whose glyphs' edges a layout rounds. */
    private fun glyphs(text: String): ChordSheet.Line {
        var x = 40.0
        return ChordSheet.Line(
            text.map { c ->
                val width = when (c) {
                    'i', 'l', '.', ',', ' ' -> 3.37
                    'm', 'w' -> 10.51
                    '-' -> 5.52
                    else -> 7.04
                }
                ChordSheet.Span(c.toString(), x, x + width, SIZE).also { x += width }
            },
        )
    }

    /** A bold chord over the glyph [index] of [lyric], set where a layout measuring the text on its own puts it: a rounding short. */
    private fun chord(name: String, lyric: ChordSheet.Line, index: Int): ChordSheet.Span {
        val start = lyric.spans[index].start - 0.01
        return ChordSheet.Span(name, start, start + name.length * 8.0, SIZE, isBold = true)
    }

    private companion object {
        const val SIZE = 14.52
    }
}
