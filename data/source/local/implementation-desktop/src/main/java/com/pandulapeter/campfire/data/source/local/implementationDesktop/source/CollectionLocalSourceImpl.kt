package com.pandulapeter.campfire.data.source.local.implementationDesktop.source

import com.pandulapeter.campfire.data.model.domain.Collection
import com.pandulapeter.campfire.data.source.local.api.CollectionLocalSource
import com.pandulapeter.campfire.data.source.local.implementationDesktop.mapper.toEntity
import com.pandulapeter.campfire.data.source.local.implementationDesktop.mapper.toModel
import com.pandulapeter.campfire.data.source.local.implementationDesktop.model.CollectionEntity
import com.pandulapeter.campfire.data.source.local.implementationDesktop.storage.StorageManager
import io.realm.kotlin.ext.query

internal class CollectionLocalSourceImpl(
    private val storageManager: StorageManager
) : CollectionLocalSource {

    override suspend fun loadCollections(databaseUrl: String) =
        storageManager.database.query<CollectionEntity>("databaseUrl == $0", databaseUrl).find().toList().map { it.toModel() }

    override suspend fun saveCollections(databaseUrl: String, collections: List<Collection>) = with(storageManager.database) {
        write { delete(query<CollectionEntity>("databaseUrl == $0", databaseUrl).find()) }
        writeBlocking { collections.forEach { copyToRealm(it.toEntity(databaseUrl)) } }
    }

    override suspend fun deleteAllCollections() = with(storageManager.database) {
        write { delete(query<CollectionEntity>().find()) }
    }
}