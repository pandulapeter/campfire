package com.pandulapeter.campfire.data.source.local

import com.pandulapeter.campfire.data.model.domain.Database

interface DatabaseLocalSource {

    suspend fun loadDatabases(): List<Database>

    suspend fun saveDatabases(databases: List<Database>)
}