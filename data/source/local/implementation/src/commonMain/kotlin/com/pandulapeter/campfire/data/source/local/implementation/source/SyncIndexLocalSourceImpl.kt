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

import com.pandulapeter.campfire.data.source.local.api.SyncIndexLocalSource
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import org.koin.core.annotation.Single

@Single
internal class SyncIndexLocalSourceImpl(
    private val fileStorage: FileStorage,
) : SyncIndexLocalSource {

    override suspend fun loadSyncIndex() = fileStorage.readText(StorageDirectory.PREFERENCES, INDEX_FILE_NAME)

    /**
     * The index records what the last run saw from this device. Restored onto another one, where no account is
     * connected yet, it would be a statement about a folder nobody can check, so it stays out of the device backup
     * the way the credentials do; without it the first run there compares the two sides by content.
     */
    override suspend fun saveSyncIndex(document: String?) {
        write(INDEX_FILE_NAME, document)
        if (document != null) {
            fileStorage.keepOutOfDeviceBackup(StorageDirectory.PREFERENCES, INDEX_FILE_NAME)
        }
    }

    override suspend fun isForgettingCredentialsOwed() = fileStorage.exists(StorageDirectory.PREFERENCES, FORGETTING_OWED_FILE_NAME)

    override suspend fun setForgettingCredentialsOwed(isOwed: Boolean) {
        write(FORGETTING_OWED_FILE_NAME, if (isOwed) "" else null)
        if (isOwed) {
            fileStorage.keepOutOfDeviceBackup(StorageDirectory.PREFERENCES, FORGETTING_OWED_FILE_NAME)
        }
    }

    private suspend fun write(name: String, document: String?) = if (document == null) {
        fileStorage.delete(StorageDirectory.PREFERENCES, name)
    } else {
        fileStorage.writeText(StorageDirectory.PREFERENCES, name, document)
    }

    private companion object {
        const val INDEX_FILE_NAME = "sync-index.json"
        const val FORGETTING_OWED_FILE_NAME = "sync-credentials-forget-pending"
    }
}
