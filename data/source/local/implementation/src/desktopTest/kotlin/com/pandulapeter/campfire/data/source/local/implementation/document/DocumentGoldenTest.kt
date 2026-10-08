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

import com.pandulapeter.campfire.chordpro.convert.ChordSheet
import com.pandulapeter.campfire.chordpro.convert.ChordSheetConverter
import com.pandulapeter.campfire.data.model.domain.ExtractedDocument
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.data.model.domain.Logger
import com.pandulapeter.campfire.data.model.domain.normalizedToNfc
import com.pandulapeter.campfire.data.source.local.implementation.source.DocumentLocalSourceImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

class DocumentGoldenTest {
    @Test
    fun campfireExportImportsAccentsAndChordsAtTheirPrintedPositions() = runBlocking {
        val document = assertNotNull(DocumentLocalSourceImpl(Logger.Standard).extract(ImportedFile("campfire.pdf", resource("campfire.pdf"))))
        val songs = ChordSheetConverter.convert(document.sheet(), String::normalizedToNfc)
        assertEquals(listOf(resource("campfire.cho").decodeToString()), songs)
    }

    @Test
    fun campfireExportImportsBothColumnsAsOneSongInReadingOrder() = runBlocking {
        val document = assertNotNull(DocumentLocalSourceImpl(Logger.Standard).extract(ImportedFile("campfire-columns.pdf", resource("campfire-columns.pdf"))))
        val expected = "{title: Column song}\n\n{start_of_verse: Verse 1}\n" +
            (1..30).joinToString("\n") { "[Am]Line ${it.toString().padStart(2, '0')} singing [F]together." } + "\n{end_of_verse}\n"
        assertEquals(listOf(expected), ChordSheetConverter.convert(document.sheet(), String::normalizedToNfc))
    }

    @Test
    fun campfireExportsOfThreeAndFourColumnsImportAsOneSongInReadingOrder() = runBlocking {
        for ((name, lineCount) in listOf("campfire-columns-3-landscape" to 28, "campfire-columns-4" to 68, "campfire-columns-4-landscape" to 44)) {
            val document = assertNotNull(DocumentLocalSourceImpl(Logger.Standard).extract(ImportedFile("$name.pdf", resource("$name.pdf"))), name)
            val expected = "{title: Column song}\n\n{start_of_verse: Verse 1}\n" +
                (1..lineCount).joinToString("\n") { "[Am]Line ${it.toString().padStart(3, '0')} [F]sung." } + "\n{end_of_verse}\n"
            assertEquals(listOf(expected), ChordSheetConverter.convert(document.sheet(), String::normalizedToNfc), name)
        }
    }

    @Test
    fun campfireExportOfFourFilledColumnsOfTheLargestTextImportsInReadingOrderWithItsChords() = runBlocking {
        val document = assertNotNull(DocumentLocalSourceImpl(Logger.Standard).extract(ImportedFile("campfire-columns-dense.pdf", resource("campfire-columns-dense.pdf"))))
        // A line wrapped in the column is imported as the lines it was printed as, each with its chord over its syllable.
        val expected = "{title: Dense}\n\n{start_of_verse: Verse 1}\n" + (1..40).joinToString("\n") {
            "[Am]Line ${it.toString().padStart(3, '0')}\nsinging all\nthe [F]words\nof a long\nline together"
        } + "\n{end_of_verse}\n"
        assertEquals(listOf(expected), ChordSheetConverter.convert(document.sheet(), String::normalizedToNfc))
    }

    @Test
    fun independentGeneratorFixturesMatchTheirChordProGoldenFiles() = runBlocking {
        for (name in listOf("python-docx.docx", "libreoffice.docx", "reportlab.pdf", "libreoffice.pdf", "chrome.pdf", "reportlab-unicode.pdf", "two-column.pdf", "songbook.pdf")) {
            val document = assertNotNull(DocumentLocalSourceImpl(Logger.Standard).extract(ImportedFile(name, resource(name))), name)
            val result = ChordSheetConverter.convert(document.sheet(), String::normalizedToNfc)
            assertEquals(resource("${name.substringBeforeLast('.')}.cho").decodeToString(), result.joinToString("{new_song}\n"), name)
            assertEquals(result, ChordSheetConverter.convert(document.sheet(), String::normalizedToNfc), name)
        }
        assertNull(DocumentLocalSourceImpl(Logger.Standard).extract(ImportedFile("protected.pdf", resource("protected.pdf"))))
    }

    private fun resource(name: String) = requireNotNull(javaClass.getResourceAsStream("/document/$name")) { name }.use { it.readBytes() }
    private fun ExtractedDocument.sheet() = ChordSheet(pages.map { page ->
        ChordSheet.Page(page.lines.map { line -> ChordSheet.Line(line.spans.map { span ->
            ChordSheet.Span(span.text, span.start, span.end, span.size, span.isBold, span.isMonospace, span.isRaised)
        }, line.isHeading) })
    })
}
