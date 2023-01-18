package com.pandulapeter.campfire.data.source.local.implementationAndroid.mapper

import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.source.local.implementationAndroid.model.UserPreferencesEntity

internal fun UserPreferencesEntity.toModel() = UserPreferences(
    unselectedDatabaseUrls = unselectedDatabaseUrls.mapToList(),
    shouldShowExplicitSongs = shouldShowExplicitSongs
)

internal fun UserPreferences.toEntity() = UserPreferencesEntity(
    unselectedDatabaseUrls = unselectedDatabaseUrls.mapToString(),
    shouldShowExplicitSongs = shouldShowExplicitSongs
)