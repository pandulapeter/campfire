/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.mapper

import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.source.local.implementation.model.UserPreferencesDocument

internal fun UserPreferencesDocument.toModel() = UserPreferences(
    isPerformanceModeEnabled = isPerformanceModeEnabled,
    shouldShowArchivedSetlists = shouldShowArchivedSetlists,
    isLyricsOnlyModeEnabled = isLyricsOnlyModeEnabled,
    isHorizontalSectionFlowEnabled = isHorizontalSectionFlowEnabled,
    // A hand edit or a newer version's wider range must not reach the screen as it is: a size of 40 is a column per
    // word. Not a number at all is no size, and is the default.
    fontScale = fontScale.takeIf { it.isFinite() }?.coerceIn(UserPreferences.MIN_FONT_SCALE, UserPreferences.MAX_FONT_SCALE)
        ?: UserPreferences.DEFAULT_FONT_SCALE,
    sortingMode = UserPreferences.SortingMode.entries.firstOrNull { it.id == sortingMode } ?: UserPreferences.SortingMode.BY_ARTIST,
    setlistSortingMode = UserPreferences.SetlistSortingMode.entries.firstOrNull { it.id == setlistSortingMode } ?: UserPreferences.SetlistSortingMode.NEWEST_FIRST,
    uiMode = UserPreferences.UiMode.entries.firstOrNull { it.id == uiMode } ?: UserPreferences.UiMode.SYSTEM_DEFAULT,
    themeColor = UserPreferences.ThemeColor.entries.firstOrNull { it.id == themeColor } ?: UserPreferences.ThemeColor.CAMPFIRE,
    language = UserPreferences.Language.entries.firstOrNull { it.id == language } ?: UserPreferences.Language.SYSTEM_DEFAULT,
    chordSpelling = UserPreferences.ChordSpelling(
        accidentals = UserPreferences.Accidentals.entries.firstOrNull { it.id == accidentals } ?: UserPreferences.Accidentals.ORIGINAL,
        isGermanNotationEnabled = isGermanNotationEnabled,
    ),
    transpositions = transpositions,
    foldedSections = foldedSections.mapValues { (_, keys) -> keys.toSet() }.filterValues { it.isNotEmpty() },
    tagMatchMode = UserPreferences.MatchMode.entries.firstOrNull { it.id == tagMatchMode } ?: UserPreferences.MatchMode.ANY,
    languageMatchMode = UserPreferences.MatchMode.entries.firstOrNull { it.id == languageMatchMode } ?: UserPreferences.MatchMode.ANY,
)

internal fun UserPreferences.toDocument() = UserPreferencesDocument(
    isPerformanceModeEnabled = isPerformanceModeEnabled,
    shouldShowArchivedSetlists = shouldShowArchivedSetlists,
    isLyricsOnlyModeEnabled = isLyricsOnlyModeEnabled,
    isHorizontalSectionFlowEnabled = isHorizontalSectionFlowEnabled,
    fontScale = fontScale,
    sortingMode = sortingMode.id,
    setlistSortingMode = setlistSortingMode.id,
    uiMode = uiMode.id,
    themeColor = themeColor.id,
    language = language.id,
    accidentals = chordSpelling.accidentals.id,
    isGermanNotationEnabled = chordSpelling.isGermanNotationEnabled,
    transpositions = transpositions,
    foldedSections = foldedSections.mapValues { (_, keys) -> keys.toList() },
    tagMatchMode = tagMatchMode.id,
    languageMatchMode = languageMatchMode.id,
)
