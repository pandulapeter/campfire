/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.importing

import com.pandulapeter.campfire.data.model.importing.ImportPlan
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.domain.implementation.testSetlist
import com.pandulapeter.campfire.domain.implementation.testSong
import kotlin.test.Test
import kotlin.test.assertEquals

class SetlistSongTitlesTest {

    @Test
    fun `an entry is found among the batch's songs before the library's and its own picked file's first`() {
        val songs = listOf(batchSong("Other River.cho", "River Song", origin = 1), batchSong("Own River.cho", "River Song", origin = 0))

        val resolved = resolve(entries = listOf("River Song.cho"), songs = songs, library = listOf(testSong("anna_bell-river_song.cho", title = "River Song")))

        assertEquals(listOf("Own River.cho"), resolved)
    }

    @Test
    fun `an entry is found in the library by its title in any case or Unicode form`() {
        // The library title is decomposed (O and a combining double acute), the set title composed and in capitals.
        val library = listOf(testSong("x-osz.cho", title = "O\u030Bsz"), testSong("y-river.cho", title = "river song"))
        val titles = mapOf("River Song.cho" to "River Song", "osz.cho" to "\u0150SZ")

        assertEquals(listOf("y-river.cho", "x-osz.cho"), resolve(entries = listOf("River Song.cho", "osz.cho"), titles = titles, library = library))
    }

    @Test
    fun `two entries of one title find two songs of it where there are two`() {
        val library = listOf(testSong("tom-river_song.cho", title = "River Song"), testSong("anna-river_song.cho", title = "River Song"))

        val resolved = resolve(entries = listOf("River Song.cho", "River Song 2.cho"), titles = mapOf("River Song.cho" to "River Song", "River Song 2.cho" to "River Song"), library = library)

        assertEquals(listOf("anna-river_song.cho", "tom-river_song.cho"), resolved)
    }

    @Test
    fun `an entry nothing answers stays a missing song while one the batch resolves is left alone`() {
        val resolved = resolve(
            entries = listOf("Nowhere.cho", "Here.cho"),
            titles = mapOf("Nowhere.cho" to "Nowhere", "Here.cho" to "Here"),
            batchFileNames = mapOf("Here.cho" to "band-here.cho"),
            library = listOf(testSong("someone-here.cho", title = "Here")),
        )

        assertEquals(listOf("Nowhere.cho", "Here.cho"), resolved)
    }

    private fun resolve(
        entries: List<String>,
        titles: Map<String, String> = entries.associateWith { it.removeSuffix(".cho") },
        batchFileNames: Map<String, String> = emptyMap(),
        songs: List<ImportPlan.SongEntry> = emptyList(),
        library: List<Song> = emptyList(),
    ) = SetlistSongTitles.resolved(
        setlist = testSetlist(fileName = "gig.setlist.json", entries = entries),
        titles = titles,
        origin = 0,
        batchFileNames = batchFileNames,
        songs = songs,
        librarySongs = library,
    ).entries.map { it.songFileName }

    private fun batchSong(sourceFileName: String, title: String, origin: Int) = ImportPlan.SongEntry(
        fileName = sourceFileName,
        text = "{title: $title}\n",
        status = ImportPlan.Status.NEW,
        sourceFileName = sourceFileName,
        origin = origin,
        title = title,
    )
}
