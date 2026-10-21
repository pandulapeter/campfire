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

import com.pandulapeter.campfire.data.source.local.api.SharedLinksLocalSource
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import org.koin.core.annotation.Single

@Single
internal class SharedLinksLocalSourceImpl(
    private val fileStorage: FileStorage,
) : SharedLinksLocalSource {

    override suspend fun loadSharedLinksMirror() = fileStorage.readText(StorageDirectory.PREFERENCES, MIRROR_FILE_NAME)

    override suspend fun saveSharedLinksMirror(document: String?) = write(MIRROR_FILE_NAME, document)

    override suspend fun loadSharedLinksJournal() = fileStorage.readText(StorageDirectory.PREFERENCES, JOURNAL_FILE_NAME)

    override suspend fun saveSharedLinksJournal(document: String?) = write(JOURNAL_FILE_NAME, document)

    /**
     * Both describe the account connected here, which a device the backup is restored on has not connected, so they
     * stay out of it as the sync index does. The exclusion is a file attribute on iOS, and the atomic write replaces
     * the file, so it is set again after every write.
     */
    private suspend fun write(name: String, document: String?) {
        if (document == null) {
            fileStorage.delete(StorageDirectory.PREFERENCES, name)
        } else {
            fileStorage.writeText(StorageDirectory.PREFERENCES, name, document)
            fileStorage.keepOutOfDeviceBackup(StorageDirectory.PREFERENCES, name)
        }
    }

    private companion object {
        const val MIRROR_FILE_NAME = "shared-links.json"
        const val JOURNAL_FILE_NAME = "shared-links-journal.json"
    }
}
