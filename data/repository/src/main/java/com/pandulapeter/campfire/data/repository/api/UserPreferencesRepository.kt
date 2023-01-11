package com.pandulapeter.campfire.data.repository.api

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {

    val userPreferences: Flow<DataState<UserPreferences>>

    suspend fun loadUserPreferencesIfNeeded() : UserPreferences

    suspend fun saveUserPreferences(userPreferences: UserPreferences)
}