package com.pandulapeter.campfire.data.source.local

import com.pandulapeter.campfire.data.model.domain.Collection

interface CollectionLocalSource {

    suspend fun getCollections(): List<Collection>

    suspend fun saveCollections(collections: List<Collection>)
}