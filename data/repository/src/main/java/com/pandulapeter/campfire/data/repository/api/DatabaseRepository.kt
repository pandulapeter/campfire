package com.pandulapeter.campfire.data.repository.api

import com.pandulapeter.campfire.data.model.domain.Database

interface DatabaseRepository {

    suspend fun getDatabases(): List<Database>

    suspend fun updateDatabases(databases: List<Database>)
}