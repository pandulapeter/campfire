package com.pandulapeter.campfire.data.repository.api

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.RawSongDetails
import kotlinx.coroutines.flow.Flow

interface RawSongDetailsRepository {

    /** The urls of the songs saved for offline use - all a song list needs to mark a song as downloaded. */
    val downloadedSongUrls: Flow<DataState<Set<String>>>

    /** The text of the songs opened so far, filled in on demand by [loadRawSongDetails]. */
    val rawSongDetails: Flow<DataState<Map<String, RawSongDetails>>>

    suspend fun loadDownloadedSongUrlsIfNeeded()

    /**
     * Reads the text of a song: the saved copy first, then - unless that copy is less than a day old - the network,
     * so that a song edited after it was downloaded reaches the clients that already have it.
     *
     * The refresh is silent. A song that is already saved shows its saved text right away, keeps it if the request
     * fails, and only swaps to the new text once that has arrived.
     */
    suspend fun loadRawSongDetails(url: String, isForceRefresh: Boolean)
}
