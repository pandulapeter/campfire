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
     * Written from the transform of [combine], which runs one emission at a time, so a collection never races itself
     * over it. It is marked volatile because that transform runs on [Dispatchers.Default] rather than on the main
     * thread, and a second collection of this use case (the demo library waits on one) may read it from another thread.
     */
    @Volatile
    private var cache: ScreenData? = null

    /**
     * Built on [Dispatchers.Default] rather than wherever it is collected, which for the view model is the main thread:
     * normalizing, filtering, sorting and counting the whole library is one full pass per emission, and the first scan
     * of a large library publishes a partial list every few dozen files, each of which would be one more pass before
     * the first frame could respond.
     */
    override operator fun invoke(songFilter: Flow<SongFilter>) = combine(
        setlistRepository.setlists,
        songRepository.songs,
        // Only the preferences the list is built from: a preference that changes on every step of a transposition (or
        // on every frame of a pinch, once the debounce lets it through) must not have the whole library filtered and
        // sorted again for it.
        userPreferencesRepository.userPreferences.map { state -> state.mapData { it.toListPreferences() } }.distinctUntilChanged(),
        songFilter.distinctUntilChanged(),
    ) { setlistsDataState, songsDataState, listPreferencesDataState, filter ->

        fun createScreenData() = setlistsDataState.data?.let { unsortedSetlists ->
            songsDataState.data?.let { songs ->
                listPreferencesDataState.data?.let { listPreferences ->
                    val setlists = unsortedSetlists.sortSetlists(listPreferences)
                    val filterableSongs = songs.filterHasChords(listPreferences)
                    // What the library holds, whatever is selected: the two filter groups are counted over the songs
                    // the other one leaves, but both of them decide what is still a tag and what is still a language
                    // from here, or narrowing by one would quietly switch the other one off.
                    val availableTags = filterableSongs.toTags()
                    val availableLanguages = filterableSongs.toLanguages()
                    val songsByTag = filterableSongs.filterTags(filter, listPreferences.tagMatchMode, availableTags)
                    val songsByLanguage = filterableSongs.filterLanguages(filter, availableLanguages)
                    val songSections = songsByTag
                        .filterLanguages(filter, availableLanguages)
                        .sortIntoSections(listPreferences)
                    ScreenData(
                        setlists = setlists,
                        songs = songSections.flatMap { it.songs },
                        songSections = songSections,
                        tags = songsByLanguage.toTags().withMissingSelected(
                            available = availableTags,
                            selected = filter.selectedTags.mapTo(mutableSetOf()) { it.lowercase() },
                            key = { it.name.lowercase() },
                            toEmpty = { it.copy(songCount = 0) },
                        ),
                        languages = songsByTag.toLanguages().withMissingSelected(
                            available = availableLanguages,
                            selected = filter.selectedLanguages,
                            key = { it.code },
                            toEmpty = { it.copy(songCount = 0) },
                        ),
                        unfilteredSongs = songs,
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
    }.flowOn(Dispatchers.Default).distinctUntilChanged()

    /**
     * The setlists in the order the screen lists them. The archived ones come last whichever order that is: they are
     * only on the screen at all because the user asked to see what has been put away, and mixing them in among the
     * setlists still in use would undo the putting away.
     */
    private fun List<Setlist>.sortSetlists(listPreferences: ListPreferences) = sortedWith(
        when (listPreferences.setlistSortingMode) {
            UserPreferences.SetlistSortingMode.NEWEST_FIRST -> compareBy<Setlist> { it.isArchived }.thenByDescending { it.priority }
            UserPreferences.SetlistSortingMode.BY_TITLE -> compareBy<Setlist> { it.isArchived }.thenBy { normalizeText(it.title) }
        }
    )

    private fun List<Song>.filterHasChords(listPreferences: ListPreferences) = if (listPreferences.shouldShowSongsWithoutChords) this else filter { it.hasChords }

    /**
     * Tags are matched without regard to case, here and everywhere else, so both sides are folded to lower case
     * before they meet. A selected tag no song carries any more is dropped instead of emptying the list: it is kept
     * in the filter on purpose, see [SongFilter.selectedTags].
     */
    private fun List<Song>.filterTags(songFilter: SongFilter, matchMode: UserPreferences.TagMatchMode, tags: List<Tag>): List<Song> {
        val available = tags.mapTo(mutableSetOf()) { it.name.lowercase() }
        val selected = songFilter.selectedTags.map { it.lowercase() }.filter { it in available }
        if (selected.isEmpty()) return this
        return filter { song ->
            val songTags = song.tags.mapTo(mutableSetOf()) { it.lowercase() }
            when (matchMode) {
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
    private fun List<Song>.filterLanguages(songFilter: SongFilter, languages: List<SongLanguage>): List<Song> {
        val available = languages.mapTo(mutableSetOf()) { it.code }
        val selected = songFilter.selectedLanguages.filter { it in available }
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
     * The songs in the order the preferences ask for, cut into the sections that order is listed under.
     *
     * Both come from [SortableSong.sectionKey]: it is what the songs are ordered by before anything else and the only
     * thing they are filed by, so a section is one run of the list, and collecting the runs in a map keyed by it means
     * that no header can come up twice whatever a title starts with. The keys are computed once per song, since the
     * selector of a comparator runs on every comparison.
     */
    private fun List<Song>.sortIntoSections(listPreferences: ListPreferences): List<SongSection> {
        val sections = linkedMapOf<String, MutableList<SortableSong>>()
        map { SortableSong(song = it, sortingMode = listPreferences.sortingMode, artist = normalizeText(it.artist), title = normalizeText(it.title)) }
            .sortedWith(SortableSong.ORDER)
            .forEach { sections.getOrPut(it.sectionKey) { mutableListOf() } += it }
        return sections.map { (key, songs) ->
            val first = songs.first()
            SongSection(
                header = when (listPreferences.sortingMode) {
                    UserPreferences.SortingMode.BY_ARTIST -> SongSection.Header.Artist(name = first.song.artist, initial = first.initial, key = key)
                    UserPreferences.SortingMode.BY_TITLE -> first.initial?.let { SongSection.Header.Letter(it) } ?: SongSection.Header.Symbols
                },
                songs = songs.map { it.song },
            )
        }
    }

    /** @param artist Normalized, like [title]. */
    private class SortableSong(
        val song: Song,
        sortingMode: UserPreferences.SortingMode,
        artist: String,
        title: String,
    ) {
        /** The text the list is sorted by, and the one that orders the songs the first one ties. */
        val primaryText = if (sortingMode == UserPreferences.SortingMode.BY_ARTIST) artist else title
        val secondaryText = if (sortingMode == UserPreferences.SortingMode.BY_ARTIST) title else artist

        /** The upper case first character of [primaryText] where that is a letter, of any script. */
        val initial = primaryText.firstOrNull()?.takeIf { it.isLetter() }?.uppercaseChar()

        /**
         * By artist a section is an artist; by title it is an initial, the empty key standing for every title that
         * has none. The upper case initial rather than the first character: two lower case letters can share an
         * upper case one (`ı` and `i`), and they share a header then.
         */
        val sectionKey = if (sortingMode == UserPreferences.SortingMode.BY_ARTIST) artist else initial?.toString().orEmpty()

        companion object {

            /**
             * Whatever starts with something other than a letter comes first, in either order. Left to the order of
             * the strings those texts land on both sides of the alphabet - a digit sorts before `a`, while `¿`, `…`,
             * a curly quote and every emoji sort after `z` - which is two runs under one header.
             */
            val ORDER = compareBy<SortableSong>({ it.initial != null }, { it.sectionKey }, { it.primaryText }, { it.secondaryText })
        }
    }

    /** The part of the preferences the song list depends on. */
    private data class ListPreferences(
        val shouldShowSongsWithoutChords: Boolean,
        val sortingMode: UserPreferences.SortingMode,
        val setlistSortingMode: UserPreferences.SetlistSortingMode,
        val tagMatchMode: UserPreferences.TagMatchMode,
    )

    private fun UserPreferences.toListPreferences() = ListPreferences(
        shouldShowSongsWithoutChords = shouldShowSongsWithoutChords,
        sortingMode = sortingMode,
        setlistSortingMode = setlistSortingMode,
        tagMatchMode = tagMatchMode,
    )

    private fun <T, R> DataState<T>.mapData(transform: (T) -> R): DataState<R> = when (this) {
        is DataState.Idle -> DataState.Idle(transform(data))
        is DataState.Loading -> DataState.Loading(data?.let(transform))
        is DataState.Failure -> DataState.Failure(data?.let(transform))
    }
}
