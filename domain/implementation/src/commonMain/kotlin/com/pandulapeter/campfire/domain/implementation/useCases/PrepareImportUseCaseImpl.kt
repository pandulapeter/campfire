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

import com.pandulapeter.campfire.chordpro.ChordProSplitter
import com.pandulapeter.campfire.data.model.domain.ImportLimits
import com.pandulapeter.campfire.data.model.domain.ImportPlan
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.data.model.domain.LibraryFiles
import com.pandulapeter.campfire.data.model.domain.decodeLibraryText
import com.pandulapeter.campfire.data.repository.api.ArchiveRepository
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SongContentRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.domain.api.useCases.PrepareImportUseCase
import com.pandulapeter.campfire.domain.implementation.ImportPlanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.koin.core.annotation.Factory

@Factory
class PrepareImportUseCaseImpl internal constructor(
    private val archiveRepository: ArchiveRepository,
    private val songRepository: SongRepository,
    private val songContentRepository: SongContentRepository,
    private val setlistRepository: SetlistRepository,
) : PrepareImportUseCase {

    /**
     * Planned on [Dispatchers.Default] rather than wherever it was asked for, which for the view model is the main
     * thread. Decoding, splitting collections, reading every song header and folding comparable texts is computation.
     */
    override suspend operator fun invoke(files: List<ImportedFile>): ImportPlan = withContext(Dispatchers.Default) { plan(files) }

    private suspend fun plan(files: List<ImportedFile>): ImportPlan {
        val songFiles = mutableListOf<ImportedFile>()
        val setlistFiles = mutableListOf<ImportedFile>()
        val skippedFileNames = mutableListOf<String>()

        val oversizedFileNames = mutableListOf<String>()
        // What the import may still hold once everything is unpacked, shared by every archive and plain file of it.
        // The checks here are not redundant with the platform readers: they are what holds for a caller that did not
        // read its files through an ImportBudget.
        var remaining = ImportLimits.MAX_IMPORT_SIZE

        fun sort(file: ImportedFile) {
            val extension = file.name.substringAfterLast('.', "").lowercase()
            when {
                // Picked along with the songs it sits next to by a "select all" on a volume macOS has written to. It
                // carries the extension of the file it belongs to, and decoded as a song it would be a screen of binary.
                LibraryFiles.isHiddenFileName(file.name) -> skippedFileNames += file.name
                extension !in SONG_EXTENSIONS && extension != SETLIST_EXTENSION -> skippedFileNames += file.name
                file.isTooLarge || file.bytes.size > minOf(ImportLimits.MAX_TEXT_FILE_SIZE, remaining) -> oversizedFileNames += file.name
                else -> {
                    remaining -= file.bytes.size
                    if (extension == SETLIST_EXTENSION) setlistFiles += file else songFiles += file
                }
            }
        }

        files.forEach { file ->
            when {
                !file.name.endsWith(ARCHIVE_EXTENSION, ignoreCase = true) -> sort(file)
                file.isTooLarge || file.bytes.size > ImportLimits.MAX_IMPORT_SIZE -> oversizedFileNames += file.name
                else -> try {
                    archiveRepository.unpack(archive = file.bytes, maxSize = remaining).forEach(::sort)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    println("Could not unpack \"${file.name}\": ${exception.message}")
                    skippedFileNames += file.name
                }
            }
        }

        val songs = planSongs(songFiles, skippedFileNames)
        return ImportPlan(
            songs = songs,
            setlists = planSetlists(setlistFiles, skippedFileNames, ImportPlanner.plannedSongFileNames(songs)),
            skippedFileNames = skippedFileNames,
            oversizedFileNames = oversizedFileNames,
        )
    }

    /** Every song of the batch under the name its own header gives it, held against the library by [ImportPlanner]. */
    private suspend fun planSongs(files: List<ImportedFile>, skippedFileNames: MutableList<String>): List<ImportPlan.SongEntry> {
        val incoming = files.flatMap { file ->
            // The web's default dispatcher is a queue on its only thread, so yielding between files and songs keeps
            // the page painting and lets a preparation nobody awaits any more notice cancellation.
            yield()
            val parts = ChordProSplitter.split(file.bytes.decodeLibraryText())
            if (parts.isEmpty()) {
                skippedFileNames += file.name
                return@flatMap emptyList()
            }
            parts.map { part ->
                yield()
                // Every song is named by its own header, whichever file it arrived in. The name a file came under is
                // only worth anything where the song inside it declares no title: then it is what titles the song,
                // and a collection's parts do not even have that, since the file they came from named none of them.
                val fallbackTitle = if (parts.size == 1) file.name.substringBeforeLast('.') else ""
                // The splitter trims the blank lines between the songs of a collection; the newline a text file ends
                // with is not one of those, and without it an exported library does not import back byte for byte.
                val text = part + "\n"
                ImportPlanner.IncomingSong(
                    fileName = songRepository.importFileName(fallbackTitle = fallbackTitle, text = text),
                    text = text,
                    sourceFileName = file.name.takeIf { parts.size == 1 },
                )
            }
        }
        return ImportPlanner.planSongs(
            incoming = incoming,
            // The names come from the scan the app already made, so finding numbered siblings needs no directory
            // listing — on the web a listing opens every file — and only those siblings are read.
            libraryFileNames = songRepository.loadSongsIfNeeded().orEmpty().map { it.fileName },
            // Not cached: an import walks files the library has no other reason to hold on to.
            readLibraryText = { fileName -> songContentRepository.loadSongContent(fileName, shouldCache = false)?.text },
        )
    }

    /** Every setlist of the batch held against the library by [ImportPlanner]. */
    private suspend fun planSetlists(
        files: List<ImportedFile>,
        skippedFileNames: MutableList<String>,
        songFileNames: Map<String, String>,
    ): List<ImportPlan.SetlistEntry> {
        val incoming = files.mapNotNull { file ->
            yield()
            val setlist = setlistRepository.parseSetlist(file.bytes.decodeLibraryText())
            if (setlist == null) {
                skippedFileNames += file.name
                return@mapNotNull null
            }
            ImportPlanner.IncomingSetlist(setlist = setlist, sourceFileName = file.name)
        }
        return ImportPlanner.planSetlists(
            incoming = incoming,
            librarySetlists = setlistRepository.loadSetlistsIfNeeded().orEmpty(),
            songFileNames = songFileNames,
        )
    }

    private companion object {
        /** The ChordPro family plus plain text, without the dots, which is how a file name is asked for its type. */
        val SONG_EXTENSIONS = (LibraryFiles.SONG_EXTENSIONS + LibraryFiles.TEXT_EXTENSION).mapTo(mutableSetOf()) { it.removePrefix(".") }

        /** ".setlist.json" ends in this, and a plain ".json" is worth trying to parse as a setlist too. */
        const val SETLIST_EXTENSION = "json"
        val ARCHIVE_EXTENSION = LibraryFiles.ARCHIVE_EXTENSION
    }
}
