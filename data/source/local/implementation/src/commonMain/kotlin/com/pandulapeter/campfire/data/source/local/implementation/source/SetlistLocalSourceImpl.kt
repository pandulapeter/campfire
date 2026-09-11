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

import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.source.local.api.SetlistLocalSource
import com.pandulapeter.campfire.data.source.local.implementation.SETLIST_EXTENSION
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toDocument
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toModel
import com.pandulapeter.campfire.data.source.local.implementation.model.SetlistDocument
import com.pandulapeter.campfire.data.source.local.implementation.isNamed
import com.pandulapeter.campfire.data.source.local.implementation.setlistFileName
import com.pandulapeter.campfire.data.source.local.implementation.uniqueName
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

internal class SetlistLocalSourceImpl(
    private val fileStorage: FileStorage,
) : SetlistLocalSource {

    override suspend fun loadSetlists(): List<Setlist> = fileStorage.list(StorageDirectory.SETLISTS)
        .filter { it.name.endsWith(SETLIST_EXTENSION, ignoreCase = true) }
        .mapNotNull { file ->
            try {
                fileStorage.readText(StorageDirectory.SETLISTS, file.name)
                    ?.let { json.decodeFromString<SetlistDocument>(it).toModel(file.name) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                // Left on disk rather than deleted: a setlist the user hand-edited into invalid JSON is theirs to fix.
                println("Could not read the setlist \"${file.name}\": ${exception.message}")
                null
            }
        }

    override suspend fun createSetlist(title: String, priority: Int): Setlist {
        val fileName = fileStorage.uniqueName(StorageDirectory.SETLISTS, setlistFileName(title))
        val setlist = Setlist(fileName = fileName, title = title, priority = priority, isArchived = false, entries = emptyList())
        saveSetlist(setlist)
        return setlist
    }

    override suspend fun saveSetlist(setlist: Setlist) = fileStorage.writeText(
        directory = StorageDirectory.SETLISTS,
        name = setlist.fileName,
        text = json.encodeToString(setlist.toDocument()),
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
        val fileName = fileStorage.uniqueName(StorageDirectory.SETLISTS, desired)
        // Written before the old one is removed, as a song's rename is, and for the same reason.
        val moved = renamed.copy(fileName = fileName)
        saveSetlist(moved)
        fileStorage.delete(StorageDirectory.SETLISTS, setlist.fileName)
        return moved
    }

    /** The file name is derived from the title rather than kept, so that an exported setlist keeps its identity. */
    override suspend fun parseSetlist(document: String): Setlist? = try {
        json.decodeFromString<SetlistDocument>(document)
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

    private companion object {
        // Pretty printed because these files are meant to survive an export and be readable (and editable) outside the app.
        val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }
    }
}
