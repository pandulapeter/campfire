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

import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.repository.api.ImportDownloadRepository
import com.pandulapeter.campfire.data.source.remote.api.ImportDownloadRemoteSource
import org.koin.core.annotation.Single

@Single
internal class ImportDownloadRepositoryImpl(
    private val importDownloadRemoteSource: ImportDownloadRemoteSource,
) : ImportDownloadRepository {

    override suspend fun download(url: String) = importDownloadRemoteSource.download(url = url, maxSizeBytes = ImportLimits.MAX_IMPORT_SIZE)
}
