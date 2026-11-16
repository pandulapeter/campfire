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
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SongContentRepository
import com.pandulapeter.campfire.domain.api.export.ExportSetlistUseCase
import com.pandulapeter.campfire.domain.implementation.export.exportFileName
import org.koin.core.annotation.Factory

@Factory
internal class ExportSetlistUseCaseImpl(
    private val setlistRepository: SetlistRepository,
    private val songContentRepository: SongContentRepository,
    private val archiveRepository: ArchiveRepository,
    private val exportFileRepository: ExportFileRepository,
) : ExportSetlistUseCase {

    /**
     * The setlist document goes in exactly as it is stored, so that the transpositions in it survive the trip, and
     * the songs it names go in next to it. A song whose file is missing is left out rather than failing the export:
     * the setlist still names it, and importing it somewhere the song does exist will find it again.
     */
    override suspend operator fun invoke(setlistFileName: String, isDated: Boolean): ExportedFile? {
        val setlist = setlistRepository.loadSetlistsIfNeeded().orEmpty().firstOrNull { it.fileName == setlistFileName } ?: return null
        val document = setlistRepository.loadSetlistDocument(setlistFileName) ?: return null
        // The entries keep their library names, accents and all: the setlist document points at its songs by file
        // name, and an import follows those names to wherever the songs land.
        // A song the setlist names twice goes in once, as an archive cannot hold two entries of one name.
        val written = mutableSetOf(setlistFileName)
        val songs = setlist.entries.map { entry ->
            suspend {
                songContentRepository.loadSongContent(entry.songFileName, useCache = false)
                    ?.takeIf { written.add(it.fileName) }
                    ?.let { ArchiveEntry(name = it.fileName, bytes = it.text.encodeToByteArray()) }
            }
        }
        return exportedArchive(
            archiveRepository = archiveRepository,
            exportFileRepository = exportFileRepository,
            // Named after its title, the one thing about a setlist the user named: the file name only follows it, and
            // is numbered where two setlists share one.
            name = exportFileName(base = setlist.title, extension = LibraryFiles.ARCHIVE_EXTENSION),
            entries = listOf<suspend () -> ArchiveEntry?>({ ArchiveEntry(name = setlistFileName, bytes = document.encodeToByteArray()) }) + songs,
            isDated = isDated,
        )
    }
}
