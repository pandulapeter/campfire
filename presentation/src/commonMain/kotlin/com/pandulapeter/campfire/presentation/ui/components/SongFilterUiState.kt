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

import androidx.compose.runtime.Immutable
import com.pandulapeter.campfire.data.model.domain.SongLanguage
import com.pandulapeter.campfire.data.model.domain.Tag
import com.pandulapeter.campfire.data.model.domain.UserPreferences

/** Everything [SongFilters] shows: what the library can be filtered by, what is selected, and how. */
@Immutable
internal data class SongFilterUiState(
    val tags: List<Tag> = emptyList(),
    val languages: List<SongLanguage> = emptyList(),
    val selectedTags: Set<String> = emptySet(),
    val selectedLanguages: Set<String> = emptySet(),
    val tagMatchMode: UserPreferences.MatchMode = UserPreferences.MatchMode.ANY,
    val languageMatchMode: UserPreferences.MatchMode = UserPreferences.MatchMode.ANY,
    val tagSortingMode: UserPreferences.LabelSortingMode = UserPreferences.LabelSortingMode.BY_USAGE,
    val languageSortingMode: UserPreferences.LabelSortingMode = UserPreferences.LabelSortingMode.BY_USAGE,
    val isActive: Boolean = false,
)
