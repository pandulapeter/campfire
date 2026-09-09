package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.repository.api.UserPreferencesRepository
import com.pandulapeter.campfire.domain.api.useCases.GetUserPreferencesUseCase

class GetUserPreferencesUseCaseImpl internal constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : GetUserPreferencesUseCase {

    override operator fun invoke() = userPreferencesRepository.userPreferences
}
