package com.pandulapeter.campfire.data.repository.api

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.RawSongDetails
import kotlinx.coroutines.flow.Flow

interface RawSongDetailsRepository {

    val rawSongDetails: Flow<DataState<Map<String, RawSongDetails>>>

    suspend fun loadRawSongDetailsIfNeeded()

    suspend fun loadRawSongDetails(url: String, isForceRefresh: Boolean)
}