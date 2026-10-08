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

import com.pandulapeter.campfire.data.model.domain.ExtractedDocument
import com.pandulapeter.campfire.data.model.domain.ImportLimits
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.data.model.domain.Logger
import com.pandulapeter.campfire.data.source.local.api.DocumentLocalSource
import com.pandulapeter.campfire.data.source.local.implementation.document.DocxTextExtractor
import com.pandulapeter.campfire.data.source.local.implementation.document.PdfTextExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

@Single
internal class DocumentLocalSourceImpl(
    private val logger: Logger,
) : DocumentLocalSource {
    override suspend fun extract(file: ImportedFile): ExtractedDocument? = withContext(Dispatchers.Default) {
        if (file.isTooLarge || file.bytes.size > ImportLimits.MAX_DOCUMENT_FILE_SIZE) return@withContext null
        try {
            val document = when (file.name.substringAfterLast('.').lowercase()) {
                "pdf" -> PdfTextExtractor.extract(file.bytes)
                "docx" -> DocxTextExtractor.extract(file.bytes)
                else -> return@withContext null
            }
            document.takeIf { it.pages.any { page -> page.lines.any { line -> line.spans.any { span -> span.text.isNotBlank() } } } }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logger.log("Could not read document \"${file.name}\": ${exception.message}")
            null
        }
    }
}
