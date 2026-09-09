package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.repository.api.UserPreferencesRepository
import com.pandulapeter.campfire.data.repository.implementation.base.BaseLocalDataRepository
import com.pandulapeter.campfire.data.source.local.api.UserPreferencesLocalSource

internal class UserPreferencesRepositoryImpl(
    private val userPreferencesLocalSource: UserPreferencesLocalSource
) : BaseLocalDataRepository<UserPreferences>(
    loadDataFromLocalSource = userPreferencesLocalSource::loadUserPreferences
), UserPreferencesRepository {

    override val userPreferences = dataState

    override suspend fun loadUserPreferencesIfNeeded() = loadDataIfNeeded()

    override suspend fun saveUserPreferences(userPreferences: UserPreferences) = writeData(userPreferences) {
        userPreferencesLocalSource.saveUserPreferences(it)
    }
}
