/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.run

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.recovering
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLocalSource
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLock
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteFile
import com.pandulapeter.campfire.data.sync.implementation.SyncIndexEntry
import com.pandulapeter.campfire.data.sync.implementation.SyncKey

/**
 * Drops the index entries of files that are not library files, which only an index from before the remote
 * listing was filtered can hold: such a file was downloaded into the library folder, where nothing lists it.
 *
 * That copy is deleted where it is provably only a copy - still the bytes the index recorded, with the remote
 * file still there at the revision they came from. Anything else is left alone. In particular a foreign file
 * with *no* index entry may be the last copy of something, and cannot be told from a file the user put into
 * the library folder themselves.
 */
internal suspend fun withoutForeignEntries(
    index: Map<SyncKey, SyncIndexEntry>,
    listed: List<RemoteFile>,
    libraryFileLocalSource: LibraryFileLocalSource,
    libraryFileLock: LibraryFileLock,
    logger: Logger,
): Map<SyncKey, SyncIndexEntry> {
    val foreign = index.filterKeys { !it.kind.matches(it.name) }
    foreign.forEach { (key, entry) ->
        val isStillRemote = listed.any { it.kind == key.kind && it.name == key.name && it.revision == entry.remoteRevision }
        logger.recovering(
            // Tidying up is not worth a run: the entry is dropped either way, and the file stays where it is.
            describe = { "Could not remove the local copy of \"${key.path}\": ${it.message}" },
            fallback = {},
        ) {
            libraryFileLock.withLock {
                val local = libraryFileLocalSource.readLibraryFile(key.kind, key.name)
                if (isStillRemote && local != null && localHashOf(key, local) == entry.localHash) {
                    libraryFileLocalSource.deleteLibraryFile(key.kind, key.name)
                }
            }
        }
    }
    return index - foreign.keys
}
