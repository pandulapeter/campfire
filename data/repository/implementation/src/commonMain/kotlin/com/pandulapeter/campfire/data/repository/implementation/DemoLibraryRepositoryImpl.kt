/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.domain.LibraryFileKind
import com.pandulapeter.campfire.data.repository.api.DemoLibraryRepository
import com.pandulapeter.campfire.data.repository.api.UserPreferencesRepository
import com.pandulapeter.campfire.data.repository.implementation.base.recovering
import com.pandulapeter.campfire.data.repository.implementation.sync.SyncKey
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLocalSource
import com.pandulapeter.campfire.data.source.remote.api.hashing.localContentHash
import org.koin.core.annotation.Single

/**
 * Records the demo files under [SyncKey.path] and with [localContentHash], which is exactly the key and the hash the
 * sync engine looks the record up by when it meets a file with no index entry: anything else and the record would
 * never be found.
 */
@Single
internal class DemoLibraryRepositoryImpl(
    private val libraryFileLocalSource: LibraryFileLocalSource,
    private val libraryFileLock: LibraryFileLock,
    private val userPreferencesRepository: UserPreferencesRepository,
) : DemoLibraryRepository {

    override suspend fun rememberDemoLibraryFiles(songFileNames: Collection<String>, setlistFileNames: Collection<String>) {
        recovering(
            describe = { "Could not remember the demo library: ${it.message}" },
            fallback = {},
        ) {
            val keys = songFileNames.map { SyncKey(LibraryFileKind.SONG, it) } + setlistFileNames.map { SyncKey(LibraryFileKind.SETLIST, it) }
            // Read under the lock the repositories write under, so the bytes recorded are the ones the import left.
            val hashes = libraryFileLock.withLock {
                keys.mapNotNull { key -> libraryFileLocalSource.readLibraryFile(key.kind, key.name)?.let { key.path to localContentHash(it) } }
            }
            if (hashes.isNotEmpty()) {
                userPreferencesRepository.updateUserPreferences { it.copy(demoLibraryContentHashes = it.demoLibraryContentHashes + hashes) }
            }
        }
    }
}
