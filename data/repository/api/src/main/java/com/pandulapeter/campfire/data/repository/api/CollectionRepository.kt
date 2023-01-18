package com.pandulapeter.campfire.data.repository.api

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.Collection
import kotlinx.coroutines.flow.Flow

interface CollectionRepository {

    val collections: Flow<DataState<Map<String, List<Collection>>>>

    suspend fun loadCollections(databaseUrls: List<String>, isForceRefresh: Boolean)

    suspend fun deleteLocalCollections()
}