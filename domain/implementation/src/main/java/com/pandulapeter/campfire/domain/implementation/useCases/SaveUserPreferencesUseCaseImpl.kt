package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.repository.api.UserPreferencesRepository
import com.pandulapeter.campfire.domain.api.useCases.SaveUserPreferencesUseCase

class SaveUserPreferencesUseCaseImpl internal constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : SaveUserPreferencesUseCase {

    override suspend operator fun invoke(userPreferences: UserPreferences) = userPreferencesRepository.saveUserPreferences(userPreferences)
}