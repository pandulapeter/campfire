package com.pandulapeter.campfire.data.source.local.implementationDesktop.mapper

import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.source.local.implementationDesktop.model.UserPreferencesEntity

internal fun UserPreferencesEntity.toModel() = UserPreferences(
    shouldShowExplicitSongs = shouldShowExplicitSongs,
    shouldShowSongsWithoutChords = shouldShowSongsWithoutChords,
    unselectedDatabaseUrls = unselectedDatabaseUrls.mapToList()
)

internal fun UserPreferences.toEntity() = UserPreferencesEntity().also {
    it.shouldShowExplicitSongs = shouldShowExplicitSongs
    it.shouldShowSongsWithoutChords = shouldShowSongsWithoutChords
    it.unselectedDatabaseUrls = unselectedDatabaseUrls.mapToString()
}