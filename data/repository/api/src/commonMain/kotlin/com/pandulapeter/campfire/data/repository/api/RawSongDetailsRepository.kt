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

    suspend fun loadRawSongDetails(url: String, isForceRefresh: Boolean)
}
