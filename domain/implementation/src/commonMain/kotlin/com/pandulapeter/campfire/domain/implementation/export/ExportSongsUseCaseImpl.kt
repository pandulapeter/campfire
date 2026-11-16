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

import com.pandulapeter.campfire.data.model.library.ArchiveEntry
import com.pandulapeter.campfire.data.model.library.ExportedFile
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.repository.api.ArchiveRepository
import com.pandulapeter.campfire.data.repository.api.ExportFileRepository
import com.pandulapeter.campfire.data.repository.api.ImageRepository
import com.pandulapeter.campfire.data.repository.api.SongContentRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.domain.api.export.ExportSongsUseCase
import com.pandulapeter.campfire.domain.implementation.export.songExportFileName
import org.koin.core.annotation.Factory

@Factory
internal class ExportSongsUseCaseImpl(
    private val songRepository: SongRepository,
    private val songContentRepository: SongContentRepository,
    private val archiveRepository: ArchiveRepository,
    private val exportFileRepository: ExportFileRepository,
    private val imageRepository: ImageRepository,
) : ExportSongsUseCase {

    override suspend operator fun invoke(
        fileNames: List<String>,
        isArchive: Boolean,
        isDated: Boolean,
        rewrite: (fileName: String, text: String) -> String,
    ): ExportedFile? {
        // Read from the files rather than from the cache, which may hold a version a file has left behind since.
        // One song leaves as itself, named by its own header: a zip around a single text file would only be something
        // to unpack again. Not where a zip is what the caller's screen showed and the user picked, which is what saving
        // has to give them however many of the songs are left.
        val images = ExportedImages.of(imageRepository)
        if (fileNames.size == 1 && !isArchive) {
            val content = songContentRepository.loadSongContent(fileNames.single(), useCache = false) ?: return null
            val name = songRepository.songExportFileName(content)
            val text = rewrite(content.fileName, content.text)
            // A song showing pictures is a zip of itself and them, which opens the same in any ChordPro program: a
            // lone file would show a missing picture wherever it went.
            if (images.isNamedBy(text)) {
                return exportedArchive(
                    archiveRepository = archiveRepository,
                    exportFileRepository = exportFileRepository,
                    name = name.substringBeforeLast('.') + LibraryFiles.ARCHIVE_EXTENSION,
                    entries = listOf { ArchiveEntry(name = name, bytes = images.named(text).encodeToByteArray()) },
                    images = images,
                    isDated = isDated,
                )
            }
            return ExportedFile(name = name, mimeType = ExportedFile.TEXT_MIME_TYPE, bytes = text.encodeToByteArray())
        }
        return exportedArchive(
            archiveRepository = archiveRepository,
            exportFileRepository = exportFileRepository,
            name = ARCHIVE_NAME,
            entries = fileNames.map { fileName ->
                {
                    songContentRepository.loadSongContent(fileName, useCache = false)?.let {
                        ArchiveEntry(name = it.fileName, bytes = images.named(rewrite(it.fileName, it.text)).encodeToByteArray())
                    }
                }
            },
            images = images,
            isDated = isDated,
        )
    }

    private companion object {
        const val ARCHIVE_NAME = "campfire_songs.zip"
    }
}
