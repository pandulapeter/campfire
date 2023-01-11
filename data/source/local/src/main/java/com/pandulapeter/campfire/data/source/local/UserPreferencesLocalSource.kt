package com.pandulapeter.campfire.data.source.local

import com.pandulapeter.campfire.data.model.domain.UserPreferences

interface UserPreferencesLocalSource {

    suspend fun loadUserPreferences(): UserPreferences?

    suspend fun saveUserPreferences(userPreferences: UserPreferences)
}