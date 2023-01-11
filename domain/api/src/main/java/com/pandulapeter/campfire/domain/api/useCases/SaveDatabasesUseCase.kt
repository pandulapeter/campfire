package com.pandulapeter.campfire.domain.api.useCases

import com.pandulapeter.campfire.data.model.domain.Database

interface SaveDatabasesUseCase {

    suspend operator fun invoke(databases: List<Database>)
}