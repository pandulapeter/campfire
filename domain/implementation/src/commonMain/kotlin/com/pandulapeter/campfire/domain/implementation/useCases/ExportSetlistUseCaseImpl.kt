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
import com.pandulapeter.campfire.data.model.domain.LibraryFiles
import com.pandulapeter.campfire.data.repository.api.ArchiveRepository
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SongContentRepository
import com.pandulapeter.campfire.domain.api.useCases.ExportSetlistUseCase
import com.pandulapeter.campfire.domain.implementation.exportFileName

class ExportSetlistUseCaseImpl internal constructor(
    private val setlistRepository: SetlistRepository,
    private val songContentRepository: SongContentRepository,
    private val archiveRepository: ArchiveRepository,
) : ExportSetlistUseCase {

    /**
     * The setlist document goes in exactly as it is stored, so that the transpositions in it survive the trip, and
     * the songs it names go in next to it. A song whose file is missing is left out rather than failing the export:
     * the setlist still names it, and importing it somewhere the song does exist will find it again.
     */
    override suspend operator fun invoke(setlistFileName: String): ExportedFile? {
        val setlist = setlistRepository.loadSetlistsIfNeeded().orEmpty().firstOrNull { it.fileName == setlistFileName } ?: return null
        val document = setlistRepository.loadSetlistDocument(setlistFileName) ?: return null
        // The entries keep their library names, accents and all: the setlist document points at its songs by file
        // name, and an import follows those names to wherever the songs land.
        val files = buildMap {
            put(setlistFileName, document.encodeToByteArray())
            setlist.entries.forEach { entry ->
                songContentRepository.loadSongContent(entry.songFileName, shouldCache = false)
                    ?.let { put(it.fileName, it.text.encodeToByteArray()) }
            }
        }
        return ExportedFile(
            name = exportFileName(
                base = setlistFileName.removeSuffix(LibraryFiles.SETLIST_EXTENSION),
                extension = LibraryFiles.ARCHIVE_EXTENSION,
            ),
            mimeType = ExportedFile.ZIP_MIME_TYPE,
            bytes = archiveRepository.pack(files),
        )
    }
}
