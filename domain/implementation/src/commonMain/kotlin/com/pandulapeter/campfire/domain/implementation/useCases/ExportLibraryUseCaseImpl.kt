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
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SongContentRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.domain.api.useCases.ExportLibraryUseCase

class ExportLibraryUseCaseImpl internal constructor(
    private val songRepository: SongRepository,
    private val songContentRepository: SongContentRepository,
    private val setlistRepository: SetlistRepository,
    private val archiveRepository: ArchiveRepository,
) : ExportLibraryUseCase {

    /**
     * The archive mirrors the library's own layout, so that unpacking it into the library folder by hand is just as
     * good an import as the app's own.
     */
    override suspend operator fun invoke(): ExportedFile? {
        val files = buildMap {
            songRepository.loadSongsIfNeeded().orEmpty().forEach { song ->
                // Not cached: this walks the whole library, and keeping all of it in memory afterwards is no use.
                songContentRepository.loadSongContent(song.fileName, shouldCache = false)
                    ?.let { put("$SONGS_DIRECTORY/${song.fileName}", it.text.encodeToByteArray()) }
            }
            setlistRepository.loadSetlistsIfNeeded().orEmpty().forEach { setlist ->
                setlistRepository.loadSetlistDocument(setlist.fileName)
                    ?.let { put("$SETLISTS_DIRECTORY/${setlist.fileName}", it.encodeToByteArray()) }
            }
        }
        return if (files.isEmpty()) {
            null
        } else {
            ExportedFile(name = ARCHIVE_NAME, mimeType = ExportedFile.ZIP_MIME_TYPE, bytes = archiveRepository.pack(files))
        }
    }

    private companion object {
        const val ARCHIVE_NAME = "campfire_library.zip"
        const val SONGS_DIRECTORY = "songs"
        const val SETLISTS_DIRECTORY = "setlists"
    }
}
