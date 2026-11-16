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

import com.pandulapeter.campfire.data.model.library.ExportedFile
import com.pandulapeter.campfire.data.repository.api.ExportFileRepository
import com.pandulapeter.campfire.data.source.local.api.ExportFileLocalSource
import org.koin.core.annotation.Single

@Single
internal class ExportFileRepositoryImpl(
    private val exportFileLocalSource: ExportFileLocalSource,
) : ExportFileRepository {

    override suspend fun clear() = exportFileLocalSource.clear()

    override suspend fun create(name: String) = exportFileLocalSource.create(name)

    override suspend fun read(file: ExportedFile) = exportFileLocalSource.read(file)

    override suspend fun discard(file: ExportedFile) = exportFileLocalSource.discard(file)
}
