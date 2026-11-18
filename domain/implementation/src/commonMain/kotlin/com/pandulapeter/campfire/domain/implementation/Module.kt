/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation

import com.pandulapeter.campfire.data.repository.api.ArchiveRepository
import com.pandulapeter.campfire.data.repository.api.ExportFileRepository
import com.pandulapeter.campfire.data.repository.api.ImageRepository
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SongContentRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.domain.api.export.ExportLibraryUseCase
import com.pandulapeter.campfire.domain.implementation.export.ExportLibraryUseCaseImpl
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module

@Module
@ComponentScan
object DomainModule {

    /** Built here rather than declared on the class, since the most files an export writes is a constant its tests lower. */
    @Factory
    internal fun exportLibraryUseCase(
        songRepository: SongRepository,
        songContentRepository: SongContentRepository,
        setlistRepository: SetlistRepository,
        archiveRepository: ArchiveRepository,
        exportFileRepository: ExportFileRepository,
        imageRepository: ImageRepository,
    ): ExportLibraryUseCase = ExportLibraryUseCaseImpl(
        songRepository = songRepository,
        songContentRepository = songContentRepository,
        setlistRepository = setlistRepository,
        archiveRepository = archiveRepository,
        exportFileRepository = exportFileRepository,
        imageRepository = imageRepository,
        maxFileCount = ExportLibraryUseCase.MAX_FILE_COUNT,
    )
}
