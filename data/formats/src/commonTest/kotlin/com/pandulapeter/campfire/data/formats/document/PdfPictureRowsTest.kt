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
import kotlin.test.assertFailsWith

class PdfPictureRowsTest {
    @Test
    fun `a song whose lyrics and chords are drawn as pictures under a header of text is unreadable`() = runTest {
        // A chord picture over each lyric strip, the way a site's export draws a whole song as images.
        val body = (0 until 12).joinToString(" ") { row ->
            val y = 640 - row * 40
            "q 20 0 0 9 60 ${y + 14} cm /Chord Do Q q 400 0 0 10 50 $y cm /Strip Do Q"
        }
        assertEquals(2, PdfTextExtractor.extract(document(header())).pages.single().lines.count { it.spans.any { span -> span.text.isNotBlank() } })
        assertFailsWith<IllegalArgumentException> { PdfTextExtractor.extract(document("${header()} $body")) }
    }

    @Test
    fun `a song of text keeps its logo, its cover, and its rows of chord diagram pictures`() = runTest {
        val lyrics = (0 until 16).joinToString(" ") { row -> "BT /F1 10 Tf 50 ${600 - row * 14} Td (Line $row of the song) Tj ET" }
        val diagrams = (0 until 3).joinToString(" ") { row ->
            (0 until 6).joinToString(" ") { column -> "q 40 0 0 50 ${50 + column * 60} ${300 - row * 70} cm /Chord Do Q" }
        }
        val logo = "q 30 0 0 30 500 740 cm /Chord Do Q"
        val cover = "q 300 0 0 300 150 50 cm /Strip Do Q"
        val bytes = document("${header()} $lyrics $diagrams $logo $cover")
        assertEquals(18, PdfTextExtractor.extract(bytes).pages.single().lines.count { it.spans.any { span -> span.text.isNotBlank() } })
    }

    @Test
    fun `pictures on the baselines of text are no rows of their own`() {
        val pictures = (0 until 12).map { row -> PdfPictureRows.Picture(top = 100.0 + row * 40, bottom = 110.0 + row * 40) }
        assertEquals(0, PdfPictureRows.textlessRows(pictures, baselines = pictures.map { it.bottom }, height = 792.0))
        assertEquals(12, PdfPictureRows.textlessRows(pictures, baselines = emptyList(), height = 792.0))
    }

    private fun header() = "BT /F1 16 Tf 50 740 Td (HALLELUJAH) Tj ET BT /F1 10 Tf 50 720 Td (Capo : IV) Tj ET"

    private fun document(content: String): ByteArray {
        val writer = PdfTestWriter()
        val image = "/Type /XObject /Subtype /Image /Width 1 /Height 1 /ColorSpace /DeviceGray /BitsPerComponent 8"
        writer.add("<< /Type /Catalog /Pages 2 0 R >>")
        writer.add("<< /Type /Pages /Kids [3 0 R] /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> /XObject << /Chord 6 0 R /Strip 7 0 R >> >> >>")
        writer.add("<< /Type /Page /Parent 2 0 R /Contents 5 0 R >>")
        writer.add("<< /Type /Font /Subtype /Type1 /BaseFont /Courier /Encoding /WinAnsiEncoding >>")
        writer.stream(content)
        writer.stream("\u0000", image)
        writer.stream("\u0000", image)
        return writer.write()
    }
}
