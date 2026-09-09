package com.pandulapeter.campfire.data.source.local.api

import com.pandulapeter.campfire.data.model.domain.UserPreferences

interface UserPreferencesLocalSource {

    /** Null when nothing has been saved yet, so that the repository can fall back on its defaults. */
    suspend fun loadUserPreferences(): UserPreferences?

    suspend fun saveUserPreferences(userPreferences: UserPreferences)
}
