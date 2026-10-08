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

import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.data.formats.zip.ZipEntry
import com.pandulapeter.campfire.data.formats.zip.ZipWriter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocxTextExtractorTest {
    @Test
    fun `text box paragraphs stay separate and invalid numeric styles use defaults`() = runTest {
        val xml = document("""
            <w:p><w:r><w:t>Outside</w:t><w:drawing><w:txbxContent>
              <w:p><w:r><w:rPr><w:sz w:val="NaN"/></w:rPr><w:t>First inside</w:t></w:r></w:p>
              <w:p><w:r><w:t>Second inside</w:t></w:r></w:p>
            </w:txbxContent></w:drawing><w:t>After</w:t></w:r></w:p>
        """.trimIndent())
        val lines = DocxTextExtractor.fromXml(xml).pages.single().lines
        assertEquals(listOf("Outside", "First inside", "Second inside", "After"), lines.map { it.spans.joinToString("") { span -> span.text } })
        assertTrue(lines.flatMap { it.spans }.all { it.size.isFinite() && it.start.isFinite() && it.end.isFinite() })
    }

    @Test
    fun `reads a text box and a tracked move only once`() = runTest {
        val xml = document("""
            <w:p><w:r><mc:AlternateContent>
              <mc:Choice><w:drawing><wps:txbx><w:txbxContent><w:p><w:r><w:t>Boxed</w:t></w:r></w:p></w:txbxContent></wps:txbx></w:drawing></mc:Choice>
              <mc:Fallback><w:pict><v:textbox><w:txbxContent><w:p><w:r><w:t>Boxed</w:t></w:r></w:p></w:txbxContent></v:textbox></w:pict></mc:Fallback>
            </mc:AlternateContent></w:r></w:p>
            <w:p><w:moveFrom><w:r><w:t>Moved</w:t></w:r></w:moveFrom></w:p>
            <w:p><w:moveTo><w:r><w:t>Moved</w:t></w:r></w:moveTo></w:p>
        """.trimIndent())
        val lines = DocxTextExtractor.fromXml(xml).pages.single().lines
        assertEquals(listOf("Boxed", "Moved"), lines.map { it.spans.joinToString("") { span -> span.text } }.filter { it.isNotEmpty() })
    }

    @Test
    fun `reads styles tracked changes hyperlinks breaks and tabs`() = runTest {
        val styles = """<w:styles xmlns:w="urn:test"><w:docDefaults><w:rPrDefault><w:rPr><w:sz w:val="24"/></w:rPr></w:rPrDefault></w:docDefaults><w:style w:styleId="Title"><w:name w:val="Title"/><w:rPr><w:b/><w:sz w:val="40"/></w:rPr></w:style></w:styles>"""
        val xml = document("""
            <w:p><w:pPr><w:pStyle w:val="Title"/></w:pPr><w:r><w:t>Title &amp; song</w:t></w:r></w:p>
            <w:p><w:pPr><w:tabs><w:tab w:pos="720"/></w:tabs></w:pPr>
              <w:r><w:rPr><w:rFonts w:ascii="Courier New"/></w:rPr><w:t>Am</w:t><w:tab/><w:t>C</w:t></w:r>
              <w:del><w:r><w:t>deleted</w:t></w:r></w:del>
              <w:ins><w:r><w:rPr><w:vertAlign w:val="superscript"/></w:rPr><w:t>G</w:t></w:r></w:ins>
              <w:hyperlink><w:r><w:t>link</w:t><w:br/><w:t>next</w:t><w:br w:type="page"/><w:t>page</w:t></w:r></w:hyperlink>
            </w:p>
        """.trimIndent())
        val result = DocxTextExtractor.fromXml(xml, styles)
        assertEquals(2, result.pages.size)
        assertTrue(result.pages[0].lines[0].isHeading)
        assertEquals("Title & song", result.pages[0].lines[0].spans.single().text)
        assertEquals(20.0, result.pages[0].lines[0].spans.single().size)
        assertTrue(result.pages[0].lines[0].spans.single().isBold)
        val runs = result.pages[0].lines[1].spans
        assertEquals(listOf("Am", "C", "G", "link"), runs.map { it.text })
        assertEquals(36.0, runs[1].start)
        assertTrue(runs[0].isMonospace)
        assertTrue(runs[2].isRaised)
        assertEquals("page", result.pages[1].lines.single().spans.single().text)
    }

    @Test
    fun `aligns chord grid cells and reads other tables in cell order`() = runTest {
        fun cell(text: String) = "<w:tc><w:p><w:r><w:t>$text</w:t></w:r></w:p></w:tc>"
        val grid = "<w:tbl><w:tr>${cell("Am")}${cell("C")}</w:tr><w:tr>${cell("Hello")}${cell("world")}</w:tr></w:tbl>"
        val lines = DocxTextExtractor.fromXml(document(grid)).pages.single().lines
        assertEquals(listOf("Am", "C"), lines[0].spans.map { it.text })
        assertEquals(listOf("Hello", "world"), lines[1].spans.map { it.text })
        assertEquals(lines[0].spans.map { it.start }, lines[1].spans.map { it.start })
        val layout = "<w:tbl><w:tr>${cell("left verse")}${cell("right verse")}</w:tr></w:tbl>"
        assertEquals(listOf("left verse", "right verse"), DocxTextExtractor.fromXml(document(layout)).pages.single().lines.map { it.spans.single().text })
    }

    @Test
    fun `reads only the body parts of an actual zip and checks its magic`() = runTest {
        val bytes = ZipWriter.write(listOf(
            ZipEntry("word/document.xml", document("<w:p><w:r><w:t>Readable</w:t></w:r></w:p>").encodeToByteArray()),
            ZipEntry("word/header1.xml", "not XML and never opened".encodeToByteArray()),
            ZipEntry("word/footnotes.xml", "not XML and never opened".encodeToByteArray()),
        ))
        val source = ReadableDocuments
        assertNotNull(source.extract(ImportedFile("song.docx", bytes)))
        assertNull(source.extract(ImportedFile("song.docx", "%PDF-1.7".encodeToByteArray())))
        assertNull(source.extract(ImportedFile("song.docx", ZipWriter.write(listOf(ZipEntry("other.xml", byteArrayOf(1)))))))
        assertNull(source.extract(ImportedFile("song.doc", byteArrayOf(0xd0.toByte(), 0xcf.toByte(), 0x11, 0xe0.toByte()))))
        assertNull(source.extract(ImportedFile("empty.docx", ZipWriter.write(listOf(ZipEntry("word/document.xml", document("<w:p/>").encodeToByteArray()))))))
    }

    @Test
    fun `a Word-shaped document of ten thousand paragraphs is read`() = runTest {
        val paragraph = "<w:p w:rsidR='00A1'><w:pPr><w:spacing w:after='0'/><w:rPr><w:rFonts w:ascii='Arial'/><w:sz w:val='22'/></w:rPr></w:pPr>" +
            "<w:r><w:rPr><w:rFonts w:ascii='Arial'/><w:sz w:val='22'/><w:lang w:val='en-US'/></w:rPr><w:t xml:space='preserve'>hello </w:t></w:r></w:p>"
        val lines = DocxTextExtractor.fromXml(document(paragraph.repeat(10_000))).pages.flatMap { it.lines }
        assertEquals(10_000, lines.size)
    }

    @Test
    fun `a table inside a content control is read`() = runTest {
        fun control(content: String) = "<w:sdt><w:sdtPr/><w:sdtContent>$content</w:sdtContent></w:sdt>"
        fun cell(text: String) = "<w:tc><w:p><w:r><w:t>$text</w:t></w:r></w:p></w:tc>"
        val rows = "<w:tr>${cell("C")}</w:tr><w:tr>${cell("lyric")}</w:tr>"
        suspend fun lines(body: String) = DocxTextExtractor.fromXml(document(body)).pages.flatMap { it.lines }.map { line -> line.spans.joinToString("") { it.text } }
        val expected = listOf("C", "lyric")
        assertEquals(expected, lines(control("<w:tbl>$rows</w:tbl>")))
        assertEquals(expected, lines("<w:tbl>${control(rows)}</w:tbl>"))
        assertEquals(expected, lines("<w:tbl><w:tr>${control(cell("C"))}</w:tr><w:tr>${control(cell("lyric"))}</w:tr></w:tbl>"))
        assertEquals(expected, lines(control(control("<w:tbl>$rows</w:tbl>"))))
        var nested = "<w:p><w:r><w:t>deep</w:t></w:r></w:p>"
        repeat(17) { nested = control(nested) }
        assertEquals(emptyList(), lines(nested))
    }

    private fun document(body: String) = "<w:document xmlns:w='urn:test'><w:body>$body</w:body></w:document>"
}
