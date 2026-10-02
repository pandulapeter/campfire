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
import kotlinx.datetime.LocalDate

interface SetlistRepository {

    val setlists: Flow<DataState<List<Setlist>>>

    /** The saved setlists, or null if they could not be read. */
    suspend fun loadSetlistsIfNeeded(): List<Setlist>?

    /**
     * The file name of every setlist that names [songFileName], read from the setlist files rather than from the cache:
     * sync and an import write setlist files behind this repository's back, and the cache only catches up at the next
     * rescan. For the walks that follow a song's file name - a rename, a deletion - where a setlist the cache has not
     * seen yet would go on naming a file that is gone. A setlist the cache holds counts as well, since [updateSetlist]
     * changes one whose file cannot be decoded as the cache has it. Nothing is cached.
     *
     * Throws when the setlists cannot be listed: not knowing which setlists name the song is not knowing that none do.
     */
    suspend fun loadSetlistFileNamesNaming(songFileName: String): List<String>

    /** Reads the setlists directory again, which is what a rescan and an import need. */
    suspend fun rescan()

    /**
     * Reads these files again, and only these, after something outside the repository changed them (a sync run): a file
     * that is there is put in the list in place of its old entry, and one that is gone or no longer decodes drops out,
     * as a rescan would drop it. Before the library has been read at all this is a [rescan].
     */
    suspend fun refresh(fileNames: Set<String>)

    /** Writes a new, empty setlist under a free file name and returns it. */
    suspend fun createSetlist(title: String, description: String, date: LocalDate, isCountdownShown: Boolean): Setlist

    /**
     * Creates the file or overwrites it, and updates that one entry of the cached list. For a setlist the caller owns
     * as a whole, which is one it has just created or copied; a change to one that is already there goes through
     * [updateSetlist] or [renameSetlist]. It waits its turn among those like any other write.
     */
    suspend fun saveSetlist(setlist: Setlist)

    /**
     * Changes one setlist as a single step: read the latest from its file, transform, write. Every change to a
     * setlist's entries goes through here, so two of them made in quick succession build on each other instead of on
     * the same snapshot. Null when there is no such setlist.
     */
    suspend fun updateSetlist(fileName: String, transform: (Setlist) -> Setlist): Setlist?

    /**
     * [updateSetlist] for the one change that may move the file: the latest version of the setlist gets [title],
     * [description], [date] and [isCountdownShown] and nothing else of it changes, and its file moves to the name the title gives it (see
     * `SetlistLocalSource.renameSetlist`), so the setlist that comes back may have a different `fileName` than the
     * one that was asked for. Null when there is no such setlist, in which case nothing is written: a setlist that
     * was deleted while its title was being typed stays deleted.
     */
    suspend fun renameSetlist(fileName: String, title: String, description: String, date: LocalDate, isCountdownShown: Boolean): Setlist?

    /** See `SetlistLocalSource.parseSetlist`. */
    suspend fun parseSetlist(document: String): Setlist?

    /**
     * Writes an imported setlist under the file name it carries, suffixed until it is free unless [shouldReplace]
     * says otherwise, and returns it. The cached list is left alone, like an imported song's, until [adoptImported].
     */
    suspend fun importSetlist(setlist: Setlist, shouldReplace: Boolean): Setlist

    /**
     * Puts the setlists an import wrote into the list, in place of any of the same name, in one change. Before the
     * library has been read at all this is a [rescan].
     */
    suspend fun adoptImported(setlists: Collection<Setlist>)

    /** See `SetlistLocalSource.loadSetlistFileSizes`: every setlist file in the folder with its size, read or not. Never cached. */
    suspend fun loadSetlistFileSizes(): Map<String, Long>

    /**
     * The stored document of one setlist, for exporting it unchanged, whether it decodes or not. Null if it is missing.
     * See `SetlistLocalSource.loadSetlistDocument` for [songFileNames], which keeps only the entries naming those songs.
     */
    suspend fun loadSetlistDocument(fileName: String, songFileNames: Set<String>? = null): String?

    /** Waits for a change to the setlist that is being written, which would otherwise put the file back. */
    suspend fun deleteSetlist(fileName: String)

    /** Deletes every setlist file in the folder, the way [SongRepository.deleteAllSongs] deletes the songs. */
    suspend fun deleteAllSetlists()
}
