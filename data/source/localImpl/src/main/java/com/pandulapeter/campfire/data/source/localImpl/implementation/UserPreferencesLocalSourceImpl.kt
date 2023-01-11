package com.pandulapeter.campfire.data.source.localImpl.implementation

import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.source.local.UserPreferencesLocalSource
import com.pandulapeter.campfire.data.source.localImpl.implementation.mapper.toEntity
import com.pandulapeter.campfire.data.source.localImpl.implementation.mapper.toModel
import com.pandulapeter.campfire.data.source.localImpl.implementation.storage.dao.UserPreferencesDao

internal class UserPreferencesLocalSourceImpl(
    private val userPreferencesDao: UserPreferencesDao
) : UserPreferencesLocalSource {

    override suspend fun loadUserPreferences() = userPreferencesDao.getAll().map { it.toModel() }.firstOrNull()

    override suspend fun saveUserPreferences(userPreferences: UserPreferences) = userPreferencesDao.updateAll(listOf(userPreferences.toEntity()))
}