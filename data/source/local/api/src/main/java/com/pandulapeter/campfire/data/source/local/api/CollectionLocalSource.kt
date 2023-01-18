package com.pandulapeter.campfire.data.source.local.api

import com.pandulapeter.campfire.data.model.domain.Collection

interface CollectionLocalSource {

    suspend fun loadCollections(databaseUrl: String): List<Collection>

    suspend fun saveCollections(databaseUrl: String, collections: List<Collection>)

    suspend fun deleteAllCollections()
}