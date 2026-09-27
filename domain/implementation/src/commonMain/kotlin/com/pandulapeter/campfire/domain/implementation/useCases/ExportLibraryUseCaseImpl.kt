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
import com.pandulapeter.campfire.data.model.domain.ImportLimits
import com.pandulapeter.campfire.data.repository.api.ArchiveRepository
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SongContentRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.domain.api.useCases.ExportLibraryUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
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
     * The songs are taken from the folder rather than from the scan: a song written since the scan (by a sync run
     * that has not rescanned yet, or put into the folder by hand) or one it could not read then is not in the song
     * list, and the folder is the only place that says it exists. Every one of them that is not too large to be a song
     * is read, so the archive holds every song file there is, and names the ones it could not read or would not open.
     *
     * The songs are read [READ_BATCH_SIZE] at a time rather than one after another, and all of it runs on
     * [Dispatchers.Default] rather than on the caller's main thread. The archive and the list of skipped files keep the
     * sorted order all the same, since the batches are put together in the order they were asked for.
     */
    override suspend operator fun invoke(): ExportLibraryUseCase.Result? = withContext(Dispatchers.Default) {
        // A scan that failed is not an empty library. Exporting what it managed to read would hand the user an archive
        // they will file away as a backup and find out about years later.
        songRepository.loadSongsIfNeeded() ?: return@withContext null
        val setlists = setlistRepository.loadSetlistsIfNeeded() ?: return@withContext null
        val songFileSizes = songRepository.loadSongFileSizes()
        val contents = songFileSizes.keys.sorted().chunked(READ_BATCH_SIZE).flatMap { batch ->
            coroutineScope {
                batch.map { fileName ->
                    async {
                        // Not opened at all when it is larger than a song can be, which the scan skips for the same reason.
                        fileName to if (songFileSizes.getValue(fileName) > ImportLimits.MAX_TEXT_FILE_SIZE) {
                            null
                        } else {
                            // Not cached: this walks the whole library, and keeping all of it in memory afterwards is no use.
                            songContentRepository.loadSongContent(fileName, shouldCache = false)
                        }
                    }
                }.awaitAll()
            }
        }
        val skipped = mutableListOf<String>()
        val files = buildMap {
            contents.forEach { (fileName, content) ->
                if (content == null) skipped += fileName else put("$SONGS_DIRECTORY/$fileName", content.text.encodeToByteArray())
            }
            setlists.forEach { setlist ->
                val document = setlistRepository.loadSetlistDocument(setlist.fileName)
                if (document == null) skipped += setlist.fileName else put("$SETLISTS_DIRECTORY/${setlist.fileName}", document.encodeToByteArray())
            }
        }
        if (files.isEmpty()) return@withContext null
        ExportLibraryUseCase.Result(
            file = ExportedFile(name = ARCHIVE_NAME, mimeType = ExportedFile.ZIP_MIME_TYPE, bytes = archiveRepository.pack(files)),
            skippedFileNames = skipped,
        )
    }

    private companion object {
        const val ARCHIVE_NAME = "campfire_library.zip"
        const val SONGS_DIRECTORY = "songs"
        const val SETLISTS_DIRECTORY = "setlists"

        /** The batch the song scan and the sync preparation read in, for the same reason: bounded concurrency. */
        const val READ_BATCH_SIZE = 64
    }
}
