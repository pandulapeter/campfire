package com.pandulapeter.campfire.data.source.local.api

import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SongContent

interface SongLocalSource {

    /** Every `.cho` file in the library, with the metadata parsed out of it. Unreadable files are skipped. */
    suspend fun loadSongs(): List<Song>

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
     * Writes [text] under a free file name and returns the song it became: [desiredFileName] when the import has an
     * original name worth keeping, otherwise one derived from the title and artist in the text itself.
     */
    suspend fun importSong(desiredFileName: String?, text: String): Song

    suspend fun deleteSong(fileName: String)

    suspend fun renameSong(from: String, to: String)

    suspend fun exists(fileName: String): Boolean
}
