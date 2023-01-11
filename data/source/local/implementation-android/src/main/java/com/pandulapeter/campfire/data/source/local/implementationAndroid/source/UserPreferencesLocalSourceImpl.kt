package com.pandulapeter.campfire.data.source.local.implementationAndroid.source

import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.source.local.api.UserPreferencesLocalSource
import com.pandulapeter.campfire.data.source.local.implementationAndroid.mapper.toEntity
import com.pandulapeter.campfire.data.source.local.implementationAndroid.mapper.toModel
import com.pandulapeter.campfire.data.source.local.implementationAndroid.storage.dao.UserPreferencesDao

internal class UserPreferencesLocalSourceImpl(
    private val userPreferencesDao: UserPreferencesDao
) : UserPreferencesLocalSource {

    override suspend fun loadUserPreferences() = userPreferencesDao.getAll().map { it.toModel() }.firstOrNull()

    override suspend fun saveUserPreferences(userPreferences: UserPreferences) = userPreferencesDao.updateAll(listOf(userPreferences.toEntity()))
}