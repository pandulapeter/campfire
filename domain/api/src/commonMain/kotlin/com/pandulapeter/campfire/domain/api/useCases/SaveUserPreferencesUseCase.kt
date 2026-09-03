package com.pandulapeter.campfire.domain.api.useCases

import com.pandulapeter.campfire.data.model.domain.UserPreferences

interface SaveUserPreferencesUseCase {

    suspend operator fun invoke(userPreferences: UserPreferences)
}