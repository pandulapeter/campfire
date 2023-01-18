package com.pandulapeter.campfire.data.source.local.implementationDesktop.mapper

import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.source.local.implementationDesktop.model.UserPreferencesEntity

internal fun UserPreferencesEntity.toModel() = UserPreferences(
    unselectedDatabaseUrls = unselectedDatabaseUrls.mapToList(),
    shouldShowExplicitSongs = shouldShowExplicitSongs
)

internal fun UserPreferences.toEntity() = UserPreferencesEntity().also {
    it.unselectedDatabaseUrls = unselectedDatabaseUrls.mapToString()
    it.shouldShowExplicitSongs = shouldShowExplicitSongs
}