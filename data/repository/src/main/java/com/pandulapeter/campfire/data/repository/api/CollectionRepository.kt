package com.pandulapeter.campfire.data.repository.api

import com.pandulapeter.campfire.data.model.domain.Collection

interface CollectionRepository {

    suspend fun getCollections(sheetUrl: String, isForceRefresh: Boolean): List<Collection>
}