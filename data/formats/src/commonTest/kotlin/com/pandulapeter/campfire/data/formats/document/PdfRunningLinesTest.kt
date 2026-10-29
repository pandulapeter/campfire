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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PdfRunningLinesTest {

    @Test
    fun `a numbered footer on every page is dropped while the text the pages share is kept`() = runTest {
        val pages = extract(4) { page ->
            val header = if (page == 1) "1 0 0 1 50 780 Tm (Songbook) Tj " else ""
            header + "1 0 0 1 50 700 Tm (Verse $page) Tj 1 0 0 1 50 400 Tm (The same chorus) Tj 1 0 0 1 50 20 Tm (Page $page of 4) Tj"
        }

        assertEquals(listOf("Songbook", "Verse 1", "The same chorus"), pages.first())
        (2..4).forEach { page -> assertEquals(listOf("Verse $page", "The same chorus"), pages[page - 1]) }
    }

    @Test
    fun `a header on half of the pages is a running header`() = runTest {
        val pages = extract(4) { page ->
            val header = if (page <= 2) "1 0 0 1 50 780 Tm (Songbook) Tj " else ""
            header + "1 0 0 1 50 700 Tm (Verse $page) Tj"
        }

        assertEquals((1..4).map { listOf("Verse $it") }, pages)
    }

    @Test
    fun `a header and a footer printed at both margins are dropped from a short last page read as columns`() = runTest {
        val pages = extract(3) { page ->
            val body = if (page < 3) (0 until 30).joinToString(" ") { "1 0 0 1 50 ${700 - it * 20} Tm (Line $page $it of a song sung across the whole width of its page) Tj" }
            else "1 0 0 1 50 700 Tm (The end) Tj 1 0 0 1 50 680 Tm (Fade out) Tj"
            "1 0 0 1 0 780 Tm (Song by Someone) Tj 1 0 0 1 380 780 Tm (https://example.com/song?id=12) Tj " +
                "1 0 0 1 0 20 Tm ($page of 3) Tj 1 0 0 1 480 20 Tm (26/10/2026, 14:25) Tj $body"
        }

        assertEquals(listOf("The end", "Fade out"), pages.last())
        assertEquals(30, pages.first().size)
    }

    @Test
    fun `a section heading that opens two pages is kept`() = runTest {
        val pages = extract(3) { page ->
            val heading = when (page) { 2 -> "1 0 0 1 50 780 Tm (Verse 2) Tj "; 3 -> "1 0 0 1 50 780 Tm (Verse 6) Tj "; else -> "" }
            heading + "1 0 0 1 50 700 Tm (A line sung on page $page) Tj"
        }

        assertEquals(listOf("Verse 2", "A line sung on page 2"), pages[1])
        assertEquals(listOf("Verse 6", "A line sung on page 3"), pages[2])
    }

    @Test
    fun `a songbook's footer counting each song's pages is dropped`() = runTest {
        val footers = listOf("Page 1 of 1", "Page 1 of 3", "Page 2 of 3", "Page 3 of 3")
        val pages = extract(4) { page -> "1 0 0 1 50 700 Tm (A line sung on page $page) Tj 1 0 0 1 50 20 Tm (${footers[page - 1]}) Tj" }

        assertEquals((1..4).map { listOf("A line sung on page $it") }, pages)
    }

    @Test
    fun `a time signature opening each page at the same place is kept`() = runTest {
        val signatures = listOf("6/8", "3/4", "2/4", "3/4")
        val pages = extract(4) { page -> "1 0 0 1 50 780 Tm (${signatures[page - 1]}) Tj 1 0 0 1 50 700 Tm (A line sung on page $page) Tj" }

        assertEquals((1..4).map { listOf(signatures[it - 1], "A line sung on page $it") }, pages)
    }

    @Test
    fun `a page count written out is this page's own only`() {
        assertTrue(PdfRunningLines.isPageCount("2 of 3", page = 2, pageCount = 3))
        assertTrue(PdfRunningLines.isPageCount("Page 2 of 3", page = 2, pageCount = 3))
        assertFalse(PdfRunningLines.isPageCount("2 of 3", page = 1, pageCount = 3))
        assertFalse(PdfRunningLines.isPageCount("One of 3", page = 1, pageCount = 3))
    }

    /** The lines of a document of [count] pages in 10-point Courier, each page's content made by [content]. */
    private suspend fun extract(count: Int, content: (page: Int) -> String): List<List<String>> {
        val writer = PdfTestWriter()
        writer.add("<< /Type /Catalog /Pages 2 0 R >>")
        writer.add("<< /Type /Pages /Kids [${(1..count).joinToString(" ") { "${it + 3} 0 R" }}] /MediaBox [0 0 612 792] /Resources << /Font << /F1 3 0 R >> >> >>")
        writer.add("<< /Type /Font /Subtype /Type1 /BaseFont /Courier /Encoding /WinAnsiEncoding >>")
        (1..count).forEach { writer.add("<< /Type /Page /Parent 2 0 R /Contents ${it + count + 3} 0 R >>") }
        (1..count).forEach { writer.stream("BT /F1 10 Tf ${content(it)} ET") }
        return PdfTextExtractor.extract(writer.write()).pages.map { page ->
            page.lines.map { line -> line.spans.joinToString("") { it.text } }.filter { it.isNotEmpty() }
        }
    }
}
