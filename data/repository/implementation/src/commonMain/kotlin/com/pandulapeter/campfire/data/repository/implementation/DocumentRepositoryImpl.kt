/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.data.repository.api.DocumentRepository
import com.pandulapeter.campfire.data.source.local.api.DocumentLocalSource
import org.koin.core.annotation.Single

@Single
internal class DocumentRepositoryImpl(private val documentLocalSource: DocumentLocalSource) : DocumentRepository {
    override suspend fun extract(file: ImportedFile) = documentLocalSource.extract(file)
}
