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
import com.pandulapeter.campfire.data.model.domain.ImportResult
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.data.model.domain.LibraryFiles
import com.pandulapeter.campfire.data.repository.api.ArchiveRepository
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.domain.api.useCases.ImportFilesUseCase
import kotlinx.coroutines.CancellationException

class ImportFilesUseCaseImpl internal constructor(
    private val archiveRepository: ArchiveRepository,
    private val songRepository: SongRepository,
    private val setlistRepository: SetlistRepository,
) : ImportFilesUseCase {

    /**
     * Songs first, setlists second: a setlist points at songs by file name, and a song that had to be renamed to
     * avoid a collision has to be followed to its new name before the setlist next to it in the archive is written.
     */
    override suspend operator fun invoke(files: List<ImportedFile>): ImportResult {
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

        val importedSongFileNames = mutableListOf<String>()
        // Where each imported file ended up, for the setlists below. Only files that held exactly one song are in
        // here: a file that held several has no single name a setlist could have been pointing at.
        val storedSongFileNames = mutableMapOf<String, String>()
        songFiles.forEach { file ->
            val parts = file.text()?.let { ChordProSplitter.split(it) }.orEmpty()
            if (parts.isEmpty()) {
                skippedFileNames += file.name
                return@forEach
            }
            parts.forEach { part ->
                // A file that held a single song keeps the name it arrived with; the parts of a collection are named
                // after what is in them, since the file they came from named none of them.
                val song = songRepository.importSong(
                    desiredFileName = if (parts.size == 1) file.name.substringBeforeLast('.') + LibraryFiles.SONG_EXTENSION else null,
                    // The splitter trims the blank lines between the songs of a collection; the newline a text file
                    // ends with is not one of those, and without it an exported library does not import back byte for byte.
                    text = part + "\n",
                )
                importedSongFileNames += song.fileName
                if (parts.size == 1) {
                    storedSongFileNames[file.name] = song.fileName
                }
            }
        }

        val importedSetlistFileNames = mutableListOf<String>()
        var priority = (setlistRepository.loadSetlistsIfNeeded().orEmpty().maxOfOrNull { it.priority } ?: -1) + 1
        setlistFiles.forEach { file ->
            val setlist = file.text()?.let { setlistRepository.parseSetlist(it) }
            if (setlist == null) {
                skippedFileNames += file.name
                return@forEach
            }
            importedSetlistFileNames += setlistRepository.importSetlist(
                setlist.copy(
                    priority = priority++,
                    entries = setlist.entries.map { entry ->
                        entry.copy(songFileName = storedSongFileNames[entry.songFileName] ?: entry.songFileName)
                    },
                )
            ).fileName
        }

        // One read of the directory at the end rather than one cache update per file, which for a big archive would
        // cost more than the import itself.
        if (importedSongFileNames.isNotEmpty()) {
            songRepository.rescan()
        }
        if (importedSetlistFileNames.isNotEmpty()) {
            setlistRepository.rescan()
        }
        return ImportResult(
            importedSongFileNames = importedSongFileNames,
            importedSetlistFileNames = importedSetlistFileNames,
            skippedFileNames = skippedFileNames,
        )
    }

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
