package com.pandulapeter.campfire.data.source.local.api

import com.pandulapeter.campfire.data.model.domain.RawSongDetails

interface RawSongDetailsLocalSource {

    /**
     * Only the urls: the song lists show whether a song is downloaded, which does not need the text of every one of
     * them to be read at start.
     */
    suspend fun loadDownloadedSongUrls(): Set<String>

    suspend fun loadRawSongDetails(url: String): RawSongDetails?

    suspend fun saveRawSongDetails(rawSongDetails: RawSongDetails)
}
