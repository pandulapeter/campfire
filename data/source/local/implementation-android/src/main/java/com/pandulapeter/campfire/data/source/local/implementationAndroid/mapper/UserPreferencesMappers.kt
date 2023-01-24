package com.pandulapeter.campfire.data.source.local.implementationAndroid.mapper

import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.source.local.implementationAndroid.model.UserPreferencesEntity

internal fun UserPreferencesEntity.toModel() = UserPreferences(
    shouldShowExplicitSongs = shouldShowExplicitSongs,
    shouldShowSongsWithoutChords = shouldShowSongsWithoutChords,
    unselectedDatabaseUrls = unselectedDatabaseUrls.mapToList(),
    uiMode = UserPreferences.UiMode.values().firstOrNull { it.id == uiMode } ?: UserPreferences.UiMode.SYSTEM_DEFAULT,
    language = UserPreferences.Language.values().firstOrNull { it.id == language } ?: UserPreferences.Language.SYSTEM_DEFAULT
)

internal fun UserPreferences.toEntity() = UserPreferencesEntity(
    shouldShowExplicitSongs = shouldShowExplicitSongs,
    shouldShowSongsWithoutChords = shouldShowSongsWithoutChords,
    unselectedDatabaseUrls = unselectedDatabaseUrls.mapToString(),
    uiMode = uiMode.id,
    language = language.id
)