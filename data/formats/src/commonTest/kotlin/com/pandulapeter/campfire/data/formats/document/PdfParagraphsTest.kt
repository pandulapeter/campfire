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

class PdfParagraphsTest {
    private suspend fun read(lines: List<Pair<Double, String>>): List<String> {
        var y = 760.0
        val content = lines.joinToString(" ", prefix = "BT /F1 10 Tf ", postfix = " ET") { (step, text) ->
            y -= step
            "1 0 0 1 50 $y Tm ($text) Tj"
        }
        return PdfTextExtractor.extract(PdfTestWriter.song(content)).pages.single().lines.map { line -> line.spans.joinToString("") { it.text } }
    }

    @Test
    fun `a stanza break set tighter than two lines is read from the document's own leading`() = runTest {
        val stanza = listOf(18.0 to "First line", 12.0 to "Second line", 12.0 to "Third line", 12.0 to "Fourth line")
        assertEquals(
            listOf("First line", "Second line", "Third line", "Fourth line", "", "First line", "Second line", "Third line", "Fourth line", "", "First line", "Second line", "Third line", "Fourth line"),
            read(stanza + stanza + stanza),
        )
    }

    @Test
    fun `the longer step from a lyric to the next chord row is no stanza break, a gap beyond it is`() = runTest {
        val pair = listOf(13.0 to "G C", 11.0 to "Lyric line")
        val stanza = listOf(19.0 to "G C", 11.0 to "Lyric line") + pair + pair
        assertEquals(
            listOf("G C", "Lyric line", "G C", "Lyric line", "G C", "Lyric line", "", "G C", "Lyric line", "G C", "Lyric line", "G C", "Lyric line"),
            read(stanza + stanza),
        )
    }

    @Test
    fun `a line set apart on both sides by a gap is no stanza of its own`() = runTest {
        val stanza = listOf(18.0 to "First line", 12.0 to "Second line", 12.0 to "Third line", 12.0 to "Fourth line")
        val lyrics = listOf("First line", "Second line", "Third line", "Fourth line")
        assertEquals(
            listOf("Title", "Words by someone", "Music by someone else") + lyrics + "" + lyrics + "" + lyrics,
            read(listOf(0.0 to "Title", 18.0 to "Words by someone", 18.0 to "Music by someone else") + stanza + stanza + stanza),
        )
    }
}
