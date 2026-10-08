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
