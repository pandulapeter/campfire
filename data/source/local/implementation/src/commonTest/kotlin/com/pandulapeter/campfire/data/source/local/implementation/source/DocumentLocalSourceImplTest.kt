/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.formats.document.DocxTextExtractor
import com.pandulapeter.campfire.data.formats.zip.ZipEntry
import com.pandulapeter.campfire.data.formats.zip.ZipWriter
import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the import makes of a document around the extractors: which files are read at all, and what counts as none. */
class DocumentLocalSourceImplTest {

    private val localSource = DocumentLocalSourceImpl(Logger.Standard)

    @Test
    fun `a Word document is read by its extension, whatever its case`() = runTest {
        val document = localSource.extract(ImportedFile("Song.DOCX", docx(paragraph("Readable"))))

        assertEquals("Readable", document?.pages?.single()?.lines?.single()?.spans?.single()?.text)
    }

    @Test
    fun `a file of another extension is not read, whatever it holds`() = runTest {
        assertNull(localSource.extract(ImportedFile("song.doc", docx(paragraph("Readable")))))
        assertNull(localSource.extract(ImportedFile("song", docx(paragraph("Readable")))))
    }

    @Test
    fun `a document larger than the limit is not read`() = runTest {
        val oversized = docx(paragraph("Readable"), ZipEntry("word/media/padding.bin", Random(0).nextBytes(ImportLimits.MAX_DOCUMENT_FILE_SIZE.toInt())))
        assertTrue(oversized.size > ImportLimits.MAX_DOCUMENT_FILE_SIZE)
        // Readable as such, so that it is the size alone that keeps the import from reading it.
        assertEquals("Readable", DocxTextExtractor.extract(oversized).pages.single().lines.single().spans.single().text)

        assertNull(localSource.extract(ImportedFile("song.docx", oversized)))
        assertNull(localSource.extract(ImportedFile("song.docx", docx(paragraph("Readable")), isTooLarge = true)))
    }

    @Test
    fun `a document with no readable text is none`() = runTest {
        assertNull(localSource.extract(ImportedFile("empty.docx", docx("<w:p/>"))))
        assertNull(localSource.extract(ImportedFile("blank.docx", docx(paragraph("   ")))))
    }

    @Test
    fun `a document the extractor fails on is none`() = runTest {
        assertNull(localSource.extract(ImportedFile("song.pdf", "not a pdf".encodeToByteArray())))
        assertNull(localSource.extract(ImportedFile("song.docx", "%PDF-1.7".encodeToByteArray())))
    }

    private fun paragraph(text: String) = "<w:p><w:r><w:t xml:space='preserve'>$text</w:t></w:r></w:p>"

    private fun docx(body: String, vararg extraEntries: ZipEntry) = ZipWriter.write(
        listOf(ZipEntry("word/document.xml", "<w:document xmlns:w='urn:test'><w:body>$body</w:body></w:document>".encodeToByteArray())) +
            extraEntries,
    )
}
