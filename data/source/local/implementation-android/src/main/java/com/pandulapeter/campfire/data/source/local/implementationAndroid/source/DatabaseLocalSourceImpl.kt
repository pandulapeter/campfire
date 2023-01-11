package com.pandulapeter.campfire.data.source.local.implementationAndroid.source

import com.pandulapeter.campfire.data.model.domain.Database
import com.pandulapeter.campfire.data.source.local.api.DatabaseLocalSource
import com.pandulapeter.campfire.data.source.local.implementationAndroid.storage.dao.DatabaseDao
import com.pandulapeter.campfire.data.source.local.implementationAndroid.mapper.toEntity
import com.pandulapeter.campfire.data.source.local.implementationAndroid.mapper.toModel

internal class DatabaseLocalSourceImpl(
    private val databaseDao: DatabaseDao
) : DatabaseLocalSource {

    override suspend fun loadDatabases() = databaseDao.getAll().map { it.toModel() }

    override suspend fun saveDatabases(databases: List<Database>) = databaseDao.updateAll(databases.map { it.toEntity() })
}