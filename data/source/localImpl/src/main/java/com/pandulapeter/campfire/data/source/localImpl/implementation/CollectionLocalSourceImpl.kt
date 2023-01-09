package com.pandulapeter.campfire.data.source.localImpl.implementation

import com.pandulapeter.campfire.data.model.domain.Collection
import com.pandulapeter.campfire.data.source.local.CollectionLocalSource
import com.pandulapeter.campfire.data.source.localImpl.implementation.database.dao.CollectionDao
import com.pandulapeter.campfire.data.source.localImpl.implementation.mapper.toEntity
import com.pandulapeter.campfire.data.source.localImpl.implementation.mapper.toModel

internal class CollectionLocalSourceImpl(
    private val collectionDao: CollectionDao
) : CollectionLocalSource {

    override suspend fun getCollections() = collectionDao.getAll().map { it.toModel() }

    override suspend fun saveCollections(collections: List<Collection>) = collectionDao.updateAll(collections.map { it.toEntity() })
}