package com.pandulapeter.campfire.data.source.local.implementationDesktop.source

import com.pandulapeter.campfire.data.model.domain.Collection
import com.pandulapeter.campfire.data.source.local.api.CollectionLocalSource

internal class CollectionLocalSourceImpl : CollectionLocalSource {

    override suspend fun loadCollections(databaseUrl: String) = emptyList<Collection>() // TODO

    override suspend fun saveCollections(databaseUrl: String, collections: List<Collection>) = Unit // TODO
}