package com.pandulapeter.campfire.data.repository.api

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {

    val userPreferences: Flow<DataState<UserPreferences>>

    /** The saved preferences, or null if they could not be read - see `BaseLocalDataRepository.loadDataIfNeeded`. */
    suspend fun loadUserPreferencesIfNeeded(): UserPreferences?

    suspend fun saveUserPreferences(userPreferences: UserPreferences)
}