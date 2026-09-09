package com.pandulapeter.campfire.data.repository.api

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.RawSongDetails
import kotlinx.coroutines.flow.Flow

interface RawSongDetailsRepository {

    /** The text of the songs opened so far, filled in on demand by [loadRawSongDetails]. */
    val rawSongDetails: Flow<DataState<Map<String, RawSongDetails>>>

    /**
     * Reads the saved text of a song.
     *
     * @return Whether the song has text to show afterwards.
     */
    suspend fun loadRawSongDetails(url: String): Boolean
}
