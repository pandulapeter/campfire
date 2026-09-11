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
import com.pandulapeter.campfire.data.model.domain.ImportPlan
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.data.model.domain.LibraryFiles
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.repository.api.ArchiveRepository
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SongContentRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.domain.api.useCases.PrepareImportUseCase
import kotlinx.coroutines.CancellationException

class PrepareImportUseCaseImpl internal constructor(
    private val archiveRepository: ArchiveRepository,
    private val songRepository: SongRepository,
    private val songContentRepository: SongContentRepository,
    private val setlistRepository: SetlistRepository,
) : PrepareImportUseCase {

    override suspend operator fun invoke(files: List<ImportedFile>): ImportPlan {
        val songFiles = mutableListOf<ImportedFile>()
        val setlistFiles = mutableListOf<ImportedFile>()
        val skippedFileNames = mutableListOf<String>()

        fun sort(file: ImportedFile) = when (file.name.substringAfterLast('.', "").lowercase()) {
            in SONG_EXTENSIONS -> songFiles.add(file)
            SETLIST_EXTENSION -> setlistFiles.add(file)
            else -> skippedFileNames.add(file.name)
        }

        files.forEach { file ->
            if (file.name.endsWith(ARCHIVE_EXTENSION, ignoreCase = true)) {
                try {
                    archiveRepository.unpack(file.bytes).forEach(::sort)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    println("Could not unpack \"${file.name}\": ${exception.message}")
                    skippedFileNames += file.name
                }
            } else {
                sort(file)
            }
        }

        return ImportPlan(
            songs = planSongs(songFiles, skippedFileNames),
            setlists = planSetlists(setlistFiles, skippedFileNames),
            skippedFileNames = skippedFileNames,
        )
    }

    /**
     * What the library will hold under each name once the plan is applied, so that an archive carrying the same song
     * twice answers for the second copy the same way the library answers for the first. A conflicting name is left
     * out of it: whatever the user decides, the name either keeps the song that is there or is given to the incoming
     * one, and neither is known here.
     */
    private suspend fun planSongs(files: List<ImportedFile>, skippedFileNames: MutableList<String>): List<ImportPlan.SongEntry> {
        val plannedTexts = mutableMapOf<String, String>()
        return files.flatMap { file ->
            val parts = file.text()?.let { ChordProSplitter.split(it) }.orEmpty()
            if (parts.isEmpty()) {
                skippedFileNames += file.name
                return@flatMap emptyList()
            }
            parts.map { part ->
                // Every song is named by its own header, whichever file it arrived in. The name a file came under is
                // only worth anything where the song inside it declares no title: then it is what titles the song,
                // and a collection's parts do not even have that, since the file they came from named none of them.
                val fallbackTitle = if (parts.size == 1) file.name.substringBeforeLast('.') else ""
                // The splitter trims the blank lines between the songs of a collection; the newline a text file ends
                // with is not one of those, and without it an exported library does not import back byte for byte.
                val text = part + "\n"
                val fileName = songRepository.importFileName(fallbackTitle = fallbackTitle, text = text)
                // Not cached: an import walks files the library has no other reason to hold on to.
                val existingText = plannedTexts[fileName] ?: songContentRepository.loadSongContent(fileName, shouldCache = false)?.text
                val status = when {
                    existingText == null -> ImportPlan.Status.NEW
                    existingText == text -> ImportPlan.Status.IDENTICAL
                    else -> ImportPlan.Status.CONFLICTING
                }
                if (status == ImportPlan.Status.NEW) {
                    plannedTexts[fileName] = text
                }
                ImportPlan.SongEntry(
                    fileName = fileName,
                    text = text,
                    status = status,
                    sourceFileName = file.name.takeIf { parts.size == 1 },
                )
            }
        }
    }

    /**
     * A setlist is compared by what is in it rather than by its stored document, which carries a priority this
     * import assigns itself and would therefore never match. Whether it is archived does count, since that is the
     * user's own answer about the setlist and travels with the file the way its title does. The entries are held against the names they arrived
     * with: a song that had to be renamed is followed when the plan is applied, and a setlist pointing at one is a
     * different setlist anyway.
     */
    private suspend fun planSetlists(files: List<ImportedFile>, skippedFileNames: MutableList<String>): List<ImportPlan.SetlistEntry> {
        val existingSetlists = setlistRepository.loadSetlistsIfNeeded().orEmpty().associateBy { it.fileName }
        val plannedSetlists = mutableMapOf<String, Setlist>()
        return files.mapNotNull { file ->
            val setlist = file.text()?.let { setlistRepository.parseSetlist(it) }
            if (setlist == null) {
                skippedFileNames += file.name
                return@mapNotNull null
            }
            val existing = plannedSetlists[setlist.fileName] ?: existingSetlists[setlist.fileName]
            val status = when {
                existing == null -> ImportPlan.Status.NEW
                existing.holdsTheSameAs(setlist) -> ImportPlan.Status.IDENTICAL
                else -> ImportPlan.Status.CONFLICTING
            }
            if (status == ImportPlan.Status.NEW) {
                plannedSetlists[setlist.fileName] = setlist
            }
            ImportPlan.SetlistEntry(fileName = setlist.fileName, setlist = setlist, status = status)
        }
    }

    private fun Setlist.holdsTheSameAs(other: Setlist) = title == other.title && isArchived == other.isArchived && entries == other.entries

    /** Null when the bytes are not UTF-8 text, which is the one thing every file the app accepts has to be. */
    private fun ImportedFile.text() = try {
        bytes.decodeToString(throwOnInvalidSequence = true).removePrefix(BYTE_ORDER_MARK)
    } catch (exception: Exception) {
        println("Could not decode \"$name\": ${exception.message}")
        null
    }

    private companion object {
        /** The ChordPro family plus plain text, without the dots, which is how a file name is asked for its type. */
        val SONG_EXTENSIONS = (LibraryFiles.SONG_EXTENSIONS + LibraryFiles.TEXT_EXTENSION).mapTo(mutableSetOf()) { it.removePrefix(".") }

        /** ".setlist.json" ends in this, and a plain ".json" is worth trying to parse as a setlist too. */
        const val SETLIST_EXTENSION = "json"
        val ARCHIVE_EXTENSION = LibraryFiles.ARCHIVE_EXTENSION
        const val BYTE_ORDER_MARK = "\uFEFF"
    }
}
