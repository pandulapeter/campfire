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

    private fun index(song: Song, previous: SearchableSong?): SearchableSong = if (
        previous != null && previous.song.title == song.title && previous.song.artist == song.artist && previous.song.tags == song.tags
    ) {
        previous.copy(song = song)
    } else {
        SearchableSong(
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
    val buckets = Array(8) { mutableListOf<Song>() }
    for (song in songs) {
        val titleHit = song.title.contains(query)
        val artistHit = song.artist.contains(query)
        if (!titleHit && !artistHit && song.tags.none { it.contains(query) }) continue
        val rank = (if (song.title.startsWith(query)) 4 else 0) +
            (if (song.artist.startsWith(query)) 2 else 0) +
            (if (titleHit || artistHit) 1 else 0)
        buckets[rank] += song.song
    }
    return buildList { for (rank in buckets.indices.reversed()) addAll(buckets[rank]) }
}
