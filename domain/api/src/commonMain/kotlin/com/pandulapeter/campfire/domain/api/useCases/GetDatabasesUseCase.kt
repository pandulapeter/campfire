package com.pandulapeter.campfire.domain.api.useCases

import com.pandulapeter.campfire.data.model.domain.Database
import kotlinx.coroutines.flow.Flow

interface GetDatabasesUseCase {

    operator fun invoke(): Flow<List<Database>>
}
