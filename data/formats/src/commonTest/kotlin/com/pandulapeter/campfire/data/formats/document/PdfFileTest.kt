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
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class PdfFileTest {
    @Test
    fun `recovery scan over many unterminated streams is fast`() {
        val text = buildString {
            append("%PDF-1.7\n")
            repeat(20_000) { append("$it 0 obj\n<< /A 1 >>\nstream\nab\n") }
        }
        val elapsed = measureTime { assertFails { PdfFile(text.encodeToByteArray()).catalog() } }
        assertTrue(elapsed < 2.seconds, "Took $elapsed")
    }

    @Test
    fun `streams without a length end at their endstream in a recovery scan`() = runTest {
        val content = "BT /F1 10 Tf 50 700 Td (Unmeasured) Tj ET"
        val bytes = PdfTestWriter.song(content, brokenXref = true).decodeToString().replace("/Length ${content.length} ", "")
        assertTrue("/Length" !in bytes)
        assertEquals("Unmeasured", PdfTextExtractor.extract(bytes.encodeToByteArray()).pages.single().lines.single().spans.joinToString("") { it.text })
    }

    @Test
    fun `a length pointing into shared whitespace is linear`() {
        val placeholder = "0000000000"
        val header = "%PDF-1.7\n"
        val objects = List(5_000) { "$it 0 obj\n<< /Length $placeholder >>\nstream\nab\nendstream\nendobj\n" }
        val blockStart = header.length + objects.sumOf { it.length }
        var offset = header.length
        val text = buildString {
            append(header)
            for (item in objects) {
                val dataStart = offset + item.indexOf("stream\n") + "stream\n".length
                append(item.replace(placeholder, (blockStart - dataStart).toString().padStart(placeholder.length, '0')))
                offset += item.length
            }
            append(" ".repeat(2 shl 20))
            append("x")
        }
        val elapsed = measureTime { PdfFile(text.encodeToByteArray()) }
        assertTrue(elapsed < 2.seconds, "Took $elapsed")
    }

    @Test
    fun `object stream headers are read once and an unlisted object is not there`() {
        val count = 20_000
        var bytes = "%PDF-1.7\n".encodeToByteArray()
        val catalog = bytes.size
        bytes += "1 0 obj\n<< /Type /Catalog >>\nendobj\n".encodeToByteArray()
        val header = buildString { repeat(count) { append("${100_001 + it} 0 ") } }
        val data = header + "null"
        val stream = bytes.size
        bytes += "2 0 obj\n<< /Type /ObjStm /N $count /First ${header.length} /Length ${data.length} >>\nstream\n$data\nendstream\nendobj\n".encodeToByteArray()
        val xref = bytes.size
        val xrefNumber = count + 3
        fun row(type: Int, first: Int, second: Int) = byteArrayOf(
            type.toByte(), (first ushr 24).toByte(), (first ushr 16).toByte(), (first ushr 8).toByte(), first.toByte(), (second ushr 8).toByte(), second.toByte(),
        )
        var rows = row(0, 0, 65535) + row(1, catalog, 0) + row(1, stream, 0)
        val compressed = ByteArray(count * 7)
        for (index in 0 until count) row(2, 2, index).copyInto(compressed, index * 7)
        rows += compressed + row(1, xref, 0)
        bytes += "$xrefNumber 0 obj\n<< /Type /XRef /Root 1 0 R /Size ${xrefNumber + 1} /W [1 4 2] /Length ${rows.size} >>\nstream\n".encodeToByteArray() +
            rows + "\nendstream\nendobj\nstartxref\n$xref\n%%EOF\n".encodeToByteArray()
        lateinit var file: PdfFile
        val elapsed = measureTime {
            file = PdfFile(bytes)
            for (number in 3 until count + 3) file.resolve(PdfReference(number))
        }
        assertTrue(elapsed < 2.seconds, "Took $elapsed")
        assertNull(file.resolve(PdfReference(3)))
    }

    @Test
    fun `the newest definition wins in a recovery scan`() = runTest {
        val writer = PdfTestWriter()
        writer.add("<< /Type /Catalog /Pages 2 0 R >>")
        writer.add("<< /Type /Pages /Kids [3 0 R] /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> >>")
        writer.add("<< /Type /Page /Parent 2 0 R /Contents 5 0 R >>")
        writer.add("<< /Type /Font /Subtype /Type1 /BaseFont /Courier /Encoding /WinAnsiEncoding >>")
        writer.stream("BT /F1 10 Tf 50 700 Td (Old) Tj ET")
        writer.stream("BT /F1 10 Tf 50 700 Td (Newer) Tj ET")
        writer.stream("BT /F1 10 Tf 50 700 Td (Newest) Tj ET")
        writer.stream("3 0 << /Type /Page /Parent 2 0 R /Contents 5 0 R >>", "/Type /ObjStm /N 1 /First 4")
        writer.stream("3 0 << /Type /Page /Parent 2 0 R /Contents 6 0 R >>", "/Type /ObjStm /N 1 /First 4")
        val bytes = writer.write(brokenXref = true)
        suspend fun text(bytes: ByteArray) = PdfTextExtractor.extract(bytes).pages.single().lines.single().spans.joinToString("") { it.text }
        assertEquals("Newer", text(bytes))
        assertEquals("Newest", text(bytes + "3 0 obj\n<< /Type /Page /Parent 2 0 R /Contents 7 0 R >>\nendobj\n".encodeToByteArray()))
    }

    @Test
    fun `cached reference chains resolve every time and cycles are rejected`() {
        val writer = PdfTestWriter()
        writer.add("2 0 R")
        writer.add("<< /Type /Catalog >>")
        val file = PdfFile(writer.write())
        assertEquals("Catalog", file.dictionary(PdfReference(1))?.get("Type").name())
        assertEquals("Catalog", file.dictionary(PdfReference(1))?.get("Type").name())
        val cyclic = PdfTestWriter()
        cyclic.add("2 0 R")
        cyclic.add("1 0 R")
        assertFailsWith<IllegalArgumentException> { PdfFile(cyclic.write()).resolve(PdfReference(1)) }
    }
}
