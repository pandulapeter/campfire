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

    /** Reads the songs directory again, which is what a refresh and an import need. */
    suspend fun rescan()

    /** Writes the file and updates that one entry of the cached list, without rescanning the library. */
    suspend fun saveSong(content: SongContent)

    /** Writes [text] under a free file name derived from the title and artist, and returns the song it became. */
    suspend fun createSong(title: String, artist: String, text: String): Song

    /** See `SongLocalSource.importFileName`: the name an imported song wants, before anything is written. */
    fun importFileName(desiredFileName: String?, text: String): String

    /**
     * Writes an imported song under [fileName] and returns it, suffixing the name until it is free unless
     * [shouldReplace] says otherwise. The cached list is left alone: an import writes many files at once and ends
     * with a single [rescan].
     */
    suspend fun importSong(fileName: String, text: String, shouldReplace: Boolean): Song

    suspend fun deleteSong(fileName: String)
}
