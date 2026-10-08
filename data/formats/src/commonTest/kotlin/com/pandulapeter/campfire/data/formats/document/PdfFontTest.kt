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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class PdfFontTest {
    @Test
    fun `Type 3 widths use the font matrix`() {
        val file = PdfFile(PdfTestWriter.song("BT /F1 10 Tf 50 700 Td (text) Tj ET"))
        fun font(matrix: String) = PdfFont(
            file,
            PdfSyntax("<< /Subtype /Type3 /FontMatrix [$matrix 0 0 $matrix 0 0] /FirstChar 65 /Widths [100] /Encoding << /Differences [65 /A] >> >>".encodeToByteArray()).next() as PdfDictionary,
        )
        assertEquals(1000.0, font("0.01").decode(byteArrayOf(65)).first().width, 1e-9)
        assertEquals(100.0, font("0.001").decode(byteArrayOf(65)).first().width)
        assertEquals(500.0, font("0.01").decode(byteArrayOf(66)).first().width)
    }

    @Test
    fun `overlapping CID width ranges are rejected quickly`() {
        val file = PdfFile(PdfTestWriter.song("BT /F1 10 Tf 50 700 Td (text) Tj ET"))
        val dictionary = PdfSyntax(
            "<< /Subtype /Type0 /DescendantFonts [<< /Subtype /CIDFontType2 /W [${"0 65535 500 ".repeat(33_000)}] >>] >>".encodeToByteArray(),
        ).next() as PdfDictionary
        val elapsed = measureTime { assertFailsWith<IllegalArgumentException> { PdfFont(file, dictionary) } }
        assertTrue(elapsed < 2.seconds, "Took $elapsed")
    }

    @Test
    fun `CID widths read explicit lists and ranges`() {
        val file = PdfFile(PdfTestWriter.song("BT /F1 10 Tf 50 700 Td (text) Tj ET"))
        val font = PdfFont(
            file,
            PdfSyntax("<< /Subtype /Type0 /DescendantFonts [<< /Subtype /CIDFontType2 /W [0 [500 600] 10 20 700] >>] >>".encodeToByteArray()).next() as PdfDictionary,
        )
        assertEquals(listOf(500.0, 600.0, 700.0), font.decode(byteArrayOf(0, 0, 0, 1, 0, 15)).map { it.width }.toList())
    }
}
