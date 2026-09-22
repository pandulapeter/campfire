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
            preferences.map { state -> state.mapData { it.toSongListPreferences() } }.distinctUntilChanged(),
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
            preferences.map { state -> state.mapData { it.setlistSortingMode } }.distinctUntilChanged(),
        ) { setlistsDataState, sortingModeDataState ->
            listOf(setlistsDataState, sortingModeDataState).combinedState(
                setlistsDataState.data?.let { setlists -> sortingModeDataState.data?.let { setlists.sortSetlists(it) } },
            )
        }
        return combine(setlistPart, songPart) { setlistsDataState, songsDataState ->
            val screenData = setlistsDataState.data?.let { setlists ->
                songsDataState.data?.let { songPart ->
                    ScreenData(
                        setlists = setlists,
                        songs = songPart.songs,
                        songSections = songPart.songSections,
                        tags = songPart.tags,
                        languages = songPart.languages,
                        unfilteredSongs = songPart.unfilteredSongs,
                    ).also {
                        cache = it
                    }
                }
            }
            listOf(setlistsDataState, songsDataState).combinedState(screenData ?: cache)
        }.flowOn(Dispatchers.Default).distinctUntilChanged()
    }

    private fun List<Song>.toSongPart(songListPreferences: SongListPreferences, filter: SongFilter): SongPart {
        val filterableSongs = filterHasChords(songListPreferences)
        // What the library holds, whatever is selected: the two filter groups are counted over the songs the other one
        // leaves, but both of them decide what is still a tag and what is still a language from here, or narrowing by
        // one would quietly switch the other one off.
        val availableTags = filterableSongs.toTags()
        val availableLanguages = filterableSongs.toLanguages()
        val songsByTag = filterableSongs.filterTags(filter, songListPreferences.tagMatchMode, availableTags)
        val songsByLanguage = filterableSongs.filterLanguages(filter, songListPreferences.languageMatchMode, availableLanguages)
        val songSections = songsByTag
            .filterLanguages(filter, songListPreferences.languageMatchMode, availableLanguages)
            .sortIntoSections(songListPreferences)
        return SongPart(
            songs = songSections.flatMap { it.songs },
            songSections = songSections,
            tags = availableTags.recountedTagsOver(songsByLanguage),
            languages = availableLanguages.recountedLanguagesOver(songsByTag),
            unfilteredSongs = this,
        )
    }

    /** The song half of [ScreenData], see there. */
    private class SongPart(
        val songs: List<Song>,
        val songSections: List<SongSection>,
        val tags: List<Tag>,
        val languages: List<SongLanguage>,
        val unfilteredSongs: List<Song>,
    )

    /**
     * [data] in the state of the inputs it was built from: a failure anywhere beats a load anywhere, which beats every
     * input being idle. An idle state always carries data, so an idle input never leaves [data] without it.
     */
    private fun <T> List<DataState<*>>.combinedState(data: T?): DataState<T> = when {
        any { it is DataState.Failure } -> DataState.Failure(data)
        any { it is DataState.Loading } -> DataState.Loading(data)
        else -> DataState.Idle(data ?: throw IllegalStateException("No data available while all data states are idle."))
    }

    /**
     * The setlists in the order the screen lists them. The archived ones come last whichever order that is: they are
     * only on the screen at all because the user asked to see what has been put away, and mixing them in among the
     * setlists still in use would undo the putting away.
     *
     * Both orders end in the file name, which never ties. Priorities do - two devices that each made a setlist
     * while offline gave them the same one, and a hand written file has none - and so do titles, and what decides a
     * tie otherwise is the order of the repository's list, where a setlist moves to the end every time it is
     * written: the two would trade places on the screen whenever one of them was touched.
     */
    private fun List<Setlist>.sortSetlists(sortingMode: UserPreferences.SetlistSortingMode) = sortedWith(
        when (sortingMode) {
            UserPreferences.SetlistSortingMode.NEWEST_FIRST -> compareBy<Setlist> { it.isArchived }.thenByDescending { it.priority }
            UserPreferences.SetlistSortingMode.BY_TITLE -> compareBy<Setlist> { it.isArchived }.thenBy { normalizeText(it.title) }
        }.thenBy { it.fileName }
    )

    private fun List<Song>.filterHasChords(songListPreferences: SongListPreferences) =
        if (songListPreferences.shouldShowSongsWithoutChords) this else filter { it.hasChords }

    /**
     * Tags are matched without regard to case, here and everywhere else, so both sides are folded to lower case
     * before they meet. A selected tag no song carries any more is dropped instead of emptying the list: it is kept
     * in the filter on purpose, see [SongFilter.selectedTags].
     */
    private fun List<Song>.filterTags(songFilter: SongFilter, matchMode: UserPreferences.MatchMode, tags: List<Tag>): List<Song> {
        val available = tags.mapTo(mutableSetOf()) { it.name.lowercase() }
        val selected = songFilter.selectedTags.map { it.lowercase() }.filter { it in available }
        if (selected.isEmpty()) return this
        return filter { song ->
            val songTags = song.tags.mapTo(mutableSetOf()) { it.lowercase() }
            when (matchMode) {
                UserPreferences.MatchMode.ANY -> selected.any { it in songTags }
                UserPreferences.MatchMode.ALL -> selected.all { it in songTags }
            }
        }
    }

    /**
     * The songs left by the language filter. A song carries its languages the way it carries its tags, so several
     * selected languages combine the way several tags do; [SongLanguage.UNKNOWN] selects the songs that declare none,
     * which no song can name itself, see [SongLanguage.Companion.UNKNOWN]. Such a song is taken to be in that one
     * "language", so asking for every one of several selected languages never finds it next to a named one.
     */
    private fun List<Song>.filterLanguages(songFilter: SongFilter, matchMode: UserPreferences.MatchMode, languages: List<SongLanguage>): List<Song> {
        val available = languages.mapTo(mutableSetOf()) { it.code }
        val selected = songFilter.selectedLanguages.filter { it in available }
        if (selected.isEmpty()) return this
        return filter { song ->
            val songLanguages = song.languages.ifEmpty { listOf(SongLanguage.UNKNOWN) }
            when (matchMode) {
                UserPreferences.MatchMode.ANY -> selected.any { it in songLanguages }
                UserPreferences.MatchMode.ALL -> selected.all { it in songLanguages }
            }
        }
    }

    /**
     * Every tag of the library as the filter controls show it, counted over [songs] - the ones the language filter
     * leaves. A tag the language filter has counted down to nothing stays on the list with a zero rather than
     * dropping off it: the chips would otherwise come and go under the user's finger as the other group changes, and
     * a zero says exactly what it means - this combination of the two groups selects nothing. The zeros sort last,
     * which keeps the tags that still do something among the ones shown before "show all".
     */
    private fun List<Tag>.recountedTagsOver(songs: List<Song>): List<Tag> {
        val counts = mutableMapOf<String, Int>()
        songs.forEach { song ->
            song.tags.mapTo(mutableSetOf()) { it.lowercase() }.forEach { tag -> counts[tag] = (counts[tag] ?: 0) + 1 }
        }
        return map { it.copy(songCount = counts[it.name.lowercase()] ?: 0) }.sortedWith(tagOrder)
    }

    /** Every language of the library counted over [songs] - the ones the tag filter leaves - the way [recountedTagsOver] counts the tags. */
    private fun List<SongLanguage>.recountedLanguagesOver(songs: List<Song>): List<SongLanguage> {
        val counts = songs.toLanguages().associate { it.code to it.songCount }
        return map { it.copy(songCount = counts[it.code] ?: 0) }.sortedWith(languageOrder)
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
            .sortedWith(languageOrder)
    }

    /**
     * The tags of the library with the number of songs carrying each, most used first. Two spellings of the same word
     * are one tag, shown the way the song that comes first by file name spells it - by file name rather than by where
     * the song is in the list, because the repository's list is in no particular order (a song that was just saved is
     * at its end), and the chip would change its capitals after an edit to a song and back after the next rescan.
     */
    private fun List<Song>.toTags(): List<Tag> {
        val tagsByName = linkedMapOf<String, SpelledTag>()
        forEach { song ->
            song.tags.forEach { tag ->
                val spelled = tagsByName.getOrPut(tag.lowercase()) { SpelledTag(name = tag, fileName = song.fileName) }
                spelled.songCount++
                if (song.fileName < spelled.fileName) {
                    spelled.name = tag
                    spelled.fileName = song.fileName
                }
            }
        }
        return tagsByName.values
            .map { Tag(name = it.name, songCount = it.songCount) }
            .sortedWith(tagOrder)
    }

    /** Most used first, and the songs that declare none last, see [toLanguages]. */
    private val languageOrder = compareBy<SongLanguage> { it.code == SongLanguage.UNKNOWN }.thenByDescending { it.songCount }.thenBy { it.code }

    /** Most used first. Tags fold case but not accents, so two of them can share the text they are sorted by. */
    private val tagOrder = compareByDescending<Tag> { it.songCount }.thenBy { normalizeText(it.name) }.thenBy { it.name }

    /**
     * A tag while it is being counted.
     *
     * @param fileName The song [name] is spelled after: the first by file name of the ones counted so far.
     */
    private class SpelledTag(
        var name: String,
        var fileName: String,
        var songCount: Int = 0,
    )

    /**
     * The songs in the order the preferences ask for, cut into the sections that order is listed under.
     *
     * Both come from [SortableSong.sectionKey]: it is what the songs are ordered by before anything else and the only
     * thing they are filed by, so a section is one run of the list, and collecting the runs in a map keyed by it means
     * that no header can come up twice whatever a title starts with. The keys are computed once per song, since the
     * selector of a comparator runs on every comparison.
     */
    private fun List<Song>.sortIntoSections(songListPreferences: SongListPreferences): List<SongSection> {
        val sections = linkedMapOf<String, MutableList<SortableSong>>()
        map { SortableSong(song = it, sortingMode = songListPreferences.sortingMode, artist = normalizeText(it.artist), title = normalizeText(it.title)) }
            .sortedWith(SortableSong.ORDER)
            .forEach { sections.getOrPut(it.sectionKey) { mutableListOf() } += it }
        return sections.map { (key, songs) ->
            val first = songs.first()
            SongSection(
                header = when (songListPreferences.sortingMode) {
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
             *
             * The file name comes last because it is the one thing two songs cannot share: two arrangements of a
             * song tie on everything before it, and would otherwise be listed in the order of the repository's
             * list, where a song moves to the end every time it is saved.
             */
            val ORDER = compareBy<SortableSong>({ it.initial != null }, { it.sectionKey }, { it.primaryText }, { it.secondaryText }, { it.song.fileName })
        }
    }

    /** The part of the preferences the song list depends on. */
    private data class SongListPreferences(
        val shouldShowSongsWithoutChords: Boolean,
        val sortingMode: UserPreferences.SortingMode,
        val tagMatchMode: UserPreferences.MatchMode,
        val languageMatchMode: UserPreferences.MatchMode,
    )

    private fun UserPreferences.toSongListPreferences() = SongListPreferences(
        shouldShowSongsWithoutChords = shouldShowSongsWithoutChords,
        sortingMode = sortingMode,
        tagMatchMode = tagMatchMode,
        languageMatchMode = languageMatchMode,
    )

    private fun <T, R> DataState<T>.mapData(transform: (T) -> R): DataState<R> = when (this) {
        is DataState.Idle -> DataState.Idle(transform(data))
        is DataState.Loading -> DataState.Loading(data?.let(transform))
        is DataState.Failure -> DataState.Failure(data?.let(transform))
    }
}
