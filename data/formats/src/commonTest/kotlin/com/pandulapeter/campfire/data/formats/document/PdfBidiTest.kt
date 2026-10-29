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

import com.pandulapeter.campfire.chordpro.language.WritingDirection
import com.pandulapeter.campfire.data.model.importing.ExtractedDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class PdfBidiTest {

    /** [drawn] laid out from [left] a glyph of six points each, as a producer draws a line from the left. */
    private fun glyphs(drawn: String, left: Double = 0.0) =
        drawn.mapIndexed { index, character -> ExtractedDocument.Span(character.toString(), left + index * 6, left + index * 6 + 6, 10.0) }

    private fun column(right: Double, direction: WritingDirection = WritingDirection.RIGHT_TO_LEFT) = PdfBidi.Column(0.0, right, direction)

    @Test
    fun `a Hebrew line drawn from the left reads from the right, each character where its glyph is`() {
        val spans = glyphs("םולש")
        val line = PdfBidi.readingOrder(spans, column(24.0)).single()

        assertEquals("שלום", line.text)
        assertEquals(listOf(18.0, 12.0, 6.0, 0.0), line.pieces.map { it.start })
    }

    @Test
    fun `a Latin word and a number keep their order, and a bracket named by its glyph or by its character pairs up`() {
        assertEquals("יחד (Psalm 133)", PdfBidi.readingOrder(glyphs("(Psalm 133) דחי"), column(90.0)).single().text)
        assertEquals("יחד (Psalm 133)", PdfBidi.readingOrder(glyphs(")Psalm 133( דחי"), column(90.0)).single().text)
    }

    @Test
    fun `a line flush with the left edge of a Hebrew column and short of its right reads from the left`() {
        val line = PdfBidi.readingOrder(glyphs("Hine ma tov (בוט המ)"), column(300.0)).single()

        assertEquals("Hine ma tov (מה טוב)", line.text)
    }

    @Test
    fun `a line flush with the right edge of a Latin column reads from the right whatever its letters`() {
        val line = PdfBidi.readingOrder(glyphs("(Hine ma tov) בוט", left = 200.0), column(302.0, WritingDirection.LEFT_TO_RIGHT)).single()

        assertEquals("טוב (Hine ma tov)", line.text)
    }

    @Test
    fun `points drawn as glyphs of their own stay with the letter they are drawn over`() {
        // "שָל": the qamats is drawn over the shin, starting a little left of it, so that it sorts between the letters.
        val spans = listOf(
            ExtractedDocument.Span("\u05dc", 0.0, 6.0, 10.0),
            ExtractedDocument.Span("\u05b8", 5.5, 7.5, 10.0),
            ExtractedDocument.Span("\u05e9", 6.0, 12.0, 10.0),
        )

        assertEquals("\u05e9\u05b8\u05dc", PdfBidi.readingOrder(spans, column(12.0)).single().text)
    }

    @Test
    fun `a line written in reading order under ActualText, or holding nothing read from the right, is left as it is`() {
        val actual = listOf(ExtractedDocument.Span("שלום", 0.0, 24.0, 10.0, pieces = glyphs("שלום").reversed()))
        val latin = glyphs("Am   G")

        assertSame(actual, PdfBidi.readingOrder(actual, column(24.0)))
        assertSame(latin, PdfBidi.readingOrder(latin, column(36.0)))
    }
}
