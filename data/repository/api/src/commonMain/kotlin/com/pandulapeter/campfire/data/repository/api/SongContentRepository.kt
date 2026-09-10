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

/**
 * The text of the songs that have been opened, kept in memory so that paging back and forth in a setlist does not
 * read the same files over and over. The list of songs does not carry the text, see `SongRepository`.
 */
interface SongContentRepository {

    /**
     * Null if the file does not exist or could not be read.
     *
     * @param shouldCache False for a bulk read such as an export, which walks the whole library once and would
     *   otherwise leave all of it in memory.
     */
    suspend fun loadSongContent(fileName: String, shouldCache: Boolean = true): SongContent?

    /** Drops the cached text of one song, or of every song when [fileName] is null. */
    suspend fun invalidate(fileName: String? = null)
}
