/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.api

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.Setlist
import kotlinx.coroutines.flow.Flow

interface SetlistRepository {

    val setlists: Flow<DataState<List<Setlist>>>

    /** The saved setlists, or null if they could not be read. */
    suspend fun loadSetlistsIfNeeded(): List<Setlist>?

    /** Reads the setlists directory again, which is what a rescan and an import need. */
    suspend fun rescan()

    /** Writes a new, empty setlist under a free file name and returns it. */
    suspend fun createSetlist(title: String, description: String, priority: Int): Setlist

    /**
     * Creates the file or overwrites it, and updates that one entry of the cached list. For a setlist the caller owns
     * as a whole, which is one it has just created or copied; a change to one that is already there goes through
     * [updateSetlist] or [renameSetlist]. It waits its turn among those like any other write.
     */
    suspend fun saveSetlist(setlist: Setlist)

    /**
     * Changes one setlist as a single step: read the latest, transform, write. Every change to a setlist's entries
     * goes through here, so two of them made in quick succession build on each other instead of on the same
     * snapshot. Null when there is no such setlist.
     */
    suspend fun updateSetlist(fileName: String, transform: (Setlist) -> Setlist): Setlist?

    /**
     * [updateSetlist] for the one change that may move the file: the latest version of the setlist gets [title] and
     * [description] and nothing else of it changes, and its file moves to the name the title gives it (see
     * `SetlistLocalSource.renameSetlist`), so the setlist that comes back may have a different `fileName` than the
     * one that was asked for. Null when there is no such setlist, in which case nothing is written: a setlist that
     * was deleted while its title was being typed stays deleted.
     */
    suspend fun renameSetlist(fileName: String, title: String, description: String): Setlist?

    /** See `SetlistLocalSource.parseSetlist`. */
    suspend fun parseSetlist(document: String): Setlist?

    /**
     * Writes an imported setlist under the file name it carries, suffixed until it is free unless [shouldReplace]
     * says otherwise, and returns it. The cached list is left alone, like an imported song's.
     */
    suspend fun importSetlist(setlist: Setlist, shouldReplace: Boolean): Setlist

    /** The stored document of one setlist, for exporting it unchanged. Null if it is missing. */
    suspend fun loadSetlistDocument(fileName: String): String?

    /** Waits for a change to the setlist that is being written, which would otherwise put the file back. */
    suspend fun deleteSetlist(fileName: String)
}
