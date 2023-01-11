package com.pandulapeter.campfire.data.source.remote.api

import com.pandulapeter.campfire.data.model.domain.Collection

interface CollectionRemoteSource {

    suspend fun loadCollections(sheetUrl: String): List<Collection>
}