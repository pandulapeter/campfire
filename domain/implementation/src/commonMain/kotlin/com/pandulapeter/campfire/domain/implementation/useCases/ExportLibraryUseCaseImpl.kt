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
import org.koin.core.annotation.Factory

@Factory
class ExportLibraryUseCaseImpl internal constructor(
    private val songRepository: SongRepository,
    private val songContentRepository: SongContentRepository,
    private val setlistRepository: SetlistRepository,
    private val archiveRepository: ArchiveRepository,
) : ExportLibraryUseCase {

    /**
     * The archive mirrors the library's own layout, so that unpacking it into the library folder by hand is just as
     * good an import as the app's own.
     *
     * The archive is held against the songs folder rather than against the scan: a song the scan skipped - unreadable,
     * or too large to be a song - is not in the song list at all, and the folder is the only place that still says it
     * exists. So the archive either holds every song file there is or names the ones it does not.
     */
    override suspend operator fun invoke(): ExportLibraryUseCase.Result? {
        // A scan that failed is not an empty library. Exporting what it managed to read would hand the user an archive
        // they will file away as a backup and find out about years later.
        val songs = songRepository.loadSongsIfNeeded() ?: return null
        val setlists = setlistRepository.loadSetlistsIfNeeded() ?: return null
        val skipped = mutableListOf<String>()
        val exportedSongFileNames = mutableSetOf<String>()
        val files = buildMap {
            songs.forEach { song ->
                // Not cached: this walks the whole library, and keeping all of it in memory afterwards is no use.
                val content = songContentRepository.loadSongContent(song.fileName, shouldCache = false)
                if (content != null) {
                    put("$SONGS_DIRECTORY/${song.fileName}", content.text.encodeToByteArray())
                    exportedSongFileNames += song.fileName
                }
            }
            setlists.forEach { setlist ->
                val document = setlistRepository.loadSetlistDocument(setlist.fileName)
                if (document == null) skipped += setlist.fileName else put("$SETLISTS_DIRECTORY/${setlist.fileName}", document.encodeToByteArray())
            }
        }
        skipped.addAll(0, songRepository.loadSongFileNames().filter { it !in exportedSongFileNames }.sorted())
        if (files.isEmpty()) return null
        return ExportLibraryUseCase.Result(
            file = ExportedFile(name = ARCHIVE_NAME, mimeType = ExportedFile.ZIP_MIME_TYPE, bytes = archiveRepository.pack(files)),
            skippedFileNames = skipped,
        )
    }

    private companion object {
        const val ARCHIVE_NAME = "campfire_library.zip"
        const val SONGS_DIRECTORY = "songs"
        const val SETLISTS_DIRECTORY = "setlists"
    }
}
