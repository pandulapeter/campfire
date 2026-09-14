/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.storage.secret

import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import org.koin.core.annotation.Single

/** The [SecretStore] of a platform without one worth the name: a plain file named after the key, next to the preferences. */
@Single
internal class FileSecretStore(
    private val fileStorage: FileStorage,
) : SecretStore {

    override suspend fun load(key: String) = fileStorage.readText(StorageDirectory.PREFERENCES, key)

    override suspend fun save(key: String, value: String?) = if (value == null) {
        fileStorage.delete(StorageDirectory.PREFERENCES, key)
    } else {
        fileStorage.writeText(StorageDirectory.PREFERENCES, key, value)
    }
}
