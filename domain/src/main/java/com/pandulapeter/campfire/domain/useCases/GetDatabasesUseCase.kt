package com.pandulapeter.campfire.domain.useCases

import com.pandulapeter.campfire.data.repository.api.DatabaseRepository

class GetDatabasesUseCase internal constructor(
    private val databaseRepository: DatabaseRepository
) {
    suspend operator fun invoke() = databaseRepository.getDatabases()
}