package com.pandulapeter.campfire.data.source.remote.api

import com.pandulapeter.campfire.data.model.domain.Collection

interface CollectionRemoteSource {

    suspend fun getCollections(sheetUrl: String): List<Collection>
}