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

import com.pandulapeter.campfire.data.source.local.api.CoverArtLocalSource
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlinx.coroutines.CancellationException
import org.koin.core.annotation.Single

@Single
internal class CoverArtLocalSourceImpl(
    private val fileStorage: FileStorage,
) : CoverArtLocalSource {

    override suspend fun loadCoverArt(key: String) = quietly { fileStorage.readBytes(StorageDirectory.COVERS, key) }

    /**
     * A copy is kept out of the device backup like the sync index is: it can be downloaded again, and it would only
     * make the user's backup larger. The mark is set after every write, since an atomic write replaces the file.
     */
    override suspend fun saveCoverArt(key: String, bytes: ByteArray) {
        quietly {
            fileStorage.writeBytes(StorageDirectory.COVERS, key, bytes)
            fileStorage.keepOutOfDeviceBackup(StorageDirectory.COVERS, key)
        }
    }

    override suspend fun keepOnlyCoverArt(keys: Set<String>) {
        quietly {
            fileStorage.listNames(StorageDirectory.COVERS)
                .filterNot { it in keys }
                .forEach { fileStorage.delete(StorageDirectory.COVERS, it) }
        }
    }

    override suspend fun getCoverArtCacheSize() = quietly { fileStorage.list(StorageDirectory.COVERS).sumOf { it.size } }

    /** Runs [action] with every failure but a cancellation logged and answered with null, see [CoverArtLocalSource]. */
    private suspend fun <T> quietly(action: suspend () -> T): T? = try {
        action()
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        println("Could not access the cover art cache: ${exception::class.simpleName}")
        null
    }
}
