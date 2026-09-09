package com.pandulapeter.campfire.domain.api.useCases

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import kotlinx.coroutines.flow.Flow

interface GetUserPreferencesUseCase {

    /**
     * Separate from [GetScreenDataUseCase], which can only produce anything once the whole library has been read:
     * the theme and the language must not wait for a song scan that has nothing to do with them.
     */
    operator fun invoke(): Flow<DataState<UserPreferences>>
}
