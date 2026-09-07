package com.pandulapeter.campfire.data.source.local.implementation.mapper

import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.source.local.implementation.model.UserPreferencesEntity

internal fun UserPreferencesEntity.toModel() = UserPreferences(
    shouldShowSongsWithoutChords = shouldShowSongsWithoutChords,
    showOnlyDownloadedSongs = showOnlyDownloadedSongs,
    isLyricsOnlyModeEnabled = isLyricsOnlyModeEnabled,
    fontScale = fontScale,
    unselectedDatabaseUrls = unselectedDatabaseUrls.mapToList(),
    sortingMode = UserPreferences.SortingMode.values().firstOrNull { it.id == sortingMode } ?: UserPreferences.SortingMode.BY_ARTIST,
    uiMode = UserPreferences.UiMode.values().firstOrNull { it.id == uiMode } ?: UserPreferences.UiMode.SYSTEM_DEFAULT,
    language = UserPreferences.Language.values().firstOrNull { it.id == language } ?: UserPreferences.Language.SYSTEM_DEFAULT
)

internal fun UserPreferences.toEntity() = UserPreferencesEntity(
    shouldShowSongsWithoutChords = shouldShowSongsWithoutChords,
    showOnlyDownloadedSongs = showOnlyDownloadedSongs,
    isLyricsOnlyModeEnabled = isLyricsOnlyModeEnabled,
    fontScale = fontScale,
    unselectedDatabaseUrls = unselectedDatabaseUrls.mapToString(),
    sortingMode = sortingMode.id,
    uiMode = uiMode.id,
    language = language.id
)