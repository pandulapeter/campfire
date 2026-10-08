/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui

import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.domain.api.models.SongSection
import com.pandulapeter.campfire.presentation.ui.screens.songs.SongGroup

/** A song with the title, artist and tags the search compares, normalized for searching. */
internal data class SearchableSong(
    val song: Song,
    val title: String,
    val artist: String,
    val tags: List<String>,
) {
    fun matches(query: String) = title.contains(query) || artist.contains(query) || tags.any { it.contains(query) }
}

/**
 * One library as the search reads it: [filtered] in the order the song list shows it, and the whole library by file
 * name, both as searched ([byFileName]) and as the screens that resolve a song by its name need it ([songsByFileName]).
 */
internal data class SongSearchSnapshot(
    val filtered: List<SearchableSong>,
    val byFileName: Map<String, SearchableSong>,
    val songsByFileName: Map<String, Song>,
) {
    companion object {
        val Empty = SongSearchSnapshot(emptyList(), emptyMap(), emptyMap())
    }
}

/**
 * Builds a [SongSearchSnapshot] from each library value, folding again only the songs whose title, artist or tags
 * changed. Every entry still carries the latest [Song], since a save that changed only the size or the key is a new
 * model the rows and the details screen have to show; a song that is gone, or renamed, is gone from the index too.
 */
internal class SongSearchIndex(private val normalize: (String) -> String) {
    private var previous = emptyMap<String, SearchableSong>()

    fun update(allSongs: List<Song>, filteredSongs: List<Song>): SongSearchSnapshot {
        val byFileName = LinkedHashMap<String, SearchableSong>(allSongs.size)
        val songsByFileName = LinkedHashMap<String, Song>(allSongs.size)
        for (song in allSongs) {
            val indexed = index(song, previous[song.fileName])
            byFileName[song.fileName] = indexed
            songsByFileName[song.fileName] = song
        }
        val filtered = filteredSongs.map { song ->
            val indexed = byFileName[song.fileName]
            if (indexed?.song === song) indexed else index(song, indexed)
        }
        previous = byFileName
        return SongSearchSnapshot(filtered, byFileName, songsByFileName)
    }

    private fun index(song: Song, previous: SearchableSong?): SearchableSong = when {
        previous?.song === song -> previous
        previous != null && previous.song.title == song.title && previous.song.artist == song.artist && previous.song.tags == song.tags ->
            previous.copy(song = song)
        else -> SearchableSong(
            song = song,
            title = normalize(song.title),
            artist = normalize(song.artist),
            tags = song.tags.map(normalize),
        )
    }
}

/**
 * The songs that answer [query], best first: a title that starts with it, then an artist that does, then any other
 * title or artist match, and last a song found by one of its tags alone - a tag is shared by a whole shelf of songs, so
 * a query that names one song and also happens to be part of a tag would otherwise have that song buried somewhere in
 * the shelf. Ties keep the order of the list.
 *
 * Runs on every keystroke over the whole library. The three ranking keys have only eight combinations, so each match is
 * dropped into its bucket as it is found instead of the matches being sorted afterwards.
 */
internal fun rankSongs(songs: List<SearchableSong>, query: String): List<Song> {
    val buckets = Array(SEARCH_RANK_COUNT) { mutableListOf<Song>() }
    for (song in songs) {
        val rank = searchRank(title = song.title, artist = song.artist, tags = song.tags, query = query) ?: continue
        buckets[rank] += song.song
    }
    return buildList { for (rank in buckets.indices.reversed()) addAll(buckets[rank]) }
}

/**
 * [rankSongs]' bucket for a song with these folded fields, higher being better, or null where [query] does not find
 * it. Shared with the song picker ([songPickerMatches]), so that the same search over the same library answers the
 * same way in both places.
 */
internal fun searchRank(title: String, artist: String, tags: List<String>, query: String): Int? {
    val titleHit = title.contains(query)
    val artistHit = artist.contains(query)
    if (!titleHit && !artistHit && tags.none { it.contains(query) }) return null
    return (if (title.startsWith(query)) 4 else 0) + (if (artist.startsWith(query)) 2 else 0) + (if (titleHit || artistHit) 1 else 0)
}

/** How many values [searchRank] has: its three keys have eight combinations. */
internal const val SEARCH_RANK_COUNT = 8

/**
 * What the song list shows for [normalizedQuery]: the [sections] as they are when there is nothing to search for, and
 * otherwise the ranked matches of [filtered] as one headerless group. The decision is made on the normalized query
 * rather than on the typed one, since a query of punctuation, symbols or emoji alone folds to nothing and would
 * otherwise "rank" the whole list in its own order, without its headers.
 *
 * No groups at all when nothing matches, rather than one empty group: a search with no results has to look empty to
 * whoever decides between the list and a placeholder, not like a list with one section.
 */
internal fun songGroupsFor(
    sections: List<SongSection>,
    filtered: List<SearchableSong>,
    normalizedQuery: String,
): List<SongGroup> = if (normalizedQuery.isEmpty()) {
    sections.map { SongGroup(header = it.header, songs = it.songs) }
} else {
    rankSongs(filtered, normalizedQuery).takeIf { it.isNotEmpty() }?.let { listOf(SongGroup(header = null, songs = it)) }.orEmpty()
}
