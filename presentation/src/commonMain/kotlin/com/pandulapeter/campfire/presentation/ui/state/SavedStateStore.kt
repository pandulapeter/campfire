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

import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import com.pandulapeter.campfire.domain.api.models.SongFilter
import com.pandulapeter.campfire.presentation.ui.components.SearchState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * What survives the system killing the process while the app is in the background, which Android does whenever it
 * needs the memory: the back stack, the song filter and the two searches, kept in [savedStateHandle] as JSON. Empty on
 * every real start, and on the platforms that have no such thing as a process being restored.
 */
internal class SavedStateStore(
    @PublishedApi internal val savedStateHandle: SavedStateHandle,
    private val scope: CoroutineScope,
) {

    /**
     * Keeps the song filter, the song picker's chips and the two searches written as they change. One call for all of
     * them, since their collectors are launched one after the other.
     */
    fun startPersisting(
        songFilter: Flow<SongFilter>,
        songPickerSelectedTags: Flow<Set<String>>,
        songPickerSelectedLanguages: Flow<Set<String>>,
        songsSearch: SearchState,
        setlistsSearch: SearchState,
    ) {
        scope.launch {
            songFilter.collect { persist(SONG_FILTER_KEY, SavedSongFilter(selectedTags = it.selectedTags.toList(), selectedLanguages = it.selectedLanguages.toList())) }
        }
        scope.launch {
            songPickerSelectedTags.collect { persist(SONG_PICKER_TAGS_KEY, it.toList()) }
        }
        scope.launch {
            songPickerSelectedLanguages.collect { persist(SONG_PICKER_LANGUAGES_KEY, it.toList()) }
        }
        listOf(SONGS_SEARCH_KEY to songsSearch, SETLISTS_SEARCH_KEY to setlistsSearch).forEach { (key, search) ->
            scope.launch {
                combine(search.isOpen, snapshotFlow { search.textFieldState.text.toString() }) { isOpen, query -> SavedSearch(isOpen = isOpen, query = query) }
                    .collect { persist(key, it) }
            }
        }
    }

    fun restoreSearch(key: String) = restore<SavedSearch>(key).let { saved ->
        SearchState(isInitiallyOpen = saved?.isOpen == true, initialQuery = saved?.query.orEmpty())
    }

    /** JSON rather than the values themselves, because a saved state only takes the handful of types a Bundle does. */
    inline fun <reified T> persist(key: String, value: T) {
        persistJson(key, Json.encodeToString(value))
    }

    /** [json] as it is, for a caller that has encoded it itself to see whether it fits. */
    fun persistJson(key: String, json: String) {
        savedStateHandle[key] = json
    }

    /** Null for nothing saved, and for something saved by a build whose destinations no longer read the same way. */
    inline fun <reified T> restore(key: String): T? = savedStateHandle.get<String>(key)?.let { saved ->
        try {
            Json.decodeFromString<T>(saved)
        } catch (exception: SerializationException) {
            println("Could not restore \"$key\": ${exception.message}")
            null
        }
    }

    /** The part of a [SearchState] that is worth restoring. */
    @Serializable
    private data class SavedSearch(
        val isOpen: Boolean,
        val query: String,
    )

    /** [SongFilter] as it is saved; the domain model is not serializable, and has no reason to be. */
    @Serializable
    data class SavedSongFilter(
        val selectedTags: List<String>,
        val selectedLanguages: List<String>,
    )

    companion object {
        const val BACK_STACK_KEY = "backStack"
        const val SONG_FILTER_KEY = "songFilter"
        const val SONG_PICKER_TAGS_KEY = "songPickerTags"
        const val SONG_PICKER_LANGUAGES_KEY = "songPickerLanguages"
        const val SONGS_SEARCH_KEY = "songsSearch"
        const val SETLISTS_SEARCH_KEY = "setlistsSearch"
    }
}
