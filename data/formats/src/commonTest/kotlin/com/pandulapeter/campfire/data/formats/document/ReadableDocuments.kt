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

import com.pandulapeter.campfire.data.model.domain.ExtractedDocument
import com.pandulapeter.campfire.data.model.domain.ImportLimits
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import kotlinx.coroutines.CancellationException

/**
 * The extractors as the import reaches them: by extension, within the document size limit, a failure or a document
 * with no readable text answered as none. What `DocumentLocalSourceImpl` does around them, kept here so that the
 * extractors' tests read the way the import sees their answers; that source's own end-to-end test is
 * `DocumentGoldenTest`, next to it.
 */
internal object ReadableDocuments {

    suspend fun extract(file: ImportedFile): ExtractedDocument? {
        if (file.isTooLarge || file.bytes.size > ImportLimits.MAX_DOCUMENT_FILE_SIZE) return null
        return try {
            val document = when (file.name.substringAfterLast('.').lowercase()) {
                "pdf" -> PdfTextExtractor.extract(file.bytes)
                "docx" -> DocxTextExtractor.extract(file.bytes)
                else -> return null
            }
            document.takeIf { it.pages.any { page -> page.lines.any { line -> line.spans.any { span -> span.text.isNotBlank() } } } }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            null
        }
    }
}
