/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.runtime.Stable
import com.pandulapeter.campfire.data.model.domain.UserPreferences

/** What the controls of [SongFilters] change, remembered once where the filters are shown. */
@Stable
internal interface SongFilterActions {

    fun toggleTagFilter(tag: String)

    fun clearTagFilter()

    fun setTagMatchMode(value: UserPreferences.MatchMode)

    fun setTagSortingMode(value: UserPreferences.LabelSortingMode)

    fun toggleLanguageFilter(code: String)

    fun clearLanguageFilter()

    fun setLanguageMatchMode(value: UserPreferences.MatchMode)

    fun setLanguageSortingMode(value: UserPreferences.LabelSortingMode)

    fun clearSongFilter()
}
