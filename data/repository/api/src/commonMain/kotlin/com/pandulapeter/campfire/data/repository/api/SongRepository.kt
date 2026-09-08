package com.pandulapeter.campfire.data.repository.api

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.Song
import kotlinx.coroutines.flow.Flow

interface SongRepository {

    val songs: Flow<DataState<Map<String, List<Song>>>>

    /** @return Whether every database ended up with songs. A failed silent refresh is reported here and nowhere else. */
    suspend fun loadSongs(databaseUrls: List<String>, isForceRefresh: Boolean): Boolean
}