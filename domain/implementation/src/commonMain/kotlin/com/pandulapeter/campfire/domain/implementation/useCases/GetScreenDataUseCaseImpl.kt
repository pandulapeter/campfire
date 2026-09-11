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
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SongLanguage
import com.pandulapeter.campfire.data.model.domain.Tag
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.data.repository.api.UserPreferencesRepository
import com.pandulapeter.campfire.domain.api.models.ScreenData
import com.pandulapeter.campfire.domain.api.useCases.GetScreenDataUseCase
import com.pandulapeter.campfire.domain.api.useCases.NormalizeTextUseCase
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class GetScreenDataUseCaseImpl internal constructor(
    private val normalizeText: NormalizeTextUseCase,
    setlistRepository: SetlistRepository,
    songRepository: SongRepository,
    userPreferencesRepository: UserPreferencesRepository,
) : GetScreenDataUseCase {

    override operator fun invoke() = screenDataFlow

    private var cache: ScreenData? = null
    private val screenDataFlow = combine(
        setlistRepository.setlists,
        songRepository.songs,
        // Only the two preferences the list is built from: a preference that changes on every step of a transposition
        // (or on every frame of a pinch, once the debounce lets it through) must not have the whole library filtered
        // and sorted again for it.
        userPreferencesRepository.userPreferences.map { state -> state.mapData { it.toListPreferences() } }.distinctUntilChanged(),
    ) { setlistsDataState, songsDataState, listPreferencesDataState ->

        fun createScreenData() = setlistsDataState.data?.sortedByDescending { it.priority }?.let { setlists ->
            songsDataState.data?.let { songs ->
                listPreferencesDataState.data?.let { listPreferences ->
                    val filterableSongs = songs.filterHasChords(listPreferences)
                    // What the library holds, whatever is selected: the two filter groups are counted over the songs
                    // the other one leaves, but both of them decide what is still a tag and what is still a language
                    // from here, or narrowing by one would quietly switch the other one off.
                    val availableTags = filterableSongs.toTags()
                    val availableLanguages = filterableSongs.toLanguages()
                    val songsByTag = filterableSongs.filterTags(listPreferences, availableTags)
                    val songsByLanguage = filterableSongs.filterLanguages(listPreferences, availableLanguages)
                    ScreenData(
                        setlists = setlists,
                        songs = songsByTag
                            .filterLanguages(listPreferences, availableLanguages)
                            .sort(listPreferences),
                        tags = songsByLanguage.toTags().withMissingSelected(
                            available = availableTags,
                            selected = listPreferences.selectedTags.mapTo(mutableSetOf()) { it.lowercase() },
                            key = { it.name.lowercase() },
                            toEmpty = { it.copy(songCount = 0) },
                        ),
                        languages = songsByTag.toLanguages().withMissingSelected(
                            available = availableLanguages,
                            selected = listPreferences.selectedLanguages,
                            key = { it.code },
                            toEmpty = { it.copy(songCount = 0) },
                        ),
                        songFileNames = songs.mapTo(mutableSetOf()) { it.fileName },
                    ).also {
                        cache = it
                    }
                }
            }
        }

        val dataStates = arrayOf(setlistsDataState, songsDataState, listPreferencesDataState)
        if (dataStates.any { it is DataState.Failure }) {
            DataState.Failure(createScreenData() ?: cache)
        } else if (dataStates.any { it is DataState.Loading }) {
            DataState.Loading(createScreenData() ?: cache)
        } else {
            DataState.Idle(createScreenData() ?: cache ?: throw IllegalStateException("No data available while all data states are idle."))
        }
    }.distinctUntilChanged()

    private fun List<Song>.filterHasChords(listPreferences: ListPreferences) = if (listPreferences.shouldShowSongsWithoutChords) this else filter { it.hasChords }

    /**
     * Tags are matched without regard to case, here and everywhere else, so both sides are folded to lower case
     * before they meet. A selected tag no song carries any more is dropped instead of emptying the list: it is kept
     * in the preferences on purpose, see [UserPreferences.selectedTags].
     */
    private fun List<Song>.filterTags(listPreferences: ListPreferences, tags: List<Tag>): List<Song> {
        val available = tags.mapTo(mutableSetOf()) { it.name.lowercase() }
        val selected = listPreferences.selectedTags.map { it.lowercase() }.filter { it in available }
        if (selected.isEmpty()) return this
        return filter { song ->
            val songTags = song.tags.mapTo(mutableSetOf()) { it.lowercase() }
            when (listPreferences.tagMatchMode) {
                UserPreferences.TagMatchMode.ANY -> selected.any { it in songTags }
                UserPreferences.TagMatchMode.ALL -> selected.all { it in songTags }
            }
        }
    }

    /**
     * The songs left by the language filter. A song carries its languages the way it carries its tags, so several
     * selected languages mean a song sung in any one of them; [SongLanguage.UNKNOWN] selects the songs that declare
     * none, which no song can name itself, see [SongLanguage.Companion.UNKNOWN].
     */
    private fun List<Song>.filterLanguages(listPreferences: ListPreferences, languages: List<SongLanguage>): List<Song> {
        val available = languages.mapTo(mutableSetOf()) { it.code }
        val selected = listPreferences.selectedLanguages.filter { it in available }
        if (selected.isEmpty()) return this
        return filter { song ->
            if (song.languages.isEmpty()) SongLanguage.UNKNOWN in selected else selected.any { it in song.languages }
        }
    }

    /**
     * A filter group as its controls show it, put back together after the other group has narrowed the songs it was
     * counted over: whatever the user has selected and the narrowing counted down to nothing is appended with a
     * count of zero. A filter that is on has to stay visible to be turned off, and a zero says exactly what it
     * means — this combination of the two groups selects nothing.
     */
    private fun <T> List<T>.withMissingSelected(available: List<T>, selected: Set<String>, key: (T) -> String, toEmpty: (T) -> T): List<T> {
        val counted = mapTo(mutableSetOf(), key)
        return this + available.filter { key(it) in selected && key(it) !in counted }.map(toEmpty)
    }

    /**
     * The languages of the library with the number of songs singing in each, most used first, and the songs that
     * declare none after them however many they are: "unknown" is where the work that is still to be done sits,
     * not a language competing with the rest for the top of the list.
     */
    private fun List<Song>.toLanguages(): List<SongLanguage> {
        val countsByCode = linkedMapOf<String, Int>()
        forEach { song ->
            if (song.languages.isEmpty()) {
                countsByCode[SongLanguage.UNKNOWN] = (countsByCode[SongLanguage.UNKNOWN] ?: 0) + 1
            } else {
                song.languages.forEach { code -> countsByCode[code] = (countsByCode[code] ?: 0) + 1 }
            }
        }
        return countsByCode
            .map { (code, songCount) -> SongLanguage(code = code, songCount = songCount) }
            .sortedWith(compareBy<SongLanguage> { it.code == SongLanguage.UNKNOWN }.thenByDescending { it.songCount }.thenBy { it.code })
    }

    /**
     * The tags of the library with the number of songs carrying each, most used first. Two spellings of the same word
     * are one tag, shown the way the first song that carries it spells it.
     */
    private fun List<Song>.toTags(): List<Tag> {
        val tagsByName = linkedMapOf<String, Tag>()
        forEach { song ->
            song.tags.forEach { tag ->
                val name = tag.lowercase()
                tagsByName[name] = tagsByName[name]?.let { it.copy(songCount = it.songCount + 1) } ?: Tag(name = tag, songCount = 1)
            }
        }
        return tagsByName.values.sortedWith(compareByDescending<Tag> { it.songCount }.thenBy { normalizeText(it.name) })
    }

    /**
     * The selector of a comparator runs on every comparison, so sorting this way used to normalize each title and
     * artist a logarithmic number of times over. The keys are computed once per song here instead.
     */
    private fun List<Song>.sort(listPreferences: ListPreferences): List<Song> {
        val comparator = when (listPreferences.sortingMode) {
            UserPreferences.SortingMode.BY_ARTIST -> compareBy<SortableSong>({ it.artist }, { it.title })
            UserPreferences.SortingMode.BY_TITLE -> compareBy<SortableSong>({ it.title }, { it.artist })
        }
        return map { SortableSong(song = it, artist = normalizeText(it.artist), title = normalizeText(it.title)) }
            .sortedWith(comparator)
            .map { it.song }
    }

    private class SortableSong(
        val song: Song,
        val artist: String,
        val title: String,
    )

    /** The part of the preferences the song list depends on. */
    private data class ListPreferences(
        val shouldShowSongsWithoutChords: Boolean,
        val sortingMode: UserPreferences.SortingMode,
        val selectedTags: Set<String>,
        val tagMatchMode: UserPreferences.TagMatchMode,
        val selectedLanguages: Set<String>,
    )

    private fun UserPreferences.toListPreferences() = ListPreferences(
        shouldShowSongsWithoutChords = shouldShowSongsWithoutChords,
        sortingMode = sortingMode,
        selectedTags = selectedTags,
        tagMatchMode = tagMatchMode,
        selectedLanguages = selectedLanguages,
    )

    private fun <T, R> DataState<T>.mapData(transform: (T) -> R): DataState<R> = when (this) {
        is DataState.Idle -> DataState.Idle(transform(data))
        is DataState.Loading -> DataState.Loading(data?.let(transform))
        is DataState.Failure -> DataState.Failure(data?.let(transform))
    }
}
