/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SongLanguage
import com.pandulapeter.campfire.data.model.domain.Tag
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.data.repository.api.UserPreferencesRepository
import com.pandulapeter.campfire.domain.api.models.ScreenData
import com.pandulapeter.campfire.domain.api.models.SongFilter
import com.pandulapeter.campfire.domain.api.models.SongSection
import com.pandulapeter.campfire.domain.api.useCases.GetScreenDataUseCase
import com.pandulapeter.campfire.domain.api.useCases.NormalizeTextUseCase
import kotlin.concurrent.Volatile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Factory

@Factory
class GetScreenDataUseCaseImpl internal constructor(
    private val normalizeText: NormalizeTextUseCase,
    private val setlistRepository: SetlistRepository,
    private val songRepository: SongRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : GetScreenDataUseCase {

    /**
     * Written from the transform of the last [combine], which runs one emission at a time, so a collection never races
     * itself over it. It is marked volatile because that transform runs on [Dispatchers.Default] rather than on the
     * main thread, and a second collection of this use case (the demo library waits on one) may read it from another
     * thread.
     */
    @Volatile
    private var cache: ScreenData? = null

    /**
     * The library sorted for the last list of songs and sorting mode it was asked for, so that a change of the filters
     * sorts nothing. One reference to one immutable holder rather than a field per value: the view model collects this
     * use case twice at once, on [Dispatchers.Default] threads, and two fields written one after the other could be
     * read torn from another thread - one library paired with another library's order.
     */
    @Volatile
    private var sortMemo: SortMemo? = null

    /**
     * Built on [Dispatchers.Default] rather than wherever it is collected, which for the view model is the main thread:
     * normalizing, filtering, sorting and counting the whole library is one full pass per emission, and the first scan
     * of a large library publishes a partial list as it goes, each of which would be one more pass before the first
     * frame could respond.
     *
     * The songs and the setlists are built in two halves that are only put together at the end, so that each is built
     * again only when what it is made of changes: every tick in a setlist's song picker, every reorder and every step
     * of a transposition played from a setlist is a setlist write, and it must not have the whole library filtered and
     * sorted again for it.
     */
    override operator fun invoke(songFilter: Flow<SongFilter>): Flow<DataState<ScreenData>> {
        val preferences = userPreferencesRepository.userPreferences
        val songPart = combine(
            songRepository.songs,
            // Only the preferences the list is built from: a preference that changes on every step of a transposition
            // (or on every frame of a pinch, once the debounce lets it through) must not have the whole library
            // filtered and sorted again for it.
            preferences.map { state -> state.mapData { it.toSongListPreferences() }.orWhenUnreadable(DEFAULT_SONG_LIST_PREFERENCES) }
                .distinctUntilChanged(),
            songFilter.distinctUntilChanged(),
        ) { songsDataState, songListPreferencesDataState, filter ->
            listOf(songsDataState, songListPreferencesDataState).combinedState(
                songsDataState.data?.let { songs ->
                    songListPreferencesDataState.data?.let { songListPreferences -> songs.toSongPart(songListPreferences, filter) }
                },
            )
        }
        val setlistPart = combine(
            setlistRepository.setlists,
            preferences.map { state -> state.mapData { it.setlistSortingMode }.orWhenUnreadable(DEFAULT_SETLIST_SORTING_MODE) }
                .distinctUntilChanged(),
        ) { setlistsDataState, sortingModeDataState ->
            listOf(setlistsDataState, sortingModeDataState).combinedState(
                setlistsDataState.data?.let { setlists -> sortingModeDataState.data?.let { setlists.sortSetlists(it) } },
            )
        }
        return combine(setlistPart, songPart) { setlistsDataState, songsDataState ->
            val setlists = setlistsDataState.data
            val songPart = songsDataState.data
            val screenData = if (setlists != null && songPart != null) {
                ScreenData(
                    setlists = setlists,
                    songs = songPart.songs,
                    songSections = songPart.songSections,
                    tags = songPart.tags,
                    languages = songPart.languages,
                    unfilteredSongs = songPart.unfilteredSongs,
                    sortedSongs = songPart.sortedSongs,
                    songFilter = songPart.songFilter,
                    sortingMode = songPart.sortingMode,
                    tagMatchMode = songPart.tagMatchMode,
                    languageMatchMode = songPart.languageMatchMode,
                ).also {
                    cache = it
                }
            } else {
                null
            }
            listOf(setlistsDataState, songsDataState).combinedState(
                screenData ?: cache ?: partialScreenData(setlistsDataState, songsDataState),
            )
        }.flowOn(Dispatchers.Default).distinctUntilChanged()
    }

    /**
     * The part that was read, with the part whose first read failed standing in empty, so that one unreadable
     * directory does not keep the other one's screen empty. Null while either part is still being read for the
     * first time: filling that in would put something on screen that the real data then replaces. Never cached,
     * since it is not the library.
     */
    private fun partialScreenData(setlistsDataState: DataState<List<Setlist>>, songsDataState: DataState<SongPart>): ScreenData? {
        val setlists = setlistsDataState.data ?: emptyList<Setlist>().takeIf { setlistsDataState is DataState.Failure } ?: return null
        val songPart = songsDataState.data ?: EMPTY_SONG_PART.takeIf { songsDataState is DataState.Failure } ?: return null
        return ScreenData(
            setlists = setlists,
            songs = songPart.songs,
            songSections = songPart.songSections,
            tags = songPart.tags,
            languages = songPart.languages,
            unfilteredSongs = songPart.unfilteredSongs,
            sortedSongs = songPart.sortedSongs,
            isWholeLibrary = false,
            songFilter = songPart.songFilter,
            sortingMode = songPart.sortingMode,
            tagMatchMode = songPart.tagMatchMode,
            languageMatchMode = songPart.languageMatchMode,
        )
    }

    private fun List<Song>.toSongPart(songListPreferences: SongListPreferences, filter: SongFilter): SongPart {
        // What the library holds, whatever is selected: the two filter groups are counted over the songs the other one
        // leaves, but both of them decide what is still a tag and what is still a language from here, or narrowing by
        // one would quietly switch the other one off.
        val availableTags = toTags(normalizeText::invoke)
        val availableLanguages = toLanguages()
        // The filters are predicates over the library in its order, so what they leave is already sorted.
        val sorted = sorted(songListPreferences.sortingMode)
        val songsByTag = sorted.songs.filterTags(filter, songListPreferences.tagMatchMode, availableTags)
        val songsByLanguage = sorted.songs.filterLanguages(filter, songListPreferences.languageMatchMode, availableLanguages)
        val songSections = songsByTag
            .filterLanguages(filter, songListPreferences.languageMatchMode, availableLanguages)
            .map { sorted.byFileName.getValue(it.fileName) }
            .cutIntoSections(songListPreferences.sortingMode)
        return SongPart(
            songs = songSections.flatMap { it.songs },
            songSections = songSections,
            tags = availableTags.recountedTagsOver(songsByLanguage, normalizeText::invoke),
            languages = availableLanguages.recountedLanguagesOver(songsByTag),
            unfilteredSongs = this,
            sortedSongs = sorted.songs,
            songFilter = filter,
            sortingMode = songListPreferences.sortingMode,
            tagMatchMode = songListPreferences.tagMatchMode,
            languageMatchMode = songListPreferences.languageMatchMode,
        )
    }

    /** The song half of [ScreenData], see there. */
    private class SongPart(
        val songs: List<Song>,
        val songSections: List<SongSection>,
        val tags: List<Tag>,
        val languages: List<SongLanguage>,
        val unfilteredSongs: List<Song>,
        val sortedSongs: List<Song>,
        val songFilter: SongFilter,
        val sortingMode: UserPreferences.SortingMode,
        val tagMatchMode: UserPreferences.MatchMode,
        val languageMatchMode: UserPreferences.MatchMode,
    )

    /**
     * The setlists in the order the screen lists them. The archived ones come last whichever order that is: they are
     * only on the screen at all because the user asked to see what has been put away, and mixing them in among the
     * setlists still in use would undo the putting away.
     *
     * By date the latest day is on top, and the setlists of one day are in the order of their titles, as they are in
     * the other order. Both orders end in the file name, which never ties. Titles do, and what decides a tie otherwise is the order of the repository's list, where a setlist
     * moves to the end every time it is written: the two would trade places on the screen whenever one of them was
     * touched.
     */
    private fun List<Setlist>.sortSetlists(sortingMode: UserPreferences.SetlistSortingMode) = sortedWith(
        when (sortingMode) {
            UserPreferences.SetlistSortingMode.BY_DATE -> compareBy<Setlist> { it.isArchived }
                .thenByDescending { it.date }

            UserPreferences.SetlistSortingMode.BY_TITLE -> compareBy<Setlist> { it.isArchived }
        }.thenBy { normalizeText(it.title) }.thenBy { it.fileName }
    )

    /** The library sorted for [sortingMode], sorted again only when the library or the mode has changed (see [sortMemo]). */
    private fun List<Song>.sorted(sortingMode: UserPreferences.SortingMode): SortMemo =
        sortMemo?.takeIf { it.input === this && it.sortingMode == sortingMode }
            ?: sortedFor(sortingMode, normalizeText::invoke).also { sortMemo = it }

    /** The part of the preferences the song list depends on. */
    private data class SongListPreferences(
        val sortingMode: UserPreferences.SortingMode,
        val tagMatchMode: UserPreferences.MatchMode,
        val languageMatchMode: UserPreferences.MatchMode,
    )

    private fun UserPreferences.toSongListPreferences() = SongListPreferences(
        sortingMode = sortingMode,
        tagMatchMode = tagMatchMode,
        languageMatchMode = languageMatchMode,
    )

    private companion object {

        /** What `UserPreferencesDocument().toModel()` gives a device that has never saved any, see `:data:source:local`. */
        val DEFAULT_SONG_LIST_PREFERENCES = SongListPreferences(
            sortingMode = UserPreferences.SortingMode.BY_ARTIST,
            tagMatchMode = UserPreferences.MatchMode.ANY,
            languageMatchMode = UserPreferences.MatchMode.ANY,
        )
        val DEFAULT_SETLIST_SORTING_MODE = UserPreferences.SetlistSortingMode.BY_DATE
        val EMPTY_SONG_PART = SongPart(
            songs = emptyList(),
            songSections = emptyList(),
            tags = emptyList(),
            languages = emptyList(),
            unfilteredSongs = emptyList(),
            sortedSongs = emptyList(),
            songFilter = SongFilter(),
            sortingMode = DEFAULT_SONG_LIST_PREFERENCES.sortingMode,
            tagMatchMode = DEFAULT_SONG_LIST_PREFERENCES.tagMatchMode,
            languageMatchMode = DEFAULT_SONG_LIST_PREFERENCES.languageMatchMode,
        )
    }
}
