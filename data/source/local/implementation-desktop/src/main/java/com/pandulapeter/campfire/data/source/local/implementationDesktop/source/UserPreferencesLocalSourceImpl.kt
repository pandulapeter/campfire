package com.pandulapeter.campfire.data.source.local.implementationDesktop.source

import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.source.local.api.UserPreferencesLocalSource
import com.pandulapeter.campfire.data.source.local.implementationDesktop.mapper.toEntity
import com.pandulapeter.campfire.data.source.local.implementationDesktop.mapper.toModel
import com.pandulapeter.campfire.data.source.local.implementationDesktop.model.UserPreferencesEntity
import com.pandulapeter.campfire.data.source.local.implementationDesktop.storage.StorageManager
import io.realm.kotlin.ext.query

internal class UserPreferencesLocalSourceImpl(
    private val storageManager: StorageManager
) : UserPreferencesLocalSource {

    override suspend fun loadUserPreferences() =
        storageManager.database.query<UserPreferencesEntity>().find().toList().firstOrNull()?.toModel()

    override suspend fun saveUserPreferences(userPreferences: UserPreferences) {
        with(storageManager.database) {
            write { delete(query<UserPreferencesEntity>().find()) }
            writeBlocking { copyToRealm(userPreferences.toEntity()) }
        }
    }
}