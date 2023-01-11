package com.pandulapeter.campfire.data.source.local.implementationDesktop.source

import com.pandulapeter.campfire.data.model.domain.Database
import com.pandulapeter.campfire.data.source.local.api.DatabaseLocalSource
import com.pandulapeter.campfire.data.source.local.implementationDesktop.mapper.toEntity
import com.pandulapeter.campfire.data.source.local.implementationDesktop.mapper.toModel
import com.pandulapeter.campfire.data.source.local.implementationDesktop.model.DatabaseEntity
import com.pandulapeter.campfire.data.source.local.implementationDesktop.storage.StorageManager
import io.realm.kotlin.ext.query

internal class DatabaseLocalSourceImpl(
    private val storageManager: StorageManager
) : DatabaseLocalSource {

    override suspend fun loadDatabases() =
        storageManager.database.query<DatabaseEntity>().find().toList().map { it.toModel() }

    override suspend fun saveDatabases(databases: List<Database>) = with(storageManager.database) {
        write { delete(query<DatabaseEntity>().find()) }
        writeBlocking { databases.forEach { copyToRealm(it.toEntity()) } }
    }
}