package com.pandulapeter.campfire.data.source.local.implementation.mapper

import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.source.local.implementation.model.UserPreferencesDocument

internal fun UserPreferencesDocument.toModel() = UserPreferences(
    shouldShowSongsWithoutChords = shouldShowSongsWithoutChords,
    isLyricsOnlyModeEnabled = isLyricsOnlyModeEnabled,
    isHorizontalSectionFlowEnabled = isHorizontalSectionFlowEnabled,
    fontScale = fontScale,
    sortingMode = UserPreferences.SortingMode.entries.firstOrNull { it.id == sortingMode } ?: UserPreferences.SortingMode.BY_ARTIST,
    uiMode = UserPreferences.UiMode.entries.firstOrNull { it.id == uiMode } ?: UserPreferences.UiMode.SYSTEM_DEFAULT,
    language = UserPreferences.Language.entries.firstOrNull { it.id == language } ?: UserPreferences.Language.SYSTEM_DEFAULT,
    transpositions = transpositions
)

internal fun UserPreferences.toDocument() = UserPreferencesDocument(
    shouldShowSongsWithoutChords = shouldShowSongsWithoutChords,
    isLyricsOnlyModeEnabled = isLyricsOnlyModeEnabled,
    isHorizontalSectionFlowEnabled = isHorizontalSectionFlowEnabled,
    fontScale = fontScale,
    sortingMode = sortingMode.id,
    uiMode = uiMode.id,
    language = language.id,
    transpositions = transpositions
)
