package com.pandulapeter.campfire.data.repository.api

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SongDetails
import kotlinx.coroutines.flow.Flow

interface SongDetailsRepository {

    val songDetails: Flow<DataState<SongDetails>>

    suspend fun loadSongDetails(song: Song?, isForceRefresh: Boolean)
}