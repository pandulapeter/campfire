package com.pandulapeter.campfire.data.source.local.implementationDesktop.mapper

import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.source.local.implementationDesktop.model.UserPreferencesEntity

internal fun UserPreferencesEntity.toModel() = UserPreferences(
    shouldShowExplicitSongs = shouldShowExplicitSongs,
    shouldShowSongsWithoutChords = shouldShowSongsWithoutChords,
    unselectedDatabaseUrls = unselectedDatabaseUrls.mapToList(),
    uiMode = UserPreferences.UiMode.values().firstOrNull { it.id == uiMode } ?: UserPreferences.UiMode.SYSTEM_DEFAULT
)

internal fun UserPreferences.toEntity() = UserPreferencesEntity().also {
    it.shouldShowExplicitSongs = shouldShowExplicitSongs
    it.shouldShowSongsWithoutChords = shouldShowSongsWithoutChords
    it.unselectedDatabaseUrls = unselectedDatabaseUrls.mapToString()
    it.uiMode = uiMode.id
}