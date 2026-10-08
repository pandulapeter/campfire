/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.SongFilterActions
import com.pandulapeter.campfire.presentation.ui.components.SongFilterUiState

/**
 * What [com.pandulapeter.campfire.presentation.ui.components.SongFilters] shows, as [viewModel] holds it, for the two
 * places that show it: the songs screen's side panel and the filters sheet.
 */
@Composable
internal fun rememberSongFilterUiState(viewModel: CampfireViewModel): SongFilterUiState {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val songFilter by viewModel.songFilter.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val languages by viewModel.languages.collectAsStateWithLifecycle()
    val isSongFilterActive by viewModel.isSongFilterActive.collectAsStateWithLifecycle()
    return remember(userPreferences, songFilter, tags, languages, isSongFilterActive) {
        SongFilterUiState(
            tags = tags,
            languages = languages,
            selectedTags = songFilter.selectedTags,
            selectedLanguages = songFilter.selectedLanguages,
            tagMatchMode = userPreferences?.tagMatchMode ?: UserPreferences.MatchMode.ANY,
            languageMatchMode = userPreferences?.languageMatchMode ?: UserPreferences.MatchMode.ANY,
            tagSortingMode = userPreferences?.tagSortingMode ?: UserPreferences.LabelSortingMode.BY_USAGE,
            languageSortingMode = userPreferences?.languageSortingMode ?: UserPreferences.LabelSortingMode.BY_USAGE,
            isActive = isSongFilterActive,
        )
    }
}

/** The [SongFilterActions] that hand every control of the song filters to [viewModel]. */
@Composable
internal fun rememberSongFilterActions(viewModel: CampfireViewModel): SongFilterActions = remember(viewModel) {
    object : SongFilterActions {
        override fun toggleTagFilter(tag: String) {
            viewModel.toggleTagFilter(tag)
        }

        override fun clearTagFilter() {
            viewModel.clearTagFilter()
        }

        override fun setTagMatchMode(value: UserPreferences.MatchMode) {
            viewModel.setTagMatchMode(value)
        }

        override fun setTagSortingMode(value: UserPreferences.LabelSortingMode) {
            viewModel.setTagSortingMode(value)
        }

        override fun toggleLanguageFilter(code: String) {
            viewModel.toggleLanguageFilter(code)
        }

        override fun clearLanguageFilter() {
            viewModel.clearLanguageFilter()
        }

        override fun setLanguageMatchMode(value: UserPreferences.MatchMode) {
            viewModel.setLanguageMatchMode(value)
        }

        override fun setLanguageSortingMode(value: UserPreferences.LabelSortingMode) {
            viewModel.setLanguageSortingMode(value)
        }

        override fun clearSongFilter() {
            viewModel.clearSongFilter()
        }
    }
}
