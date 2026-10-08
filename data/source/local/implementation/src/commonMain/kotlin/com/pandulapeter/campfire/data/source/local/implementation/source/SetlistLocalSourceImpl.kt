/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.model.domain.ImportLimits
import com.pandulapeter.campfire.data.model.domain.LibraryFiles
import com.pandulapeter.campfire.data.model.domain.Logger
import com.pandulapeter.campfire.data.model.domain.ParsedSetlist
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import com.pandulapeter.campfire.data.source.local.api.SetlistLocalSource
import com.pandulapeter.campfire.data.source.local.implementation.mapper.isDated
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toDocument
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toModel
import com.pandulapeter.campfire.data.source.local.implementation.moveFile
import com.pandulapeter.campfire.data.source.local.implementation.model.SetlistDocument
import com.pandulapeter.campfire.data.source.local.implementation.model.SetlistDocumentFormat
import com.pandulapeter.campfire.data.source.local.implementation.isNamed
import com.pandulapeter.campfire.data.source.local.implementation.setlistFileName
import com.pandulapeter.campfire.data.source.local.implementation.uniqueName
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.BatchRead
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.koin.core.annotation.Single
import kotlin.time.Clock

/** Setlists are decoded on [Dispatchers.Default] for the reason the songs are parsed there (see [SongLocalSourceImpl]). */
@Single
internal class SetlistLocalSourceImpl(
    private val fileStorage: FileStorage,
    private val logger: Logger,
) : SetlistLocalSource {

    override suspend fun loadSetlists(): List<ParsedSetlist> = withContext(Dispatchers.Default) {
        val files = fileStorage.list(StorageDirectory.SETLISTS)
            .filter { LibraryFiles.isSetlistFileName(it.name) }
            .filter { file ->
                (file.size <= ImportLimits.MAX_TEXT_FILE_SIZE).also { isReadable ->
                    // Nothing the app writes is that large, so it was put there from outside, and reading it whole is
                    // what would take the app down.
                    if (!isReadable) logger.log("Skipped the setlist \"${file.name}\": ${file.size} bytes is more than a setlist can hold.")
                }
            }
        // Setlists are few, so they are read in one batch rather than in the song scan's bounded ones.
        files.zip(fileStorage.readTexts(StorageDirectory.SETLISTS, files.map { it.name })).mapNotNull { (file, answer) ->
            try {
                when (answer) {
                    is BatchRead.Text -> SetlistDocumentFormat.decode(answer.text).let { document ->
                        ParsedSetlist(setlist = document.toModel(file.name, size = file.size, undatedDay = today()), isDated = document.isDated)
                    }
                    BatchRead.Missing -> null
                    is BatchRead.Failed -> throw answer.cause
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                // Left on disk rather than deleted: a setlist the user hand-edited into invalid JSON is theirs to fix.
                logger.log("Could not read the setlist \"${file.name}\": ${exception.message}")
                null
            }
        }
    }

    override suspend fun loadSetlist(fileName: String): Setlist? = withContext(Dispatchers.Default) {
        val size = fileStorage.info(StorageDirectory.SETLISTS, fileName)?.size ?: return@withContext null
        if (size > ImportLimits.MAX_TEXT_FILE_SIZE) throw LibraryStorageException("\"$fileName\" is too large to be a setlist.")
        fileStorage.readText(StorageDirectory.SETLISTS, fileName)
            ?.let { SetlistDocumentFormat.decode(it).toDatedModel(fileName, size = size) }
    }

    /**
     * Every setlist has a day, so a file that names none - written before there were dates, by hand, or by an older
     * version on another device - is given the day it is first read on and saved with it at once, rather than being
     * read as today again tomorrow. Only [loadSetlist] does this, since its callers hold the repository's locks: the
     * bulk read would write back the text it read a moment ago over whatever replaced it since. The write announces no
     * library change and starts no sync run of its own; the next run carries it. A write that fails leaves the setlist
     * dated in memory only, to be tried again by the next read.
     */
    private suspend fun SetlistDocument.toDatedModel(fileName: String, size: Long): Setlist {
        val setlist = toModel(fileName, size = size, undatedDay = today())
        if (isDated) return setlist
        return try {
            saveSetlist(setlist)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logger.log("Could not write the day into the setlist \"$fileName\": ${exception.message}")
            setlist
        }
    }

    override suspend fun createSetlist(title: String, description: String, date: LocalDate, isCountdownShown: Boolean): Setlist {
        val fileName = fileStorage.uniqueName(StorageDirectory.SETLISTS, setlistFileName(title))
        val setlist = Setlist(
            fileName = fileName,
            title = title,
            description = description,
            date = date,
            isCountdownShown = isCountdownShown,
            isArchived = false,
            entries = emptyList(),
            size = 0,
        )
        return saveSetlist(setlist)
    }

    /**
     * Returns [setlist] as the file now holds it: carrying the size of what was written, and naming each song once.
     * A model built in memory can name one twice - a song renamed onto the name of an entry whose file this device
     * does not have, see `RenameSongFileUseCaseImpl` - while the document never can (`toDocument`), and the caller
     * caches what comes back from here. The first mention wins, its transposition with it, which is the rule a file
     * edited by hand is read by (`toModel`).
     */
    override suspend fun saveSetlist(setlist: Setlist): Setlist {
        val deduplicated = setlist.copy(entries = setlist.entries.distinctBy { it.songFileName })
        val text = SetlistDocumentFormat.encode(deduplicated.toDocument())
        fileStorage.writeText(directory = StorageDirectory.SETLISTS, name = deduplicated.fileName, text = text)
        // Every platform writes the text as UTF-8 with nothing before it, so this is the file's size without asking
        // the storage for it again.
        return deduplicated.copy(size = text.encodeToByteArray().size.toLong())
    }

    override suspend fun renameSetlist(setlist: Setlist, title: String): Setlist {
        val renamed = setlist.copy(title = title)
        val desired = setlistFileName(title)
        // A title that normalizes to the name the file already has (a change of capitals, or of the punctuation the
        // name never carried) moves nothing: the file is where it belongs, and the copy would only be its own. Nor does
        // a title whose name is the one the stored title gives: a save of the edit dialog that only changed the
        // description, or nothing, is no reason to move a file named by hand or by an older rule, and a move reaches
        // every synced device as a deletion and a new file.
        if (setlist.fileName.isNamed(desired) || setlistFileName(setlist.title) == desired) {
            return saveSetlist(renamed)
        }
        val fileName = fileStorage.uniqueName(StorageDirectory.SETLISTS, desired, currentName = setlist.fileName)
        var moved = renamed
        fileStorage.moveFile(StorageDirectory.SETLISTS, currentName = setlist.fileName, newName = fileName) { name ->
            moved = saveSetlist(renamed.copy(fileName = name))
        }
        return moved.copy(fileName = fileName)
    }

    /** The file name is derived from the title rather than kept, so that an exported setlist keeps its identity. */
    override suspend fun parseSetlist(document: String): ParsedSetlist? = try {
        SetlistDocumentFormat.decode(document)
            .takeIf { it.title.isNotBlank() }
            ?.let { ParsedSetlist(setlist = it.toModel(setlistFileName(it.title), size = 0, undatedDay = today()), isDated = it.isDated) }
    } catch (exception: Exception) {
        logger.log("Could not parse an imported setlist: ${exception.message}")
        null
    }

    override suspend fun importSetlist(setlist: Setlist, shouldReplace: Boolean): Setlist = setlist
        .copy(
            fileName = if (shouldReplace) {
                setlist.fileName
            } else {
                fileStorage.uniqueName(StorageDirectory.SETLISTS, setlist.fileName)
            },
        )
        .let { saveSetlist(it) }

    override suspend fun loadSetlistFileSizes() = fileStorage.list(StorageDirectory.SETLISTS)
        .filter { LibraryFiles.isSetlistFileName(it.name) }
        .associate { it.name to it.size }

    override suspend fun loadSetlistDocument(fileName: String, songFileNames: Set<String>?): String? {
        val text = fileStorage.readText(StorageDirectory.SETLISTS, fileName) ?: return null
        if (songFileNames == null) return text
        val document = SetlistDocumentFormat.decode(text)
        return SetlistDocumentFormat.encode(document.copy(songs = document.songs.filter { it.file in songFileNames }))
    }

    override suspend fun deleteSetlist(fileName: String) = fileStorage.delete(StorageDirectory.SETLISTS, fileName)

    private fun today() = Clock.System.todayIn(TimeZone.currentSystemDefault())
}
