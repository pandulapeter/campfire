package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.source.local.api.UserPreferencesLocalSource
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toEntity
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toModel
import com.pandulapeter.campfire.data.source.local.implementation.storage.StorageManager

internal class UserPreferencesLocalSourceImpl(
    private val storageManager: StorageManager
) : UserPreferencesLocalSource {

    override suspend fun loadUserPreferences() = storageManager.loadUserPreferences()?.toModel()

    override suspend fun saveUserPreferences(userPreferences: UserPreferences) = storageManager.saveUserPreferences(userPreferences.toEntity())
}
