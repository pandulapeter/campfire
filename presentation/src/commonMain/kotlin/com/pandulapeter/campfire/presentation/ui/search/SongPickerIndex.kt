/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.search

import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.data.model.song.SongLanguage
import com.pandulapeter.campfire.data.model.song.Tag
import com.pandulapeter.campfire.data.model.song.tagKey

/**
 * A song of the song picker with the title and the artist its search compares, normalized, and the tags (by
 * `tagKey`) and languages ([SongLanguage.UNKNOWN] for none) its filters compare.
 */
internal class PickableSong(
    val song: Song,
    val title: String,
    val artist: String,
    val tags: Set<String>,
    val searchableTags: List<String>,
    val languages: Set<String>,
)

/** The [PickableSong] of a song as the library's search index already folded it. */
internal fun SearchableSong.toPickableSong() = PickableSong(
    song = song,
    title = title,
    artist = artist,
    tags = song.tags.mapTo(mutableSetOf(), ::tagKey),
    searchableTags = tags,
    languages = song.languages.ifEmpty { listOf(SongLanguage.UNKNOWN) }.toSet(),
)

/**
 * The songs of [songs] the song picker lists: the ones the active tag and language chips leave, narrowed like the
 * songs screen's filters, and, for a non-empty [normalizedQuery], those that answer it, ranked the way the songs
 * screen's search ranks them ([searchRank]) with ties in the order of the list. With nothing to search for, the list's
 * own order.
 */
internal fun songPickerMatches(
    songs: List<PickableSong>,
    normalizedQuery: String,
    activeTags: Set<String>,
    activeLanguages: Set<String>,
): List<PickableSong> {
    val filtered = songs.filter { song ->
        (activeTags.isEmpty() || activeTags.any { it in song.tags }) &&
            (activeLanguages.isEmpty() || activeLanguages.any { it in song.languages })
    }
    if (normalizedQuery.isEmpty()) return filtered
    val buckets = Array(SEARCH_RANK_COUNT) { mutableListOf<PickableSong>() }
    for (song in filtered) {
        val rank = searchRank(title = song.title, artist = song.artist, tags = song.searchableTags, query = normalizedQuery) ?: continue
        buckets[rank] += song
    }
    return buildList { for (rank in buckets.indices.reversed()) addAll(buckets[rank]) }
}

/**
 * Every song of the library as the song picker lists it: in the songs screen's order in [list], and by file name in
 * [byFileName] for the songs a setlist already holds, which the picker puts first.
 *
 * @param sorted The library in that order, as [pickerSongsOf] built [list] from, so that it can tell when nothing changed.
 */
internal class PickerSongs(
    val list: List<PickableSong>,
    val byFileName: Map<String, PickableSong>,
    val sorted: List<Song>,
) {
    companion object {
        val Empty = PickerSongs(emptyList(), emptyMap(), emptyList())
    }
}

/**
 * The [PickerSongs] of the library in the order of [sorted], with each song's search keys taken from [byFileName], or
 * [previous] itself while [sorted] is the very list it was built from. The domain layer hands out the same sorted list
 * for as long as the library and its order are the same, and then the search index holds the same songs, so a filter
 * changed on the songs screen builds nothing here, and the unchanged value is not emitted to an open picker again.
 */
internal fun pickerSongsOf(sorted: List<Song>, byFileName: Map<String, SearchableSong>, previous: PickerSongs): PickerSongs {
    if (sorted === previous.sorted) return previous
    val list = sorted.mapNotNull { byFileName[it.fileName] }.map { it.toPickableSong() }
    return PickerSongs(list = list, byFileName = list.associateBy { it.song.fileName }, sorted = sorted)
}

/**
 * What the song picker can be narrowed by: every language and tag of the library, counted over the whole of it by the
 * domain (`ScreenData.libraryTags`, `libraryLanguages`). [languages] is empty for a library that sings in one language,
 * which has nothing to choose between.
 */
internal data class PickerFilterOptions(
    val languages: List<SongLanguage>,
    val tags: List<Tag>,
) {
    companion object {
        val Empty = PickerFilterOptions(emptyList(), emptyList())
    }
}

/** [languages] is offered only where the library sings in more than one, since one has nothing to choose between. */
internal fun pickerFilterOptionsOf(tags: List<Tag>, languages: List<SongLanguage>) =
    PickerFilterOptions(languages = if (languages.size > 1) languages else emptyList(), tags = tags)
