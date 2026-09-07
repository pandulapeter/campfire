package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.model.domain.Database
import com.pandulapeter.campfire.data.source.local.api.DatabaseLocalSource
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toEntity
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toModel
import com.pandulapeter.campfire.data.source.local.implementation.storage.StorageManager

internal class DatabaseLocalSourceImpl(
    private val storageManager: StorageManager
) : DatabaseLocalSource {

    override suspend fun loadDatabases() = storageManager.loadDatabases().map { it.toModel() }

    override suspend fun saveDatabases(databases: List<Database>) = storageManager.saveDatabases(databases.map { it.toEntity() })
}
