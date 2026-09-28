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

import com.pandulapeter.campfire.data.model.domain.SongContent
import kotlinx.coroutines.flow.Flow

/**
 * The text of the songs that have been opened, kept in memory so that paging back and forth in a setlist does not
 * read the same files over and over. The list of songs does not carry the text, see `SongRepository`.
 */
interface SongContentRepository {

    /**
     * Every [invalidate], as the file name it named, or null for every song. Whoever holds a copy of a text outside
     * this cache drops or re-reads it: the invalidation means the file may have changed underneath that copy, and a
     * write built on it would put back what somebody else (a sync run, another device) has just replaced.
     *
     * A notification rather than a queue: nothing is replayed to a late collector, and a collector that falls far
     * behind may miss some, so it must not be used to count writes.
     */
    val invalidations: Flow<String?>

    /**
     * Null if the file does not exist or could not be read.
     *
     * @param useCache False reads the file itself, neither answering from the cache nor adding to it: for a read that
     *   has to see what is on disk right now and walks much of the library, such as an export or the import's check for
     *   a song that is already there. Answered from the cache, it could hand out a version the file has left behind
     *   since, when changed by something that does not invalidate (a file edited in the library folder by hand).
     */
    suspend fun loadSongContent(fileName: String, useCache: Boolean = true): SongContent?

    /** Drops the cached text of one song, or of every song when [fileName] is null. */
    suspend fun invalidate(fileName: String? = null)

    /**
     * Drops the cached texts of these songs in one step. [invalidations] names each of them, or says null for every song
     * where there are too many to name without overflowing a collector that is behind.
     */
    suspend fun invalidate(fileNames: Set<String>)
}
