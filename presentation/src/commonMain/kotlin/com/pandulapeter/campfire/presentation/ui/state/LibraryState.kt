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
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.domain.api.models.ScreenData
import com.pandulapeter.campfire.domain.api.models.SongFilter
import com.pandulapeter.campfire.domain.api.models.SongSection
import com.pandulapeter.campfire.domain.api.useCases.GetScreenDataUseCase
import com.pandulapeter.campfire.domain.api.useCases.LoadScreenDataUseCase
import com.pandulapeter.campfire.domain.api.useCases.NormalizeSearchTextUseCase
import com.pandulapeter.campfire.presentation.ui.components.LabelsOnEverySong
import com.pandulapeter.campfire.presentation.ui.screens.settings.LibrarySummary
import com.pandulapeter.campfire.presentation.ui.screens.songs.SongGroups
import com.pandulapeter.campfire.presentation.ui.search.SongSearchIndex
import com.pandulapeter.campfire.presentation.ui.search.SongSearchSnapshot
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The library as the app shows it, read once into memory and kept in step with the files: the songs, the setlists and
 * the folded search keys of every song, from the one subscription to the domain layer.
 */
internal class LibraryState(
    private val scope: CoroutineScope,
    getScreenData: GetScreenDataUseCase,
    songFilter: StateFlow<SongFilter>,
    private val loadScreenData: LoadScreenDataUseCase,
    private val normalizeSearchText: NormalizeSearchTextUseCase,
) {

    /**
     * The single subscription to the domain layer: every state below maps over this instead of over
     * [GetScreenDataUseCase] directly, which would re-run the whole repository combine once per state. Started
     * eagerly so that the data is loaded into memory as the app starts, rather than when a screen first asks for it.
     */
    val screenData: StateFlow<DataState<ScreenData>> = getScreenData(songFilter).stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        // Loading, not Failure: nothing has been asked for yet, which is not something to show an error for.
        initialValue = DataState.Loading(null),
    )

    // Data
    /**
     * True while the library is being read for the first time, its partial batches included. A rescan of a library
     * that has been read once is not a loading state: it publishes no partial data, so what is on screen meanwhile is
     * the previous, complete library, and flipping this would only recompose every screen twice for nothing. A first
     * read that failed has not been read, so the retry after it still shows loading.
     */
    val isLoading = screenData
        .runningFold(LoadingLatch(isLoading = true, hasBeenRead = false)) { latch, state ->
            val hasBeenRead = latch.hasBeenRead || state is DataState.Idle
            LoadingLatch(isLoading = state is DataState.Loading && !hasBeenRead, hasBeenRead = hasBeenRead)
        }
        .map { it.isLoading }
        .asState(scope, true)

    val setlists = screenData.map { it.data?.setlists.orEmpty() }.asState(scope, emptyList())

    /**
     * The file names of every song that is in at least one setlist, which is what decides whether the "add to
     * setlist" action is drawn as a filled star or an outlined one. Worked out once per change to the setlists
     * rather than per song shown, since every row of the song list asks the same question.
     */
    val songFileNamesInSetlists = setlists
        .map { setlists -> setlists.flatMapTo(mutableSetOf()) { setlist -> setlist.entries.map { it.songFileName } } }
        .asState(scope, emptySet())

    /**
     * The whole library, whatever the filters hide, which is what everything that looks a song up by its file name
     * reads: a setlist lists what somebody wrote down rather than what the song list is currently narrowed to, and
     * the details screen it opens has to find every one of those songs.
     */
    val allSongs = screenData.map { it.data?.unfilteredSongs.orEmpty() }.asState(scope, emptyList())

    /**
     * The labels every song in the library carries, which the song rows leave off: a tag that is on every song tells
     * one song from no other, and a library that sings in one language has nothing to mark a song with. Counted over
     * the whole library rather than over what the filters leave, since a tag filter narrows the list to songs that
     * all carry that tag, and the rows would then lose the very label the reader narrowed them by. Tags are folded
     * to lowercase the way the filters count them, so two spellings of one word are one tag here too.
     */
    val labelsOnEverySong = allSongs.map { songs ->
        // Folded one song at a time, stopping at the first song that leaves both empty, which in most libraries is the
        // second one.
        var tags: Set<String>? = null
        var languages: Set<String>? = null
        for (song in songs) {
            if (tags?.isEmpty() != true) song.tags.mapTo(HashSet()) { it.lowercase() }.let { tags = tags?.intersect(it) ?: it }
            if (languages?.isEmpty() != true) song.languages.toSet().let { languages = languages?.intersect(it) ?: it }
            if (tags?.isEmpty() == true && languages?.isEmpty() == true) break
        }
        LabelsOnEverySong(tags = tags.orEmpty(), languages = languages.orEmpty())
    }.flowOn(Dispatchers.Default).asState(scope, LabelsOnEverySong())

    /**
     * Every tag the library uses, most used first, as both the filter controls and the suggestions of the tag
     * dialog offer them.
     */
    val tags = screenData.map { it.data?.tags.orEmpty() }.asState(scope, emptyList())

    /**
     * Every language the library sings in, most used first and the songs that declare none last, as the filter
     * controls offer them. Empty, or a single entry, is a library with nothing to filter by.
     */
    val languages = screenData.map { it.data?.languages.orEmpty() }.asState(scope, emptyList())

    /**
     * The library as the song list shows it and as the search reads it, taken from one [screenData] value: the sections
     * as the domain layer cut them, the filtered songs with their title, artist and tags normalized for searching, and
     * the whole library by file name, which is what the setlists and the song details screen read - a setlist names its
     * songs whatever the song filters hide. Built from one value so that a library change can never pair a new filtered
     * list with an old lookup, and normalized once per library rather than once per keystroke, a song whose searchable
     * text did not change keeping what it was folded to.
     *
     * Distinct, because [screenData] also emits for every write to a setlist with the songs exactly as they were, and
     * each of those would otherwise have the whole library indexed again for nothing. Built on [Dispatchers.Default],
     * as the domain layer builds [screenData], since a whole library is too much to fold between two frames.
     */
    private val songSearchIndex = SongSearchIndex { normalizeSearchText(it) }

    val indexedSongs = screenData.map { state ->
        val data = state.data
        IndexedSongInput(
            all = data?.unfilteredSongs.orEmpty(),
            filtered = data?.songs.orEmpty(),
            sections = data?.songSections.orEmpty(),
            sorted = data?.sortedSongs.orEmpty(),
            filterKey = data?.let {
                "${it.sortingMode.name}|${it.songFilter.selectedTags.sorted()}|${it.tagMatchMode.name}|" +
                    "${it.songFilter.selectedLanguages.sorted()}|${it.languageMatchMode.name}"
            }.orEmpty(),
        )
    }.distinctUntilChanged().map { input ->
        IndexedSongs(input.sections, songSearchIndex.update(input.all, input.filtered), input.filterKey, input.sorted)
    }.flowOn(Dispatchers.Default).asState(scope, IndexedSongs(emptyList(), SongSearchSnapshot.Empty, "", emptyList()))

    /** Shared file-name lookup for screens that resolve songs from a destination or a setlist. */
    val songsByFileName = indexedSongs.map { it.search.songsByFileName }.asState(scope, emptyMap())

    /**
     * Null until the library has actually been read, so that the settings screen never flashes a count of zero. The size
     * is added up from what the scan read off every file, so it costs no listing of its own and arrives in the same value
     * as the counts.
     */
    val librarySummary = screenData
        .map { state ->
            // A library with an unreadable part standing in empty is not one to count.
            state.data?.takeIf { it.isWholeLibrary }?.let { data ->
                LibrarySummary(
                    songCount = data.unfilteredSongs.size,
                    setlistCount = data.setlists.size,
                    size = data.unfilteredSongs.sumOf { it.size } + data.setlists.sumOf { it.size },
                )
            }
        }
        .asState(scope, null)

    /** When the last rescan started or, once it has finished, finished; see [refreshIfStale]. */
    private var lastRescanAt: TimeMark? = null

    fun refresh() = scope.launch {
        // Marked as it starts as well, so that the focus that follows a start, which comes right behind it on the
        // desktop, does not ask for a second rescan while the first is still running.
        lastRescanAt = TimeSource.Monotonic.markNow()
        loadScreenData(true)
        lastRescanAt = TimeSource.Monotonic.markNow()
    }

    /**
     * [refresh], unless the library has been read again within the last [MIN_RESCAN_INTERVAL]: for the desktop
     * window regaining the focus, which a user editing a song in another window next to it does often, and each time
     * of which re-reading the whole library would be a cost with nothing new to show for it.
     */
    fun refreshIfStale() {
        if (lastRescanAt?.let { it.elapsedNow() < MIN_RESCAN_INTERVAL } == true) return
        refresh()
    }

    /** Reads the library for the first time. */
    fun startLoading() = scope.launch { loadScreenData(false) }

    /** The state [isLoading] is folded from. */
    private data class LoadingLatch(
        val isLoading: Boolean,
        val hasBeenRead: Boolean,
    )

    /** @param filterKey The filter and the preferences [filtered] was built for, see [SongGroups]. */
    private data class IndexedSongInput(
        val all: List<Song>,
        val filtered: List<Song>,
        val sections: List<SongSection>,
        val sorted: List<Song>,
        val filterKey: String,
    )

    /** @param sorted The whole library in the songs screen's order, see [pickerSongs]. */
    data class IndexedSongs(
        val sections: List<SongSection>,
        val search: SongSearchSnapshot,
        val filterKey: String,
        val sorted: List<Song>,
    )

    private companion object {
        val MIN_RESCAN_INTERVAL = 10.seconds
    }
}
