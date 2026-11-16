/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.export

import com.pandulapeter.campfire.data.model.library.ExportedFile
import com.pandulapeter.campfire.data.repository.api.ExportFileRepository
import com.pandulapeter.campfire.domain.api.export.DiscardExportedFileUseCase
import org.koin.core.annotation.Factory

@Factory
internal class DiscardExportedFileUseCaseImpl(
    private val exportFileRepository: ExportFileRepository,
) : DiscardExportedFileUseCase {

    override suspend operator fun invoke(file: ExportedFile) = exportFileRepository.discard(file)
}
