/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.model.domain.ExportedFile
import com.pandulapeter.campfire.data.repository.api.ArchiveRepository
import com.pandulapeter.campfire.data.repository.api.SongContentRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.domain.api.useCases.ExportSongsUseCase
import com.pandulapeter.campfire.domain.implementation.songExportFileName
import org.koin.core.annotation.Factory

@Factory
internal class ExportSongsUseCaseImpl(
    private val songRepository: SongRepository,
    private val songContentRepository: SongContentRepository,
    private val archiveRepository: ArchiveRepository,
) : ExportSongsUseCase {

    override suspend operator fun invoke(fileNames: List<String>, isArchive: Boolean): ExportedFile? {
        // Read from the files rather than from the cache, which may hold a version a file has left behind since.
        val contents = fileNames.mapNotNull { songContentRepository.loadSongContent(it, useCache = false) }
        return when {
            contents.isEmpty() -> null
            // One song leaves as itself, named by its own header: a zip around a single text file would only be
            // something to unpack again. Not where a zip is what the caller's screen showed and the user picked, which
            // is what saving has to give them however many of the songs are left.
            contents.size == 1 && !isArchive -> contents.first().let {
                ExportedFile(
                    name = songRepository.songExportFileName(it),
                    mimeType = ExportedFile.TEXT_MIME_TYPE,
                    bytes = it.text.encodeToByteArray(),
                )
            }

            else -> ExportedFile(
                name = ARCHIVE_NAME,
                mimeType = ExportedFile.ZIP_MIME_TYPE,
                bytes = archiveRepository.pack(contents.associate { it.fileName to it.text.encodeToByteArray() }),
            )
        }
    }

    private companion object {
        const val ARCHIVE_NAME = "campfire_songs.zip"
    }
}
