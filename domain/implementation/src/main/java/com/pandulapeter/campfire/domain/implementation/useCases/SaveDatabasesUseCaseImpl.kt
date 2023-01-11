package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.model.domain.Database
import com.pandulapeter.campfire.data.repository.api.DatabaseRepository
import com.pandulapeter.campfire.domain.api.useCases.SaveDatabasesUseCase

class SaveDatabasesUseCaseImpl internal constructor(
    private val databaseRepository: DatabaseRepository
) : SaveDatabasesUseCase {

    override suspend operator fun invoke(databases: List<Database>) = databaseRepository.saveDatabases(databases)
}