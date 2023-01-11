package com.pandulapeter.campfire.domain.useCases

import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.repository.api.UserPreferencesRepository

class SaveUserPreferencesUseCase internal constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) {
    suspend operator fun invoke(
        userPreferences: UserPreferences
    ) = userPreferencesRepository.saveUserPreferences(userPreferences)
}