/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.SongLanguage
import com.pandulapeter.campfire.data.model.domain.Tag
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.domain.api.models.ScreenData
import com.pandulapeter.campfire.domain.api.models.SongFilter
import com.pandulapeter.campfire.domain.api.useCases.NormalizeSearchTextUseCase
import com.pandulapeter.campfire.presentation.ui.components.Placeholder
import com.pandulapeter.campfire.presentation.ui.components.ScrollPosition
import com.pandulapeter.campfire.presentation.ui.components.SearchState
import com.pandulapeter.campfire.presentation.ui.screens.songs.SongGroups
import com.pandulapeter.campfire.presentation.ui.search.songGroupsFor
import com.pandulapeter.campfire.presentation.ui.state.SavedStateStore.Companion.SONGS_SEARCH_KEY
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update

/**
 * The songs screen: its search, its scroll position, the tag and language filters and what the list shows.
 *
 * @param mutableSongFilter The filter, shared with [LibraryState], which builds the library from it.
 * @param isImporting Whether an import is running, which an empty library is waiting for.
 * @param changeUserPreferences See [PreferencesController.changeUserPreferences].
 */
internal class SongListState(
    private val scope: CoroutineScope,
    savedStateStore: SavedStateStore,
    private val mutableSongFilter: MutableStateFlow<SongFilter>,
    screenData: StateFlow<DataState<ScreenData>>,
    indexedSongs: StateFlow<LibraryState.IndexedSongs>,
    private val tags: StateFlow<List<Tag>>,
    private val languages: StateFlow<List<SongLanguage>>,
    isImporting: StateFlow<Boolean>,
    private val normalizeSearchText: NormalizeSearchTextUseCase,
    private val changeUserPreferences: (UserPreferences.() -> UserPreferences) -> Unit,
) {

    val songsScrollPosition = ScrollPosition()

    /**
     * The search of each of the two list screens, held here for the same reason their scroll positions are, see
     * [SearchState]. They are separate because the two lists are searched for different things.
     */
    val songsSearch = savedStateStore.restoreSearch(SONGS_SEARCH_KEY)

    val songFilter = mutableSongFilter.asStateFlow()

    /**
     * Whether the filter controls have anything to offer: a tag, or a choice between two languages. Without either,
     * the songs screen leaves out both the controls and the action that opens them rather than showing an empty sheet.
     */
    val hasSongFilters = combine(tags, languages) { tags, languages -> tags.isNotEmpty() || languages.size > 1 }.asState(scope, false)

    /**
     * Whether the song list is narrowed by anything the filter controls show as selected, which is what the badge on
     * their app bar action says while they are out of sight. It asks the chips rather than [songFilter]: a selected
     * tag the library no longer has is kept but narrows nothing, and the language group is only offered at all once
     * there are two languages to choose between, so a badge counting either would point at a filter that cannot be
     * found in the controls it opens.
     */
    val isSongFilterActive = combine(songFilter, tags, languages) { filter, tags, languages ->
        val selectedTags = filter.selectedTags.mapTo(mutableSetOf()) { it.lowercase() }
        tags.any { it.name.lowercase() in selectedTags } || (languages.size > 1 && languages.any { it.code in filter.selectedLanguages })
    }.asState(scope, false)

    // The sections arrive cut, from the same pass that sorted them. Cutting them here would take the sorting mode
    // from the preferences, which change before the list sorted by them arrives.
    val songGroups = combine(indexedSongs, songsSearch.activeQuery) { indexed, query ->
        val normalizedQuery = normalizeSearchText(query)
        SongGroups(
            filterKey = "$normalizedQuery|${indexed.filterKey}",
            groups = songGroupsFor(sections = indexed.sections, filtered = indexed.search.filtered, normalizedQuery = normalizedQuery),
        )
    }.flowOn(Dispatchers.Default).asState(scope, SongGroups(filterKey = "", groups = emptyList()))

    /**
     * What the song list has to show instead of songs, null while it has songs. A library that is empty because
     * the search matched nothing is told apart from one that is empty because the load has not finished (or has
     * failed) here, so that a list without data never sits on a loading indicator that nothing will ever replace.
     */
    val songsPlaceholder = combine(screenData, songGroups, isImporting) { screenData, songGroups, isImporting ->
        songListPlaceholder(screenData = screenData, songGroups = songGroups, isImporting = isImporting)
    }.asState(scope, Placeholder.LOADING)

    /** A selected tag is matched the way the filter itself matches it, without regard to case. */
    fun toggleTagFilter(tag: String) = mutableSongFilter.update { filter ->
        val without = filter.selectedTags.filterNotTo(mutableSetOf()) { it.equals(tag, ignoreCase = true) }
        filter.copy(selectedTags = if (without.size == filter.selectedTags.size) filter.selectedTags + tag else without)
    }

    /** Only the tags the library still has are cleared: a selection this screen never showed is not a tap's to lose. */
    fun clearTagFilter() = mutableSongFilter.update { filter ->
        val libraryTags = tags.value.mapTo(mutableSetOf()) { it.name.lowercase() }
        filter.copy(selectedTags = filter.selectedTags.filterNotTo(mutableSetOf()) { it.lowercase() in libraryTags })
    }

    fun setTagMatchMode(value: UserPreferences.MatchMode) = changeUserPreferences { copy(tagMatchMode = value) }

    fun setLanguageMatchMode(value: UserPreferences.MatchMode) = changeUserPreferences { copy(languageMatchMode = value) }

    /** The codes are normalized by the parser, so a selected language is the string the filter chip carries. */
    fun toggleLanguageFilter(code: String) = mutableSongFilter.update { filter ->
        filter.copy(
            selectedLanguages = if (code in filter.selectedLanguages) filter.selectedLanguages - code else filter.selectedLanguages + code,
        )
    }

    /** Only the languages the library still has are cleared, for the same reason [clearTagFilter] is careful. */
    fun clearLanguageFilter() = mutableSongFilter.update { filter ->
        val libraryLanguages = languages.value.mapTo(mutableSetOf()) { it.code }
        filter.copy(selectedLanguages = filter.selectedLanguages.filterNotTo(mutableSetOf()) { it in libraryLanguages })
    }

    /**
     * Both groups at once, each as careful as its own clear action, so that what is reset is what [isSongFilterActive]
     * counted. One update rather than the two clear actions in a row, which would filter the list twice and could show
     * it with only the tags reset for a frame.
     */
    fun clearSongFilter() = mutableSongFilter.update { filter ->
        val libraryTags = tags.value.mapTo(mutableSetOf()) { it.name.lowercase() }
        val libraryLanguages = languages.value.mapTo(mutableSetOf()) { it.code }
        filter.copy(
            selectedTags = filter.selectedTags.filterNotTo(mutableSetOf()) { it.lowercase() in libraryTags },
            selectedLanguages = filter.selectedLanguages.filterNotTo(mutableSetOf()) { it in libraryLanguages },
        )
    }
}
