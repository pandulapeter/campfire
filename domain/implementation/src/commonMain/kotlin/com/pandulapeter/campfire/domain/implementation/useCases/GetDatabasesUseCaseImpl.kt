package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.repository.api.DatabaseRepository
import com.pandulapeter.campfire.domain.api.useCases.GetDatabasesUseCase
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The databases on their own, straight from their repository. GetScreenDataUseCase also exposes them, but only
 * once every other source has data too, which would make the Settings screen wait for the song list it does not
 * need.
 */
class GetDatabasesUseCaseImpl internal constructor(
    databaseRepository: DatabaseRepository
) : GetDatabasesUseCase {

    private val databasesFlow = databaseRepository.databases
        .map { dataState -> dataState.data.orEmpty().sortedBy { it.priority } }
        .distinctUntilChanged()

    override operator fun invoke() = databasesFlow
}
