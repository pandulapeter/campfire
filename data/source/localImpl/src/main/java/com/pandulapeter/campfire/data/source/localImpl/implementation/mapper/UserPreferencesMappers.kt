package com.pandulapeter.campfire.data.source.localImpl.implementation.mapper

import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.source.localImpl.implementation.model.UserPreferencesEntity

internal fun UserPreferencesEntity.toModel() = UserPreferences(
    unselectedDatabaseUrls = unselectedDatabaseUrls.mapToList()
)

internal fun UserPreferences.toEntity() = UserPreferencesEntity(
    unselectedDatabaseUrls = unselectedDatabaseUrls.mapToString()
)