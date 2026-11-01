/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.setlists

import androidx.compose.ui.state.ToggleableState
import com.pandulapeter.campfire.data.model.setlist.Setlist
import com.pandulapeter.campfire.presentation.ui.playing.testPreferences
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** A tick of the song picker moves one song in or out of the setlist as it is now, never the picker's own copy of it. */
internal class SetlistEntryEditsTest {

    private val setlist = Setlist(
        fileName = "set.setlist.json",
        title = "Set",
        description = "",
        date = LocalDate(2026, 1, 1),
        isArchived = false,
        entries = listOf(
            Setlist.Entry(songFileName = "a"),
            Setlist.Entry(songFileName = "b", transposition = 2),
            Setlist.Entry(songFileName = "c"),
        ),
        size = 0,
    )

    @Test
    fun `a song ticked in goes to the end and the others keep their overrides`() = assertEquals(
        setlist.entries + Setlist.Entry(songFileName = "d"),
        setlist.withSongTicked(songFileName = "d", isTicked = true, preferences = null).entries,
    )

    @Test
    fun `ticking a song the setlist names changes nothing`() =
        assertEquals(setlist, setlist.withSongTicked(songFileName = "b", isTicked = true, preferences = null))

    @Test
    fun `a song ticked out is removed and the rest keep their order`() = assertEquals(
        listOf("a", "c"),
        setlist.withSongTicked(songFileName = "b", isTicked = false, preferences = null).entries.map { it.songFileName },
    )

    @Test
    fun `a song another device added is kept through a tick`() {
        val synced = setlist.copy(entries = setlist.entries + Setlist.Entry(songFileName = "x"))
        assertEquals(
            listOf("a", "b", "c", "x", "d"),
            synced.withSongTicked(songFileName = "d", isTicked = true, preferences = null).entries.map { it.songFileName },
        )
        assertEquals(
            listOf("b", "c", "x"),
            synced.withSongTicked(songFileName = "a", isTicked = false, preferences = null).entries.map { it.songFileName },
        )
    }

    @Test
    fun `songs added together go to the end in the order given, skipping the ones it names`() = assertEquals(
        listOf("a", "b", "c", "e", "d"),
        setlist.withSongsAdded(listOf("e", "b", "d", "e"), preferences = null).entries.map { it.songFileName },
    )

    @Test
    fun `adding only songs it names changes nothing`() = assertEquals(setlist, setlist.withSongsAdded(listOf("a", "c"), preferences = null))

    @Test
    fun `taking songs out keeps the rest with their overrides`() = assertEquals(
        listOf(Setlist.Entry(songFileName = "b", transposition = 2)),
        setlist.withSongsTakenOut(setOf("a", "c", "x")).entries,
    )

    private val library = testPreferences().copy(
        transpositions = mapOf("d" to -2, "b" to 5),
        tempos = mapOf("d" to 96),
        capos = mapOf("e" to 3),
    )

    @Test
    fun `a song ticked in is played the way the library plays it`() = assertEquals(
        Setlist.Entry(songFileName = "d", transposition = -2, tempo = 96),
        setlist.withSongTicked(songFileName = "d", isTicked = true, preferences = library).entries.last(),
    )

    @Test
    fun `songs added together each take the library's overrides and leave the rest to their files`() = assertEquals(
        listOf(
            Setlist.Entry(songFileName = "d", transposition = -2, tempo = 96),
            Setlist.Entry(songFileName = "e", capo = 3),
            Setlist.Entry(songFileName = "f"),
        ),
        setlist.withSongsAdded(listOf("d", "e", "f"), preferences = library).entries.drop(3),
    )

    @Test
    fun `a song the setlist names keeps its entry's overrides over the library's`() =
        assertEquals(setlist, setlist.withSongsAdded(listOf("b"), preferences = library))
}
