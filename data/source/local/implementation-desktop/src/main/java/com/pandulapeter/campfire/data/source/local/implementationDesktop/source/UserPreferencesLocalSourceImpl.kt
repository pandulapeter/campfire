package com.pandulapeter.campfire.data.source.local.implementationDesktop.source

import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.source.local.api.UserPreferencesLocalSource

internal class UserPreferencesLocalSourceImpl : UserPreferencesLocalSource {

    override suspend fun loadUserPreferences(): UserPreferences? = null // TODO

    override suspend fun saveUserPreferences(userPreferences: UserPreferences) = Unit // TODO
}