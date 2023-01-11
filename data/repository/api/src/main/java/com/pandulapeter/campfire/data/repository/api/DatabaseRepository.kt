package com.pandulapeter.campfire.data.repository.api

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.Database
import kotlinx.coroutines.flow.Flow

interface DatabaseRepository {

    val databases: Flow<DataState<List<Database>>>

    suspend fun loadDatabasesIfNeeded(): List<Database>

    suspend fun saveDatabases(databases: List<Database>)
}