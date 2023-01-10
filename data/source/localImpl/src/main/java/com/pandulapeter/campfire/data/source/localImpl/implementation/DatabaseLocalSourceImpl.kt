package com.pandulapeter.campfire.data.source.localImpl.implementation

import com.pandulapeter.campfire.data.model.domain.Database
import com.pandulapeter.campfire.data.source.local.DatabaseLocalSource
import com.pandulapeter.campfire.data.source.localImpl.implementation.storage.dao.DatabaseDao
import com.pandulapeter.campfire.data.source.localImpl.implementation.mapper.toEntity
import com.pandulapeter.campfire.data.source.localImpl.implementation.mapper.toModel

internal class DatabaseLocalSourceImpl(
    private val databaseDao: DatabaseDao
) : DatabaseLocalSource {

    override suspend fun getDatabases() = databaseDao.getAll().map { it.toModel() }

    override suspend fun saveDatabases(databases: List<Database>) = databaseDao.updateAll(databases.map { it.toEntity() })
}