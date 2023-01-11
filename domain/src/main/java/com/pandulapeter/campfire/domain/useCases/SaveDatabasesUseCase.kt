package com.pandulapeter.campfire.domain.useCases

import com.pandulapeter.campfire.data.model.domain.Database
import com.pandulapeter.campfire.data.repository.api.DatabaseRepository

class SaveDatabasesUseCase internal constructor(
    private val databaseRepository: DatabaseRepository
) {
    suspend operator fun invoke(
        databases: List<Database>
    ) = databaseRepository.saveDatabases(databases)
}