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

    /**
     * Writes an imported song under a free name based on [desiredFileName] and returns it. The cached list is left
     * alone: an import writes many files at once and ends with a single [rescan].
     */
    suspend fun importSong(desiredFileName: String?, text: String): Song

    suspend fun deleteSong(fileName: String)
}
