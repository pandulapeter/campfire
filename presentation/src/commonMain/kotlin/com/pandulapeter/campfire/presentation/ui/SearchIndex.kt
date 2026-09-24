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

import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song

internal data class SearchableSong(
    val song: Song,
    val title: String,
    val artist: String,
    val tags: List<String>,
) {
    fun matches(query: String) = title.contains(query) || artist.contains(query) || tags.any { it.contains(query) }
}

internal data class SongSearchSnapshot(
    val filtered: List<SearchableSong>,
    val byFileName: Map<String, SearchableSong>,
    val songsByFileName: Map<String, Song>,
) {
    companion object {
        val Empty = SongSearchSnapshot(emptyList(), emptyMap(), emptyMap())
    }
}

/** Reuses normalized fields while replacing each entry's Song with the latest library model. */
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

/** The three Boolean ranking keys have only eight values; each bucket preserves source order. */
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

internal class SearchableSetlistIndex(private val normalize: (String) -> String) {
    private var cached = emptyMap<String, SearchableSetlist>()

    fun update(setlists: List<Setlist>): Map<String, SearchableSetlist> {
        val next = LinkedHashMap<String, SearchableSetlist>(setlists.size)
        for (setlist in setlists) {
            val previous = cached[setlist.fileName]
            next[setlist.fileName] = if (previous != null && previous.title == setlist.title && previous.description == setlist.description) {
                previous
            } else {
                SearchableSetlist(
                    title = setlist.title,
                    description = setlist.description,
                    normalizedTitle = normalize(setlist.title),
                    normalizedDescription = normalize(setlist.description),
                )
            }
        }
        cached = next
        return next
    }
}

internal data class SearchableSetlist(
    val title: String,
    val description: String,
    val normalizedTitle: String,
    val normalizedDescription: String,
) {
    fun matches(query: String) = normalizedTitle.contains(query) || normalizedDescription.contains(query)
}
