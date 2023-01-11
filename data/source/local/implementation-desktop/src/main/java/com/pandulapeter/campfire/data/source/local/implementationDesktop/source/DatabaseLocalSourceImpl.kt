package com.pandulapeter.campfire.data.source.local.implementationDesktop.source

import com.pandulapeter.campfire.data.model.domain.Database
import com.pandulapeter.campfire.data.source.local.api.DatabaseLocalSource

internal class DatabaseLocalSourceImpl : DatabaseLocalSource {

    override suspend fun loadDatabases() = emptyList<Database>() // TODO

    override suspend fun saveDatabases(databases: List<Database>) = Unit // TODO
}