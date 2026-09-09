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
