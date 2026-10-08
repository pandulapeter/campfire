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

import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.domain.api.models.SongSection

/**
 * The whole library in the order [sortingMode] asks for. The keys are computed once per song, since the selector of a
 * comparator runs on every comparison.
 */
internal fun List<Song>.sortedFor(sortingMode: UserPreferences.SortingMode, normalize: (String) -> String): SortMemo {
    val sortable = map { SortableSong(song = it, sortingMode = sortingMode, artist = normalize(it.artist), title = normalize(it.title)) }
        .sortedWith(SortableSong.ORDER)
    return SortMemo(input = this, sortingMode = sortingMode, songs = sortable.map { it.song }, byFileName = sortable.associateBy { it.song.fileName })
}

/**
 * The library [input] sorted by [sortingMode]: [songs] in that order, and the sort keys of each song by its file
 * name, which is unique in the library.
 */
internal class SortMemo(
    val input: List<Song>,
    val sortingMode: UserPreferences.SortingMode,
    val songs: List<Song>,
    val byFileName: Map<String, SortableSong>,
)

/**
 * Songs already in the order [sortingMode] asks for, cut into the sections that order is listed under.
 *
 * Both come from [SortableSong.sectionKey]: it is what the songs are ordered by before anything else and the only
 * thing they are filed by, so a section is one run of the list, and collecting the runs in a map keyed by it means
 * that no header can come up twice whatever a title starts with.
 */
internal fun List<SortableSong>.cutIntoSections(sortingMode: UserPreferences.SortingMode): List<SongSection> {
    val sections = linkedMapOf<String, MutableList<SortableSong>>()
    forEach { sections.getOrPut(it.sectionKey) { mutableListOf() } += it }
    return sections.map { (key, songs) ->
        val first = songs.first()
        SongSection(
            header = when (sortingMode) {
                UserPreferences.SortingMode.BY_ARTIST -> SongSection.Header.Artist(name = first.song.artist, initial = first.initial, key = key)
                UserPreferences.SortingMode.BY_TITLE -> first.initial?.let { SongSection.Header.Letter(it) } ?: SongSection.Header.Symbols
            },
            songs = songs.map { it.song },
        )
    }
}

/** @param artist Normalized, like [title]. */
internal class SortableSong(
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
