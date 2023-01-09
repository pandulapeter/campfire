package com.pandulapeter.campfire.data.repository.api

import com.pandulapeter.campfire.data.model.domain.Collection

interface CollectionRepository {

    fun areCollectionsAvailable() : Boolean

    suspend fun getCollections(isForceRefresh: Boolean): List<Collection>

    suspend fun getCollectionById(isForceRefresh: Boolean, collectionId: String) : Collection
}