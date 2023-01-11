package com.pandulapeter.campfire.data.source.local.implementationAndroid.source

import com.pandulapeter.campfire.data.model.domain.Collection
import com.pandulapeter.campfire.data.source.local.api.CollectionLocalSource
import com.pandulapeter.campfire.data.source.local.implementationAndroid.storage.dao.CollectionDao
import com.pandulapeter.campfire.data.source.local.implementationAndroid.mapper.toEntity
import com.pandulapeter.campfire.data.source.local.implementationAndroid.mapper.toModel

internal class CollectionLocalSourceImpl(
    private val collectionDao: CollectionDao
) : CollectionLocalSource {

    override suspend fun loadCollections(databaseUrl: String) = collectionDao.getAll(databaseUrl).map { it.toModel() }

    override suspend fun saveCollections(databaseUrl: String, collections: List<Collection>) = collectionDao.updateAll(databaseUrl, collections.map { it.toEntity(databaseUrl) })
}