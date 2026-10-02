/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.print

import kotlinx.coroutines.test.runTest
import kotlin.test.*

internal class PrintPdfWriterTest {
    @Test fun packsTwoRoundedGraysABytePaddingAnOddRowWithWhite() {
        val packed = ByteArray(4)
        val pixels = intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF888888.toInt())
        packPrintRows(pixels + pixels, width = 3, rows = 2, packed = packed, firstRow = 0)
        assertContentEquals(byteArrayOf(0x0F, 0x8F.toByte(), 0x0F, 0x8F.toByte()), packed)
    }

    @Test fun pdfCrossReferenceOffsetsResolveToEveryObjectWithTwoPages() = runTest {
        val writer = PrintPdfWriter(595.276f, 841.89f, "Szőke (live)")
        repeat(2) { writer.addPage(8, 8, ByteArray(32) { if (it % 4 == 0) 0x0F else -1 }) }
        val bytes = writer.finish()
        val text = bytes.decodeToString()
        assertTrue(text.startsWith("%PDF-1.4"))
        assertTrue(text.contains("/Count 2"))
        assertTrue((10..13).all { bytes[it].toInt() and 255 >= 128 }, "The second line marks the file as binary.")
        assertTrue(text.contains("/Title <FEFF0053007a0151006b006500200028006c0069007600650029>"))
        val info = Regex("""/Info (\d+) 0 R""").find(text)!!.groupValues[1].toInt()
        val xrefOffset = text.substringAfterLast("startxref\n").substringBefore('\n').toInt()
        val xref = bytes.copyOfRange(xrefOffset, bytes.size).decodeToString()
        assertTrue(xref.startsWith("xref\n"))
        val entries = xref.lines().drop(3).takeWhile { it.endsWith(" n ") }
        assertTrue(info <= entries.size)
        entries.forEachIndexed { index, line ->
            val offset = line.take(10).toInt()
            assertTrue(bytes.copyOfRange(offset, minOf(offset + 16, bytes.size)).decodeToString().startsWith("${index + 1} 0 obj\n"))
        }
    }

    @Test fun aReusedScratchBufferWritesIdenticalPagesIdentically() = runTest {
        val page = ByteArray(32 * 64) { if (it % 7 == 0) 0 else -1 }
        val writer = PrintPdfWriter(100f, 100f, "Pages")
        writer.addPage(32, 16, ByteArray(256) { it.toByte() })
        repeat(2) { writer.addPage(64, 64, page) }
        val bytes = writer.finish()
        // One character per byte, so that a match's position is the stream's offset in the file.
        val text = CharArray(bytes.size) { (bytes[it].toInt() and 255).toChar() }.concatToString()
        val streams = Regex("""/Width (\d+) [^>]*/Length (\d+) >>\nstream\n""").findAll(text).map { match ->
            val start = match.range.last + 1
            bytes.copyOfRange(start, start + match.groupValues[2].toInt())
        }.toList()
        assertEquals(3, streams.size)
        assertContentEquals(deflated(page), streams[1])
        assertContentEquals(streams[1], streams[2])
    }

    @Test fun invisibleFontsCarryUnicodeClustersAndPrintedStylesAcrossPages() = runTest {
        val writer = PrintPdfWriter(100f, 100f, "Unicode")
        val glyphs = listOf(
            PrintPdfText("Ő", 10f, 20f, 8f, 12f, PrintStyle(12, bold = true)),
            PrintPdfText("\uD83D\uDE42", 18f, 20f, 12f, 12f, PrintStyle(12)),
            PrintPdfText("e\u0301", 30f, 20f, 6f, 12f, PrintStyle(12, monospace = true)),
        )
        repeat(2) { writer.addPage(2, 2, ByteArray(2) { -1 }, glyphs) }
        val text = writer.finish().decodeToString()
        assertEquals(3, Regex("/Subtype /Type3").findAll(text).count())
        assertTrue(text.contains("<01> <0150>"))
        assertTrue(text.contains("<01> <d83dde42>"))
        assertTrue(text.contains("<01> <00650301>"))
        assertTrue(text.contains("/FontWeight 700"))
        assertTrue(text.contains("/FontName /CampfireTextMonoF2 /Flags 5"))
        assertEquals(2, Regex("/Font << /F0 \\d+ 0 R /F1 \\d+ 0 R /F2 \\d+ 0 R >>").findAll(text).count())
    }

    @Test fun aFontSplitsAfter255DistinctCharactersWithoutTruncatingItsUnicodeMap() = runTest {
        val writer = PrintPdfWriter(1000f, 100f, "Many characters")
        val glyphs = (0x400..0x500).mapIndexed { index, code ->
            PrintPdfText(code.toChar().toString(), index * 2f, 10f, 2f, 12f, PrintStyle(12))
        }
        writer.addPage(2, 2, ByteArray(2) { -1 }, glyphs)
        val text = writer.finish().decodeToString()
        assertEquals(2, Regex("/Subtype /Type3").findAll(text).count())
        assertTrue(text.contains("/LastChar 255"))
        assertTrue(text.contains("/LastChar 2"))
        assertTrue(text.contains("<ff> <04fe>"))
        assertTrue(text.contains("<01> <04ff>"))
        assertTrue(text.contains("<02> <0500>"))
        assertTrue(Regex("(\\d+) beginbfchar").findAll(text).all { it.groupValues[1].toInt() <= 100 })
    }

    @Test fun pdfNumbersAreFiniteDecimalNumbersEvenForSmallAdvances() {
        assertEquals("0.001", printPdfNumber(0.001f))
        assertEquals("-0.125", printPdfNumber(-0.125f))
        assertEquals("595.276", printPdfNumber(595.276f))
        assertEquals("10", printPdfNumber(10f))
        assertFailsWith<IllegalArgumentException> { printPdfNumber(Float.NaN) }
    }

    @Test fun rightToLeftRunsAreFoundWhole() {
        fun glyph(run: Int, isRtl: Boolean = false) = PrintPdfText("x", 0f, 0f, 1f, 1f, PrintStyle(12), run, isRtl)
        val glyphs = listOf(
            glyph(0), glyph(0),
            glyph(1, isRtl = true), glyph(1, isRtl = true),
            glyph(2, isRtl = true), glyph(2), glyph(2), glyph(2, isRtl = true),
            glyph(3, isRtl = true),
        )
        assertEquals(listOf(2..3, 4..7, 8..8), rtlRuns(glyphs))
        assertEquals(emptyList(), rtlRuns(glyphs.take(2)))
    }

    @Test fun aRightToLeftRunCarriesItsLogicalTextAndLatinPagesAreUnchanged() = runTest {
        suspend fun content(glyphs: List<PrintPdfText>): ByteArray {
            val writer = PrintPdfWriter(100f, 100f, "Text")
            writer.addPage(2, 2, ByteArray(2) { -1 }, glyphs)
            val bytes = writer.finish()
            val text = CharArray(bytes.size) { (bytes[it].toInt() and 255).toChar() }.concatToString()
            val match = Regex("""<< /Filter /FlateDecode /Length (\d+) >>\nstream\n""").find(text)!!
            val start = match.range.last + 1
            return bytes.copyOfRange(start, start + match.groupValues[1].toInt())
        }
        val style = PrintStyle(12)
        val image = "q 100 0 0 100 0 0 cm /Im0 Do Q\nBT 3 Tr\n/F0 12 Tf\n1 0 0 1 10 68 Tm [<01>"
        val latin = listOf(PrintPdfText("a", 10f, 20f, 6f, 12f, style, 0), PrintPdfText("b", 50f, 20f, 6f, 12f, style, 1))
        assertContentEquals(deflated("$image -2833.333 <02>] TJ\nET\n".encodeToByteArray()), content(latin))
        val mixed = listOf(
            PrintPdfText("a", 10f, 20f, 6f, 12f, style, 0),
            PrintPdfText("\u05d0", 40f, 20f, 6f, 12f, style, 1, isRtl = true),
            PrintPdfText("\u05d1", 34f, 20f, 6f, 12f, style, 1, isRtl = true),
            PrintPdfText("b", 50f, 20f, 6f, 12f, style, 2),
        )
        val expected = "$image] TJ\n/Span << /ActualText <FEFF05d005d1> >> BDC\n[ -2000 <02> 1000 <03>] TJ\nEMC\n[ -833.333 <04>] TJ\nET\n"
        assertContentEquals(deflated(expected.encodeToByteArray()), content(mixed))
    }

    private suspend fun deflated(input: ByteArray) = PrintBytes().also { PrintDeflater().deflate(input, it) }.result()
}
