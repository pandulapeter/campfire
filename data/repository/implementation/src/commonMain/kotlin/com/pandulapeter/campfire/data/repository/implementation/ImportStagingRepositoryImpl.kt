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

import com.pandulapeter.campfire.data.model.importing.StagedFile
import com.pandulapeter.campfire.data.repository.api.ImportStagingRepository
import com.pandulapeter.campfire.data.source.local.api.ImportStagingLocalSource
import org.koin.core.annotation.Single

@Single
internal class ImportStagingRepositoryImpl(
    private val importStagingLocalSource: ImportStagingLocalSource,
) : ImportStagingRepository {

    override suspend fun deleteOtherSessions() = importStagingLocalSource.deleteOtherSessions()

    override suspend fun isIntact(file: StagedFile) = importStagingLocalSource.isIntact(file)

    override suspend fun deleteRequestsOf(files: Collection<StagedFile>) = importStagingLocalSource.deleteRequestsOf(files)
}
