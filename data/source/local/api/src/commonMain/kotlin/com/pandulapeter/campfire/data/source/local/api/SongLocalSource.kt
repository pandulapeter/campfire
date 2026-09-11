/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.api

import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SongContent

interface SongLocalSource {

    /**
     * Every `.cho` file in the library, with the metadata parsed out of it. Unreadable files are skipped.
     *
     * Reading a whole library is the slowest thing the app does at start, so the songs arrive in batches rather than
     * all at the end: [onProgress] is called with everything read so far, once per batch and never with the last one
     * (which is the returned list). An implementation is free to hand over one batch of everything, but not to leave
     * the caller with nothing until it has finished.
     */
    suspend fun loadSongs(onProgress: (List<Song>) -> Unit): List<Song>

    /** The metadata of a single file, so that saving one song does not have to rescan the whole library. */
    suspend fun loadSong(fileName: String): Song?

    /** Null if the file does not exist. */
    suspend fun loadSongContent(fileName: String): SongContent?

    /** Creates the file or overwrites it. */
    suspend fun saveSongContent(content: SongContent)

    /**
     * Writes [text] under a file name derived from the title and artist, suffixed until it is free, and returns the
     * song it became. File naming is the storage layer's business, so callers only supply the content.
     */
    suspend fun createSong(title: String, artist: String, text: String): Song

    /**
     * The name [text] wants in the library: [desiredFileName] when the import has an original name worth keeping,
     * otherwise one derived from the title and artist in the text itself. Nothing is read or written, and a name
     * that is already taken is returned as it is - deciding what to do about that is the caller's business.
     */
    fun importFileName(desiredFileName: String?, text: String): String

    /**
     * Writes [text] under [fileName] and returns the song it became. The name is suffixed until it is free unless
     * [shouldReplace] says otherwise, which is the one way an import ever overwrites a file the library already has.
     */
    suspend fun importSong(fileName: String, text: String, shouldReplace: Boolean): Song

    suspend fun deleteSong(fileName: String)

    suspend fun exists(fileName: String): Boolean
}
