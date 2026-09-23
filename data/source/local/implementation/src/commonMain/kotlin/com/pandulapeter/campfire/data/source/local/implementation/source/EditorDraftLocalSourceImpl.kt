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

import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.data.source.local.api.EditorDraftLocalSource
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toEditorDraftDocument
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toModel
import com.pandulapeter.campfire.data.source.local.implementation.model.EditorDraftDocument
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

@Single
internal class EditorDraftLocalSourceImpl(
    private val fileStorage: FileStorage,
) : EditorDraftLocalSource {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun loadEditorDraft() = try {
        fileStorage.readText(StorageDirectory.PREFERENCES, FILE_NAME)?.let { json.decodeFromString<EditorDraftDocument>(it).toModel() }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        // Only the kind of failure goes into the log: a message is free to quote what it failed on, which here is
        // somebody's unfinished song.
        println("Could not read the editor's draft: ${exception::class.simpleName}")
        null
    }

    /**
     * A draft restored onto another phone would reopen an editor over a song that phone may not have, for text the user
     * left behind on the old one, so it stays out of the device backup the way the sync index does. The mark is set
     * after every write, since an atomic write replaces the file and the mark with it.
     */
    override suspend fun saveEditorDraft(draft: SongContent?) {
        if (draft == null) {
            fileStorage.delete(StorageDirectory.PREFERENCES, FILE_NAME)
        } else {
            fileStorage.writeText(StorageDirectory.PREFERENCES, FILE_NAME, json.encodeToString(draft.toEditorDraftDocument()))
            fileStorage.keepOutOfDeviceBackup(StorageDirectory.PREFERENCES, FILE_NAME)
        }
    }

    private companion object {
        const val FILE_NAME = "editor-draft.json"
    }
}
