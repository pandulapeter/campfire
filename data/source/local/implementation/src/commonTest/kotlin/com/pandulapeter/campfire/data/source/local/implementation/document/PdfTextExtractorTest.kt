/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.document

import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.data.source.local.implementation.source.DocumentLocalSourceImpl
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PdfTextExtractorTest {
    @Test
    fun oversizedShownStringsStopAtTheGlyphBudgetAndCancellationPropagates() = runTest {
        val source = DocumentLocalSourceImpl()
        assertNull(source.extract(ImportedFile("huge.pdf", PdfTestWriter.song("BT /F1 10 Tf (" + "a".repeat(PdfTextExtractor.MAX_GLYPHS + 1) + ") Tj ET"))))
        val cancelled = launch(start = CoroutineStart.UNDISPATCHED) {
            coroutineContext.cancel()
            source.extract(ImportedFile("cancelled.pdf", PdfTestWriter.song("BT /F1 10 Tf (text) Tj ET")))
            error("Cancelled extraction returned")
        }
        cancelled.join()
        assertTrue(cancelled.isCancelled)
    }

    @Test
    fun cachedReferenceChainsResolveEveryTimeAndCyclesAreRejected() {
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

    @Test
    fun inheritedRotationKeepsHorizontalTextAndDropsTheOtherOrientation() = runTest {
        val writer = PdfTestWriter()
        writer.add("<< /Type /Catalog /Pages 2 0 R >>")
        writer.add("<< /Type /Pages /Kids [3 0 R] /MediaBox [0 0 612 792] /Rotate 90 /Resources << /Font << /F1 4 0 R >> >> >>")
        writer.add("<< /Type /Page /Contents 5 0 R >>")
        writer.add("<< /Type /Font /Subtype /Type1 /BaseFont /Courier >>")
        writer.stream("BT /F1 10 Tf 0 1 -1 0 100 50 Tm (Rotated) Tj 1 0 0 1 50 200 Tm (Vertical) Tj ET")
        val line = PdfTextExtractor.extract(writer.write()).pages.single().lines.single()
        assertEquals("Rotated", line.spans.joinToString("") { it.text })
        assertEquals(50.0, line.spans.first().start)
        assertEquals(56.0, line.spans.first().end)
    }

    @Test
    fun dropsRunningHeadersAndPageNumbersButKeepsParagraphGaps() = runTest {
        val writer = PdfTestWriter()
        writer.add("<< /Type /Catalog /Pages 2 0 R >>")
        writer.add("<< /Type /Pages /Kids [3 0 R 4 0 R] /MediaBox [0 0 612 792] /Resources << /Font << /F1 5 0 R >> >> >>")
        writer.add("<< /Type /Page /Contents 6 0 R >>")
        writer.add("<< /Type /Page /Contents 7 0 R >>")
        writer.add("<< /Type /Font /Subtype /Type1 /BaseFont /Courier >>")
        for (page in 1..2) writer.stream("BT /F1 10 Tf 50 780 Td (Songbook $page) Tj 1 0 0 1 50 700 Tm (First) Tj 1 0 0 1 50 684 Tm (Second) Tj 1 0 0 1 50 640 Tm (Third) Tj 1 0 0 1 50 20 Tm ($page) Tj ET")
        val pages = PdfTextExtractor.extract(writer.write()).pages
        assertEquals(2, pages.size)
        pages.forEach { page -> assertEquals(listOf("First", "Second", "", "Third"), page.lines.map { it.spans.joinToString("") { span -> span.text } }) }
    }

    @Test
    fun dropsPageCountFootersOnASinglePageButKeepsTheSameTextInTheBody() = runTest {
        val bytes = PdfTestWriter.song("BT /F1 10 Tf 50 700 Td (1 / 1) Tj " +
            "1 0 0 1 50 684 Tm (Song lyrics) Tj 1 0 0 1 50 20 Tm (1 / 1) Tj ET")
        val lines = PdfTextExtractor.extract(bytes).pages.single().lines
        assertEquals(listOf("1 / 1", "Song lyrics"), lines.map { it.spans.joinToString("") { span -> span.text } })
    }

    @Test
    fun keepsATimeSignatureAtTheTopOfThePage() = runTest {
        val bytes = PdfTestWriter.song("BT /F1 10 Tf 50 780 Td (3/4) Tj 1 0 0 1 50 700 Tm (Song lyrics) Tj 1 0 0 1 50 20 Tm (2 / 3) Tj ET")
        val lines = PdfTextExtractor.extract(bytes).pages.single().lines
        assertEquals(listOf("3/4", "", "Song lyrics", "", "2 / 3"), lines.map { it.spans.joinToString("") { span -> span.text } })
    }

    @Test
    fun rejectsInvalidPageBoundsAndCyclicPagesWithoutFailingTheSource() = runTest {
        for (tree in listOf("/Kids [2 0 R]", "/Kids [3 0 R] /MediaBox [0 0 0 0]")) {
            val writer = PdfTestWriter()
            writer.add("<< /Type /Catalog /Pages 2 0 R >>")
            writer.add("<< /Type /Pages $tree >>")
            writer.add("<< /Type /Page >>")
            assertNull(DocumentLocalSourceImpl().extract(ImportedFile("invalid.pdf", writer.write())))
        }
    }

    @Test
    fun acceptsPageBoxesWrittenWithTheirCornersInEitherOrder() = runTest {
        val bytes = PdfTestWriter.song("BT /F1 10 Tf 50 700 Td (Flipped) Tj ET").decodeToString().replace("/MediaBox [0 0 612 792]", "/MediaBox [612 792 0 0]")
        assertEquals("Flipped", PdfTextExtractor.extract(bytes.encodeToByteArray()).pages.single().lines.single().spans.joinToString("") { it.text })
    }

    @Test
    fun recoversFromACorruptCompressedXrefStreamByScanning() = runTest {
        val writer = PdfTestWriter()
        writer.add("<< /Type /Catalog /Pages 2 0 R >>")
        writer.add("<< /Type /Pages /Kids [3 0 R] /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> >>")
        writer.add("<< /Type /Page /Contents 5 0 R >>")
        writer.add("<< /Type /Font /Subtype /Type1 /BaseFont /Courier /Encoding /WinAnsiEncoding >>")
        writer.stream("BT /F1 10 Tf 50 700 Td (Recovered) Tj ET")
        val bytes = writer.writeXrefStream(predictor = true)
        // The byte after the zlib header opens the deflate block; 7 is a block type that does not exist.
        val text = CharArray(bytes.size) { (bytes[it].toInt() and 255).toChar() }.concatToString()
        bytes[text.lastIndexOf("stream\n") + "stream\n".length + 2] = 7
        assertEquals("Recovered", PdfTextExtractor.extract(bytes).pages.single().lines.single().spans.joinToString("") { it.text })
    }

    @Test
    fun readsObjectStreamsAndXrefStreamsWithPngPrediction() = runTest {
        for (predictor in listOf(false, true)) {
            val writer = PdfTestWriter()
            writer.add("<< /Type /Catalog /Pages 2 0 R >>")
            writer.add("<< /Type /Pages /Kids [3 0 R] /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> >>")
            writer.add("<< /Type /Page /Contents 5 0 R >>")
            writer.add("null")
            writer.stream("BT /F1 10 Tf 50 700 Td (Compressed) Tj ET")
            writer.stream("4 0 << /Type /Font /Subtype /Type1 /BaseFont /Courier /Encoding /WinAnsiEncoding >>", "/Type /ObjStm /N 1 /First 4")
            val bytes = writer.writeXrefStream(mapOf(4 to (6 to 0)), predictor)
            assertEquals("Compressed", PdfTextExtractor.extract(bytes).pages.single().lines.single().spans.joinToString("") { it.text })
        }
    }

    @Test
    fun incrementalSavesUseTheLatestObjectAndRejectCyclicPrevChains() = runTest {
        val base = PdfTestWriter.song("BT /F1 10 Tf 50 700 Td (Old) Tj ET")
        val previous = Regex("startxref\\s+([0-9]+)").find(base.decodeToString())!!.groupValues[1]
        val content = "BT /F1 10 Tf 50 700 Td (New) Tj ET"
        val objectBytes = "5 0 obj\n<< /Length ${content.length} >>\nstream\n$content\nendstream\nendobj\n".encodeToByteArray()
        val offset = base.size + objectBytes.size
        val trailer = "xref\n5 1\n${base.size.toString().padStart(10, '0')} 00000 n \ntrailer\n<< /Size 6 /Prev $previous >>\nstartxref\n$offset\n%%EOF\n".encodeToByteArray()
        val updated = base + objectBytes + trailer
        assertEquals("New", PdfTextExtractor.extract(updated).pages.single().lines.single().spans.joinToString("") { it.text })
        // A damaged chain is recoverable by scanning the objects, without following the cycle indefinitely.
        val cyclic = base + objectBytes + trailer.decodeToString().replace("/Prev $previous", "/Prev $offset").encodeToByteArray()
        assertEquals("New", PdfTextExtractor.extract(cyclic).pages.single().lines.single().spans.joinToString("") { it.text })
    }

    @Test
    fun formXobjectsUseTheirResourcesAndMatrixAndIgnoreImages() = runTest {
        val writer = PdfTestWriter()
        writer.add("<< /Type /Catalog /Pages 2 0 R >>")
        writer.add("<< /Type /Pages /Kids [3 0 R] /MediaBox [0 0 612 792] /Resources << /XObject << /Form1 6 0 R /Image 7 0 R >> >> >>")
        writer.add("<< /Type /Page /Contents 5 0 R >>")
        writer.add("<< /Type /Font /Subtype /Type1 /BaseFont /Courier /Encoding /WinAnsiEncoding >>")
        writer.stream("/Image Do /Form1 Do")
        writer.stream("BT /F1 10 Tf 0 0 Td (Form text) Tj ET", "/Subtype /Form /Matrix [1 0 0 1 50 700] /Resources << /Font << /F1 4 0 R >> >>")
        writer.stream(byteArrayOf(1, 2, 3), "/Subtype /Image /Filter /DCTDecode")
        val spans = PdfTextExtractor.extract(writer.write()).pages.single().lines.single().spans
        assertEquals("Form text", spans.joinToString("") { it.text })
        assertEquals(50.0, spans.first().start)
    }
    @Test
    fun extractsPositionedStringsInBaselineOrderAndRecoversBrokenXrefOffsets() = runTest {
        val content = "BT /F1 10 Tf 1 0 0 1 50 700 Tm (Am) Tj 1 0 0 1 92 700 Tm (C) Tj 1 0 0 1 50 688 Tm (Hello world) Tj ET"
        for (broken in listOf(false, true)) {
            val result = PdfTextExtractor.extract(PdfTestWriter.song(content, brokenXref = broken))
            val lines = result.pages.single().lines
            assertEquals(listOf("Am     C", "Hello world"), lines.map { it.spans.joinToString("") { span -> span.text } })
            assertEquals(50.0, lines[0].spans.first().start)
            assertEquals(56.0, lines[0].spans.first().end)
            assertEquals(92.0, lines[0].spans.last().start)
            assertTrue(lines[0].spans.first().isMonospace)
        }
    }

    @Test
    fun interpretsTextOperatorsAndGraphicsMatrices() = runTest {
        val content = "q 1 0 0 1 20 10 cm BT /F1 10 Tf 12 TL 1 0 0 1 30 690 Tm [(A) -400 (B)] TJ (C) ' 0 0 (D) \" 0 -12 TD (E) Tj T* (F) Tj 0 -12 Td (G) Tj ET Q"
        val lines = PdfTextExtractor.extract(PdfTestWriter.song(content)).pages.single().lines
        assertEquals(listOf("A B", "C", "D", "E", "F", "G"), lines.map { it.spans.joinToString("") { span -> span.text } })
        assertEquals(50.0, lines[0].spans.first().start)
        assertEquals(60.0, lines[0].spans.last().start)
    }

    @Test
    fun decodesToUnicodeCharsRangesArraysLigaturesAndCidWidths() = runTest {
        val font = "/Type /Font /Subtype /Type0 /BaseFont /Embedded-Bold /Encoding /Identity-H /DescendantFonts [6 0 R] /ToUnicode 7 0 R"
        val bytes = PdfTestWriter.song("BT /F1 10 Tf 50 700 Td <00010002000300040005> Tj ET", font, extra = { writer ->
            writer.add("<< /Type /Font /Subtype /CIDFontType2 /DW 500 /W [1 [1000 200] 3 5 600] >>")
            writer.stream("1 begincodespacerange <0000> <FFFF> endcodespacerange 1 beginbfchar <0001> <0151> endbfchar 1 beginbfrange <0002> <0003> <0041> endbfrange 1 beginbfrange <0004> <0005> [<00660069> <03A9>] endbfrange")
        })
        val spans = PdfTextExtractor.extract(bytes).pages.single().lines.single().spans
        assertEquals("\u0151ABfi\u03a9", spans.joinToString("") { it.text })
        assertEquals(listOf(50.0, 60.0, 62.0, 68.0, 74.0), spans.map { it.start })
        assertTrue(spans.first().isBold)
    }

    @Test
    fun supportsWinAnsiMacRomanStandardAndGlyphNameDifferences() = runTest {
        val encodings = listOf(
            "/WinAnsiEncoding" to ("<E9>" to "\u00e9"),
            "/MacRomanEncoding" to ("<8E>" to "\u00e9"),
            "/StandardEncoding" to ("<AE>" to "\ufb01"),
            "<< /BaseEncoding /WinAnsiEncoding /Differences [65 /ohungarumlaut /uhungarumlaut /uni0416 /u1F600] >>" to ("<41424344>" to "\u0151\u0171\u0416\ud83d\ude00"),
        )
        for ((encoding, pair) in encodings) {
            val bytes = PdfTestWriter.song("BT /F1 10 Tf 50 700 Td ${pair.first} Tj ET", "/Type /Font /Subtype /Type1 /BaseFont /Courier /Encoding $encoding")
            assertEquals(pair.second, PdfTextExtractor.extract(bytes).pages.single().lines.single().spans.joinToString("") { it.text })
        }
        assertEquals("\u03a9", PdfFont.glyphName("Omegagreek"))
        assertEquals("\u0416", PdfFont.glyphName("Zhecyrillic"))
    }

    @Test
    fun protectedScannedMalformedAndUnmappedDocumentsAreUnreadable() = runTest {
        val source = DocumentLocalSourceImpl()
        assertNull(source.extract(ImportedFile("scan.pdf", PdfTestWriter.song("q Q"))))
        assertNull(source.extract(ImportedFile("bad.pdf", "not a pdf".encodeToByteArray())))
        assertNull(source.extract(ImportedFile("unknown.pdf", PdfTestWriter.song("BT /F1 10 Tf (abc) Tj ET", "/Type /Font /Subtype /Type1 /BaseFont /Unknown"))))
        val writer = PdfTestWriter()
        writer.add("<< /Type /Catalog /Pages 2 0 R >>")
        writer.add("<< /Type /Pages /Kids [] >>")
        assertNull(source.extract(ImportedFile("protected.pdf", writer.write("/Root 1 0 R /Encrypt 8 0 R"))))
        assertNotNull(source.extract(ImportedFile("okay.pdf", PdfTestWriter.song("BT /F1 10 Tf 50 700 Td (text) Tj ET"))))
    }

    @Test
    fun tokenizerPreservesBinaryStringsReferencesAndEscapes() {
        val parser = PdfSyntax("<< /Na#6de (a\\(b\\)\\101\\n) /Hex <4142F> /Ref 12 0 R /List [1 -2.5 /Name] >>".encodeToByteArray())
        val value = parser.next() as PdfDictionary
        assertEquals("a(b)A\n", (value["Name"] as PdfString).bytes.decodeToString())
        assertEquals(listOf(65, 66, 240), (value["Hex"] as PdfString).bytes.map { it.toInt() and 255 })
        assertEquals(PdfReference(12), value["Ref"])
        assertEquals(-2.5, value["List"].array()[1].number())
        assertFailsWith<IllegalArgumentException> { PdfSyntax(("[".repeat(66) + "]".repeat(66)).encodeToByteArray()).next() }
    }
}
