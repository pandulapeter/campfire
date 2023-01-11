package com.pandulapeter.campfire.data.source.localImpl.implementation

import com.pandulapeter.campfire.data.model.domain.Collection
import com.pandulapeter.campfire.data.source.local.CollectionLocalSource
import com.pandulapeter.campfire.data.source.localImpl.implementation.storage.dao.CollectionDao
import com.pandulapeter.campfire.data.source.localImpl.implementation.mapper.toEntity
import com.pandulapeter.campfire.data.source.localImpl.implementation.mapper.toModel

internal class CollectionLocalSourceImpl(
    private val collectionDao: CollectionDao
) : CollectionLocalSource {

    override suspend fun loadCollections(databaseUrl: String) = collectionDao.getAll(databaseUrl).map { it.toModel() }

    override suspend fun saveCollections(databaseUrl: String, collections: List<Collection>) = collectionDao.updateAll(databaseUrl, collections.map { it.toEntity(databaseUrl) })
}