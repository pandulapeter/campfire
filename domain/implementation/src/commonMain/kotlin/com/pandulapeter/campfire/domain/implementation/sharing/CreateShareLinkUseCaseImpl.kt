/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.sharing

import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.model.share.CreateShareResult
import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import com.pandulapeter.campfire.data.repository.api.SharedLinksRepository
import com.pandulapeter.campfire.domain.api.export.ExportSetlistUseCase
import com.pandulapeter.campfire.domain.api.export.ExportSongsUseCase
import com.pandulapeter.campfire.domain.api.sharing.CreateShareLinkUseCase
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Factory

@Factory
internal class CreateShareLinkUseCaseImpl(
    private val sharedLinksRepository: SharedLinksRepository,
    private val exportSetlist: ExportSetlistUseCase,
    private val exportSongs: ExportSongsUseCase,
) : CreateShareLinkUseCase {

    override suspend operator fun invoke(
        kind: SharedLinkKind,
        fileName: String,
        title: String,
    ): CreateShareResult {
        // Read first, so that an item another device shared already gets that link back, before a copy is built.
        sharedLinksRepository.refresh()?.let { return CreateShareResult.Failed(it) }
        sharedLinksRepository.shares.first()
            .firstOrNull { it.kind == kind && LibraryFiles.isSameLibraryName(it.fileName, fileName) }
            ?.let { return CreateShareResult.Shared(it) }
        // Undated, so that the same files make the same bytes on every device, and a run that builds the copy again
        // from unchanged files finds nothing to upload.
        val copy = when (kind) {
            SharedLinkKind.SETLIST -> exportSetlist(setlistFileName = fileName, songFileNames = null, isDated = false)
            SharedLinkKind.SONG -> exportSongs(fileNames = listOf(fileName), isArchive = true, isDated = false)
        } ?: return CreateShareResult.Missing
        // Anyone opening the link takes the copy in as an import, which refuses anything larger.
        if (copy.bytes.size > ImportLimits.MAX_IMPORT_SIZE) return CreateShareResult.TooLarge
        return sharedLinksRepository.share(kind = kind, fileName = fileName, title = title, copy = copy.bytes)
    }
}
