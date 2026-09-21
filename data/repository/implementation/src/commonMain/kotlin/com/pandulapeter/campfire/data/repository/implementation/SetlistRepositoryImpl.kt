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

import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.implementation.base.BaseLocalDataRepository
import com.pandulapeter.campfire.data.source.local.api.SetlistLocalSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single

@Single
internal class SetlistRepositoryImpl(
    private val setlistLocalSource: SetlistLocalSource,
) : BaseLocalDataRepository<List<Setlist>>(), SetlistRepository {

    override val setlists = dataState

    /**
     * Held from reading a setlist to having its write in the cache, so that the next change reads what this one
     * wrote. The cache is the one place that is right straight after a write: anything observing [setlists] only
     * catches up a few hops later, and a file write is plenty of time for a second tap to land in between. Every
     * write to a setlist file takes it, the ones that read nothing included, since a save, a move or a deletion
     * crossing a change that is halfway through is how a setlist ends up holding the older of the two, twice in the
     * library, or back after it was deleted.
     */
    private val writeMutex = Mutex()

    override suspend fun loadDataFromLocalSource() = setlistLocalSource.loadSetlists()

    override suspend fun loadSetlistsIfNeeded() = loadDataIfNeeded()

    override suspend fun rescan() {
        reloadData()
    }

    override suspend fun createSetlist(title: String, description: String, priority: Int): Setlist {
        val setlist = setlistLocalSource.createSetlist(title = title, description = description, priority = priority)
        updateData { current -> current.orEmpty() + setlist }
        return setlist
    }

    override suspend fun saveSetlist(setlist: Setlist) = writeMutex.withLock { write(setlist) }

    override suspend fun updateSetlist(fileName: String, transform: (Setlist) -> Setlist) = writeMutex.withLock {
        latest(fileName)?.let(transform)?.also { write(it) }
    }

    override suspend fun renameSetlist(fileName: String, title: String, description: String) = writeMutex.withLock {
        latest(fileName)?.let { setlist ->
            setlistLocalSource.renameSetlist(setlist = setlist.copy(description = description), title = title).also { renamed ->
                updateData { current ->
                    current.orEmpty().filterNot { it.fileName == fileName || it.fileName == renamed.fileName } + renamed
                }
            }
        }
    }

    override suspend fun parseSetlist(document: String) = setlistLocalSource.parseSetlist(document)

    override suspend fun importSetlist(setlist: Setlist, shouldReplace: Boolean) = setlistLocalSource.importSetlist(setlist, shouldReplace)

    override suspend fun loadSetlistDocument(fileName: String) = setlistLocalSource.loadSetlistDocument(fileName)

    override suspend fun deleteSetlist(fileName: String) = writeMutex.withLock {
        setlistLocalSource.deleteSetlist(fileName)
        updateData { current -> current.orEmpty().filterNot { it.fileName == fileName } }
    }

    /** The setlist as the cache has it. Only meaningful under [writeMutex], where no write can be halfway to it. */
    private suspend fun latest(fileName: String) = loadDataIfNeeded()?.firstOrNull { it.fileName == fileName }

    /** Callers hold [writeMutex]. */
    private suspend fun write(setlist: Setlist) {
        setlistLocalSource.saveSetlist(setlist)
        updateData { current -> current.orEmpty().filterNot { it.fileName == setlist.fileName } + setlist }
    }
}
