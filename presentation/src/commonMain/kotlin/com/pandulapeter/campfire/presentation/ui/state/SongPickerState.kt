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

import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.presentation.ui.search.PickerFilterOptions
import com.pandulapeter.campfire.presentation.ui.search.PickerSongs
import com.pandulapeter.campfire.presentation.ui.search.pickerFilterOptions
import com.pandulapeter.campfire.presentation.ui.search.toPickableSong
import com.pandulapeter.campfire.presentation.ui.state.SavedStateStore.Companion.SONG_PICKER_LANGUAGES_KEY
import com.pandulapeter.campfire.presentation.ui.state.SavedStateStore.Companion.SONG_PICKER_TAGS_KEY
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** The song picker sheet's songs and filter chips, and the chips it has ticked, which outlive the sheet for the session. */
internal class SongPickerState(
    private val scope: CoroutineScope,
    savedStateStore: SavedStateStore,
    indexedSongs: StateFlow<LibraryState.IndexedSongs>,
    allSongs: StateFlow<List<Song>>,
) {

    /** Assignment-sheet tags survive reopening the sheet, independently of the main Songs filter. Same run lifetime. */
    private val _songPickerSelectedTags = MutableStateFlow<Set<String>>(
        savedStateStore.restore<List<String>>(SONG_PICKER_TAGS_KEY).orEmpty().mapTo(mutableSetOf()) { it.lowercase() }
    )

    val songPickerSelectedTags = _songPickerSelectedTags.asStateFlow()

    /** Assignment-sheet languages have the same independent session lifetime as its tags. */
    private val _songPickerSelectedLanguages = MutableStateFlow<Set<String>>(
        savedStateStore.restore<List<String>>(SONG_PICKER_LANGUAGES_KEY).orEmpty().toSet()
    )

    val songPickerSelectedLanguages = _songPickerSelectedLanguages.asStateFlow()

    /**
     * Every song of the library in the order the songs screen is sorted by, with its search and filter keys, as the song
     * picker lists and searches it. Built here rather than as the sheet opens, where the first frame of the sheet would
     * wait for a whole library to be folded. The order is the domain layer's (`ScreenData.sortedSongs`), which sorts
     * the library once for the songs screen and the picker alike, and comes with the search index of the same value.
     */
    val pickerSongs = indexedSongs.map { indexed ->
        val list = indexed.sorted.mapNotNull { indexed.search.byFileName[it.fileName] }.map { it.toPickableSong() }
        PickerSongs(list = list, byFileName = list.associateBy { it.song.fileName })
    }
        .flowOn(Dispatchers.Default)
        .asState(scope, PickerSongs.Empty)

    /** The song picker's filter chips, counted over the whole library for the same reason [pickerSongs] is sorted here. */
    val songPickerFilters = allSongs
        .map(::pickerFilterOptions)
        .flowOn(Dispatchers.Default)
        .asState(scope, PickerFilterOptions.Empty)

    fun toggleSongPickerTag(tag: String) = _songPickerSelectedTags.update { selected ->
        val key = tag.lowercase()
        if (key in selected) selected - key else selected + key
    }

    fun toggleSongPickerLanguage(code: String) = _songPickerSelectedLanguages.update { selected ->
        if (code in selected) selected - code else selected + code
    }
}
