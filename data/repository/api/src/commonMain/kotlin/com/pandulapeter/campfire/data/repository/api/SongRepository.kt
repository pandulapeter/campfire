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
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SongContent
import kotlinx.coroutines.flow.Flow

interface SongRepository {

    val songs: Flow<DataState<List<Song>>>

    /** The songs in the library, or null if the directory could not be read. */
    suspend fun loadSongsIfNeeded(): List<Song>?

    /** See `SongLocalSource.loadSongFileSizes`: every song file in the folder with its size, read or not. Never cached. */
    suspend fun loadSongFileSizes(): Map<String, Long>

    /**
     * Reads the whole songs directory again: the app coming back to the front where the folder can be edited outside it,
     * a retry after a failed read, and a sync run cut short by something that is not an exception, which cannot say what
     * it changed. A sync run otherwise hands over the files it changed to [refresh], and an import ends with
     * [adoptImported].
     */
    suspend fun rescan()

    /**
     * Reads these files again, and only these, after something outside the repository changed them (a sync run): a file
     * that is there is put in the list in place of its old entry, and one that is gone or no longer reads drops out, as
     * a rescan would drop it. Their cached texts are invalidated. Before the library has been read at all this is a
     * [rescan], since there is no list yet to put them into.
     */
    suspend fun refresh(fileNames: Set<String>)

    /**
     * Writes the file and updates that one entry of the cached list, without rescanning the library.
     *
     * @param expectedText The text the change was built on, for a change that edits the file rather than replaces it.
     *   The file is only written while it still holds exactly that text; otherwise nothing is written, the cached text
     *   of the song is dropped, and false is returned, so that the caller can build the change again on what the file
     *   holds now. Null writes whatever the file holds, which is what an explicit save from the editor means.
     * @return False only when [expectedText] did not match.
     */
    suspend fun saveSong(content: SongContent, expectedText: String? = null): Boolean

    /** Writes [text] under a free file name derived from the title and artist, and returns the song it became. */
    suspend fun createSong(title: String, artist: String, text: String): Song

    /** See `SongLocalSource.importFileName`: the name an imported song wants, before anything is written. */
    fun importFileName(fallbackTitle: String, text: String): String

    /**
     * Writes an imported song under [fileName] and returns it, suffixing the name until it is free unless
     * [shouldReplace] says otherwise. The cached list is left alone: an import writes many files at once and ends
     * with a single [adoptImported].
     */
    suspend fun importSong(fileName: String, text: String, shouldReplace: Boolean): Song

    /**
     * Puts the songs an import wrote into the list, in place of any of the same name, in one change, and drops the
     * cached texts of those names, since a replaced file may have had one. Before the library has been read at all this
     * is a [rescan], since there is no list yet to put them into.
     */
    suspend fun adoptImported(songs: Collection<Song>)

    /**
     * See `SongLocalSource.renameSong`: moves the file to the name the song's own metadata gives it and returns it
     * under that name, or null when nothing moved. The references to the old name are the caller's to follow.
     */
    suspend fun renameSong(song: Song): Song?

    suspend fun deleteSong(fileName: String)

    /**
     * Deletes every song file in the folder, the ones that did not read included, as one change to the list. Every file
     * is attempted even after one fails; the list then keeps the ones that are still there, and the first failure is
     * thrown.
     */
    suspend fun deleteAllSongs()
}
