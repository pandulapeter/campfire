/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.playing

import com.pandulapeter.campfire.data.model.preferences.UserPreferences

/** Preferences with every feature on and nothing set, for the tests of this package to copy what they are about into. */
internal fun testPreferences() = UserPreferences(
    isPerformanceModeEnabled = false,
    shouldShowArchivedSetlists = false,
    areChordsEnabled = true,
    areSetlistsEnabled = true,
    isMetronomeEnabled = true,
    fontScale = UserPreferences.DEFAULT_FONT_SCALE,
    sortingMode = UserPreferences.SortingMode.BY_TITLE,
    setlistSortingMode = UserPreferences.SetlistSortingMode.BY_DATE,
    uiMode = UserPreferences.UiMode.SYSTEM_DEFAULT,
    themeColor = UserPreferences.ThemeColor.CAMPFIRE,
    isAppIconThemed = true,
    isCoverArtEnabled = true,
    shouldNumberSections = true,
    language = UserPreferences.Language.SYSTEM_DEFAULT,
    chordSpelling = UserPreferences.ChordSpelling.Default,
    transpositions = emptyMap(),
    foldedSections = emptyMap(),
    tagMatchMode = UserPreferences.MatchMode.ANY,
    languageMatchMode = UserPreferences.MatchMode.ANY,
    tagSortingMode = UserPreferences.LabelSortingMode.BY_USAGE,
    languageSortingMode = UserPreferences.LabelSortingMode.BY_USAGE,
)
