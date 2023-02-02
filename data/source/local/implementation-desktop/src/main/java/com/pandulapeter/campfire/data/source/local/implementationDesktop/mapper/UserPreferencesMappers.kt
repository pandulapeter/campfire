package com.pandulapeter.campfire.data.source.local.implementationDesktop.mapper

import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.source.local.implementationDesktop.model.UserPreferencesEntity

internal fun UserPreferencesEntity.toModel() = UserPreferences(
    shouldShowExplicitSongs = shouldShowExplicitSongs,
    shouldShowSongsWithoutChords = shouldShowSongsWithoutChords,
    showOnlyDownloadedSongs = showOnlyDownloadedSongs,
    unselectedDatabaseUrls = unselectedDatabaseUrls.mapToList(),
    sortingMode = UserPreferences.SortingMode.values().firstOrNull { it.id == sortingMode } ?: UserPreferences.SortingMode.BY_ARTIST,
    uiMode = UserPreferences.UiMode.values().firstOrNull { it.id == uiMode } ?: UserPreferences.UiMode.SYSTEM_DEFAULT,
    language = UserPreferences.Language.values().firstOrNull { it.id == language } ?: UserPreferences.Language.SYSTEM_DEFAULT
)

internal fun UserPreferences.toEntity() = UserPreferencesEntity().also {
    it.shouldShowExplicitSongs = shouldShowExplicitSongs
    it.shouldShowSongsWithoutChords = shouldShowSongsWithoutChords
    it.showOnlyDownloadedSongs = showOnlyDownloadedSongs
    it.unselectedDatabaseUrls = unselectedDatabaseUrls.mapToString()
    it.sortingMode = sortingMode.id
    it.uiMode = uiMode.id
    it.language = language.id
}