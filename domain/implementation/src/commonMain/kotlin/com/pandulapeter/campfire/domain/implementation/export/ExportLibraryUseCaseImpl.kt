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

import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.library.ArchiveEntry
import com.pandulapeter.campfire.data.model.song.SongContent
import com.pandulapeter.campfire.data.repository.api.ArchiveRepository
import com.pandulapeter.campfire.data.repository.api.ExportFileRepository
import com.pandulapeter.campfire.data.repository.api.ImageRepository
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SongContentRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.domain.api.export.ExportLibraryUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Factory

@Factory
internal class ExportLibraryUseCaseImpl(
    private val songRepository: SongRepository,
    private val songContentRepository: SongContentRepository,
    private val setlistRepository: SetlistRepository,
    private val archiveRepository: ArchiveRepository,
    private val exportFileRepository: ExportFileRepository,
    private val imageRepository: ImageRepository,
) : ExportLibraryUseCase {

    /**
     * The archive mirrors the library's own layout, so that unpacking it into the library folder by hand is just as
     * good an import as the app's own.
     *
     * The songs are taken from the folder rather than from the scan: a song written since the scan (by a sync run
     * that has not rescanned yet, or put into the folder by hand) or one it could not read then is not in the song
     * list, and the folder is the only place that says it exists. Every one of them that is not too large to be a song
     * is read, so the archive holds every song file there is, and names the ones it could not read or would not open.
     * The setlists are taken from the folder too, and exported as they are stored rather than as the scan decoded
     * them: a setlist hand-edited into JSON that no longer decodes is skipped by the scan and left on disk as the
     * user's to fix, and a backup is exactly where it should be kept.
     *
     * The archive is written into a file as it is read, never held whole: the songs are read [READ_BATCH_SIZE] at a time
     * rather than one after another, each batch when the archive reaches it and let go of as its songs are written, and
     * all of it runs on [Dispatchers.Default] rather than on the caller's main thread. The archive and the list of
     * skipped files keep the sorted order all the same.
     */
    override suspend operator fun invoke(): ExportLibraryUseCase.Result? = withContext(Dispatchers.Default) {
        // A scan that failed is not an empty library. Exporting what it managed to read would hand the user an archive
        // they will file away as a backup and find out about years later.
        songRepository.loadSongsIfNeeded() ?: return@withContext null
        setlistRepository.loadSetlistsIfNeeded() ?: return@withContext null
        val songFileSizes = songRepository.loadSongFileSizes()
        val setlistFileSizes = setlistRepository.loadSetlistFileSizes()
        if (songFileSizes.isEmpty() && setlistFileSizes.isEmpty()) return@withContext null
        val skipped = mutableListOf<String>()
        val images = ExportedImages.of(imageRepository)
        val songNames = songFileSizes.keys.sorted()
        val batch = mutableMapOf<String, SongContent?>()
        val songs = songNames.mapIndexed { index, fileName ->
            suspend {
                if (index % READ_BATCH_SIZE == 0) {
                    batch.clear()
                    batch.putAll(readSongs(songNames.subList(index, minOf(index + READ_BATCH_SIZE, songNames.size)), songFileSizes))
                }
                val content = batch.remove(fileName)
                if (content == null) skipped += fileName
                content?.let { ArchiveEntry(name = "$SONGS_DIRECTORY/$fileName", bytes = images.named(it.text).encodeToByteArray()) }
            }
        }
        val setlists = setlistFileSizes.keys.sorted().map { fileName ->
            suspend {
                val document = if (setlistFileSizes.getValue(fileName) > ImportLimits.MAX_TEXT_FILE_SIZE) {
                    null
                } else {
                    setlistRepository.loadSetlistDocument(fileName)
                }
                if (document == null) skipped += fileName
                document?.let { ArchiveEntry(name = "$SETLISTS_DIRECTORY/$fileName", bytes = it.encodeToByteArray()) }
            }
        }
        val file = exportedArchive(
            archiveRepository = archiveRepository,
            exportFileRepository = exportFileRepository,
            name = ARCHIVE_NAME,
            entries = songs + setlists,
            images = images,
            isDated = true,
        ) ?: return@withContext null
        ExportLibraryUseCase.Result(file = file, skippedFileNames = skipped)
    }

    /** [fileNames] read in parallel, each answered with null where it is larger than a song can be or could not be read. */
    private suspend fun readSongs(fileNames: List<String>, sizes: Map<String, Long>) = coroutineScope {
        fileNames.map { fileName ->
            async {
                // Not opened at all when it is larger than a song can be, which the scan skips for the same reason.
                fileName to if (sizes.getValue(fileName) > ImportLimits.MAX_TEXT_FILE_SIZE) {
                    null
                } else {
                    // Past the cache: an export has to hold what is on disk, and keeping the whole library in memory
                    // afterwards would be no use.
                    songContentRepository.loadSongContent(fileName, useCache = false)
                }
            }
        }.awaitAll().toMap()
    }

    private companion object {
        const val ARCHIVE_NAME = "campfire_library.zip"
        const val SONGS_DIRECTORY = "songs"
        const val SETLISTS_DIRECTORY = "setlists"

        /** The batch the song scan and the sync preparation read in, for the same reason: bounded concurrency. */
        const val READ_BATCH_SIZE = 64
    }
}
