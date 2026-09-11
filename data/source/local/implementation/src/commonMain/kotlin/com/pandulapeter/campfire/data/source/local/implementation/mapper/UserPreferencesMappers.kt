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
    shouldShowSongsWithoutChords = shouldShowSongsWithoutChords,
    isLyricsOnlyModeEnabled = isLyricsOnlyModeEnabled,
    isHorizontalSectionFlowEnabled = isHorizontalSectionFlowEnabled,
    fontScale = fontScale,
    sortingMode = UserPreferences.SortingMode.entries.firstOrNull { it.id == sortingMode } ?: UserPreferences.SortingMode.BY_ARTIST,
    uiMode = UserPreferences.UiMode.entries.firstOrNull { it.id == uiMode } ?: UserPreferences.UiMode.SYSTEM_DEFAULT,
    language = UserPreferences.Language.entries.firstOrNull { it.id == language } ?: UserPreferences.Language.SYSTEM_DEFAULT,
    chordSpelling = UserPreferences.ChordSpelling(
        accidentals = UserPreferences.Accidentals.entries.firstOrNull { it.id == accidentals } ?: UserPreferences.Accidentals.ORIGINAL,
        isGermanNotationEnabled = isGermanNotationEnabled,
    ),
    transpositions = transpositions,
)

internal fun UserPreferences.toDocument() = UserPreferencesDocument(
    shouldShowSongsWithoutChords = shouldShowSongsWithoutChords,
    isLyricsOnlyModeEnabled = isLyricsOnlyModeEnabled,
    isHorizontalSectionFlowEnabled = isHorizontalSectionFlowEnabled,
    fontScale = fontScale,
    sortingMode = sortingMode.id,
    uiMode = uiMode.id,
    language = language.id,
    accidentals = chordSpelling.accidentals.id,
    isGermanNotationEnabled = chordSpelling.isGermanNotationEnabled,
    transpositions = transpositions,
)
