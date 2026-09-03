package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.model.domain.Database
import com.pandulapeter.campfire.data.source.local.api.DatabaseLocalSource
import com.pandulapeter.campfire.data.source.local.implementation.storage.dao.DatabaseDao
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toEntity
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toModel

internal class DatabaseLocalSourceImpl(
    private val databaseDao: DatabaseDao
) : DatabaseLocalSource {

    override suspend fun loadDatabases() = databaseDao.getAll().map { it.toModel() }

    override suspend fun saveDatabases(databases: List<Database>) = databaseDao.updateAll(databases.map { it.toEntity() })
}