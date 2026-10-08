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

import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SongLanguage
import com.pandulapeter.campfire.data.model.domain.Tag

/**
 * A song of the song picker with the title and the artist its search compares, normalized, and the tags (in lower
 * case) and languages ([SongLanguage.UNKNOWN] for none) its filters compare.
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
    tags = song.tags.mapTo(mutableSetOf()) { it.lowercase() },
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
 */
internal class PickerSongs(
    val list: List<PickableSong>,
    val byFileName: Map<String, PickableSong>,
) {
    companion object {
        val Empty = PickerSongs(emptyList(), emptyMap())
    }
}

/**
 * What the song picker can be narrowed by: every language and tag of the library, counted over the whole of it.
 * [languages] is empty for a library that sings in one language, which has nothing to choose between.
 */
internal data class PickerFilterOptions(
    val languages: List<SongLanguage>,
    val tags: List<Tag>,
) {
    companion object {
        val Empty = PickerFilterOptions(emptyList(), emptyList())
    }
}

/**
 * The languages and tags of the library, counted and ordered the way the songs screen's filters order them: most
 * used first, the songs that declare no language after the rest, and a tag spelled the way the song that comes first
 * by file name spells it, so that the chip does not change its capitals as the library is written to. That spelling
 * is kept by comparing file names as the songs come rather than by sorting the library first.
 */
internal fun pickerFilterOptions(songs: List<Song>): PickerFilterOptions {
    val languageCounts = mutableMapOf<String, Int>()
    val tagCounts = mutableMapOf<String, Int>()
    val tagSpellings = mutableMapOf<String, Pair<String, String>>()
    songs.forEach { song ->
        song.languages.ifEmpty { listOf(SongLanguage.UNKNOWN) }.toSet().forEach { languageCounts[it] = (languageCounts[it] ?: 0) + 1 }
        song.tags.associateBy { it.lowercase() }.forEach { (key, tag) ->
            tagCounts[key] = (tagCounts[key] ?: 0) + 1
            val spelled = tagSpellings[key]
            if (spelled == null || song.fileName < spelled.first) tagSpellings[key] = song.fileName to tag
        }
    }
    return PickerFilterOptions(
        languages = if (languageCounts.size > 1) {
            languageCounts
                .map { (code, songCount) -> SongLanguage(code = code, songCount = songCount) }
                .sortedWith(compareBy<SongLanguage> { it.code == SongLanguage.UNKNOWN }.thenByDescending { it.songCount }.thenBy { it.code })
        } else {
            emptyList()
        },
        tags = tagCounts
            .map { (key, songCount) -> Tag(name = tagSpellings.getValue(key).second, songCount = songCount) }
            .sortedWith(compareByDescending<Tag> { it.songCount }.thenBy { it.name.lowercase() }),
    )
}
