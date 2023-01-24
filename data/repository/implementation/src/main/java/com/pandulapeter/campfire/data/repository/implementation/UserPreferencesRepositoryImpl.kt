package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.repository.api.UserPreferencesRepository
import com.pandulapeter.campfire.data.repository.implementation.base.BaseLocalDataRepository
import com.pandulapeter.campfire.data.source.local.api.UserPreferencesLocalSource

internal class UserPreferencesRepositoryImpl(
    userPreferencesLocalSource: UserPreferencesLocalSource
) : BaseLocalDataRepository<UserPreferences>(
    loadDataFromLocalSource = { userPreferencesLocalSource.loadUserPreferences() ?: defaultUserPreferences },
    saveDataToLocalSource = userPreferencesLocalSource::saveUserPreferences
), UserPreferencesRepository {

    override val userPreferences = dataState

    override suspend fun loadUserPreferencesIfNeeded() = loadDataIfNeeded()

    override suspend fun saveUserPreferences(userPreferences: UserPreferences) = saveData(userPreferences)

    companion object {
        private val defaultUserPreferences = UserPreferences(
            shouldShowExplicitSongs = false,
            shouldShowSongsWithoutChords = false,
            unselectedDatabaseUrls = emptyList(),
            uiMode = UserPreferences.UiMode.SYSTEM_DEFAULT,
            language = UserPreferences.Language.SYSTEM_DEFAULT
        )
    }
}