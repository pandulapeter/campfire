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
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import com.pandulapeter.campfire.data.source.local.api.SetlistLocalSource
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toDocument
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toModel
import com.pandulapeter.campfire.data.source.local.implementation.moveFile
import com.pandulapeter.campfire.data.source.local.implementation.model.SetlistDocumentFormat
import com.pandulapeter.campfire.data.source.local.implementation.isNamed
import com.pandulapeter.campfire.data.source.local.implementation.setlistFileName
import com.pandulapeter.campfire.data.source.local.implementation.uniqueName
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

/** Setlists are decoded on [Dispatchers.Default] for the reason the songs are parsed there (see [SongLocalSourceImpl]). */
@Single
internal class SetlistLocalSourceImpl(
    private val fileStorage: FileStorage,
) : SetlistLocalSource {

    override suspend fun loadSetlists(): List<Setlist> = withContext(Dispatchers.Default) {
        fileStorage.list(StorageDirectory.SETLISTS)
            .filter { LibraryFiles.isSetlistFileName(it.name) }
            .mapNotNull { file ->
                try {
                    if (file.size > ImportLimits.MAX_TEXT_FILE_SIZE) {
                        // Nothing the app writes is that large, so it was put there from outside, and reading it whole
                        // is what would take the app down.
                        println("Skipped the setlist \"${file.name}\": ${file.size} bytes is more than a setlist can hold.")
                        null
                    } else {
                        fileStorage.readText(StorageDirectory.SETLISTS, file.name)
                            ?.let { SetlistDocumentFormat.decode(it).toModel(file.name) }
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    // Left on disk rather than deleted: a setlist the user hand-edited into invalid JSON is theirs to fix.
                    println("Could not read the setlist \"${file.name}\": ${exception.message}")
                    null
                }
            }
    }

    override suspend fun loadSetlist(fileName: String): Setlist? = withContext(Dispatchers.Default) {
        val size = fileStorage.info(StorageDirectory.SETLISTS, fileName)?.size ?: return@withContext null
        if (size > ImportLimits.MAX_TEXT_FILE_SIZE) throw LibraryStorageException("\"$fileName\" is too large to be a setlist.")
        fileStorage.readText(StorageDirectory.SETLISTS, fileName)
            ?.let { SetlistDocumentFormat.decode(it).toModel(fileName) }
    }

    override suspend fun createSetlist(title: String, description: String, priority: Int): Setlist {
        val fileName = fileStorage.uniqueName(StorageDirectory.SETLISTS, setlistFileName(title))
        val setlist = Setlist(
            fileName = fileName,
            title = title,
            description = description,
            priority = priority,
            isArchived = false,
            entries = emptyList(),
        )
        saveSetlist(setlist)
        return setlist
    }

    override suspend fun saveSetlist(setlist: Setlist) = fileStorage.writeText(
        directory = StorageDirectory.SETLISTS,
        name = setlist.fileName,
        text = SetlistDocumentFormat.encode(setlist.toDocument()),
    )

    override suspend fun renameSetlist(setlist: Setlist, title: String): Setlist {
        val renamed = setlist.copy(title = title)
        val desired = setlistFileName(title)
        // A title that normalizes to the name the file already has (a change of capitals, or of the punctuation the
        // name never carried) moves nothing: the file is where it belongs, and the copy would only be its own.
        if (setlist.fileName.isNamed(desired)) {
            saveSetlist(renamed)
            return renamed
        }
        val fileName = fileStorage.uniqueName(StorageDirectory.SETLISTS, desired, currentName = setlist.fileName)
        fileStorage.moveFile(StorageDirectory.SETLISTS, currentName = setlist.fileName, newName = fileName) { name ->
            saveSetlist(renamed.copy(fileName = name))
        }
        return renamed.copy(fileName = fileName)
    }

    /** The file name is derived from the title rather than kept, so that an exported setlist keeps its identity. */
    override suspend fun parseSetlist(document: String): Setlist? = try {
        SetlistDocumentFormat.decode(document)
            .takeIf { it.title.isNotBlank() }
            ?.let { it.toModel(setlistFileName(it.title)) }
    } catch (exception: Exception) {
        println("Could not parse an imported setlist: ${exception.message}")
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
        .also { saveSetlist(it) }

    override suspend fun loadSetlistDocument(fileName: String) = fileStorage.readText(StorageDirectory.SETLISTS, fileName)

    override suspend fun deleteSetlist(fileName: String) = fileStorage.delete(StorageDirectory.SETLISTS, fileName)
}
