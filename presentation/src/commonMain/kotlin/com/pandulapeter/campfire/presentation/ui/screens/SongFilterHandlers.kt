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
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.components.songFilters.SongFilterActions
import com.pandulapeter.campfire.presentation.ui.components.songFilters.SongFilterUiState
import com.pandulapeter.campfire.presentation.ui.state.LibraryState
import com.pandulapeter.campfire.presentation.ui.state.PreferencesController
import com.pandulapeter.campfire.presentation.ui.state.songs.SongListState
import kotlinx.coroutines.flow.StateFlow

/**
 * What [com.pandulapeter.campfire.presentation.ui.components.songFilters.SongFilters] shows in the filters sheet, as
 * the song list and the preferences hold it.
 */
@Composable
internal fun rememberSongFilterUiState(
    userPreferencesFlow: StateFlow<UserPreferences?>,
    songListState: SongListState,
    libraryState: LibraryState,
): SongFilterUiState {
    val userPreferences by userPreferencesFlow.collectAsStateWithLifecycle()
    val songFilter by songListState.songFilter.collectAsStateWithLifecycle()
    val tags by libraryState.tags.collectAsStateWithLifecycle()
    val languages by libraryState.languages.collectAsStateWithLifecycle()
    val isSongFilterActive by songListState.isSongFilterActive.collectAsStateWithLifecycle()
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

/** The [SongFilterActions] that hand every control of the song filters to the holders that answer it. */
@Composable
internal fun rememberSongFilterActions(
    songListState: SongListState,
    preferencesController: PreferencesController,
): SongFilterActions = remember(songListState, preferencesController) {
    object : SongFilterActions {
        override fun toggleTagFilter(tag: String) {
            songListState.toggleTagFilter(tag)
        }

        override fun clearTagFilter() {
            songListState.clearTagFilter()
        }

        override fun setTagMatchMode(value: UserPreferences.MatchMode) {
            songListState.setTagMatchMode(value)
        }

        override fun setTagSortingMode(value: UserPreferences.LabelSortingMode) {
            preferencesController.setTagSortingMode(value)
        }

        override fun toggleLanguageFilter(code: String) {
            songListState.toggleLanguageFilter(code)
        }

        override fun clearLanguageFilter() {
            songListState.clearLanguageFilter()
        }

        override fun setLanguageMatchMode(value: UserPreferences.MatchMode) {
            songListState.setLanguageMatchMode(value)
        }

        override fun setLanguageSortingMode(value: UserPreferences.LabelSortingMode) {
            preferencesController.setLanguageSortingMode(value)
        }

        override fun clearSongFilter() {
            songListState.clearSongFilter()
        }
    }
}
