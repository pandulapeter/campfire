/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.setlists

import androidx.compose.ui.state.ToggleableState
import com.pandulapeter.campfire.data.model.domain.Setlist
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** A tick of the song picker moves one song in or out of the setlist as it is now, never the picker's own copy of it. */
internal class SetlistSongToggleTest {

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

    private val selection = listOf("a", "b", "c")

    @Test
    fun `a song ticked in goes to the end and the others keep their overrides`() = assertEquals(
        setlist.entries + Setlist.Entry(songFileName = "d"),
        setlist.withSongTicked(songFileName = "d", isTicked = true).entries,
    )

    @Test
    fun `ticking a song the setlist names changes nothing`() =
        assertEquals(setlist, setlist.withSongTicked(songFileName = "b", isTicked = true))

    @Test
    fun `a song ticked out is removed and the rest keep their order`() = assertEquals(
        listOf("a", "c"),
        setlist.withSongTicked(songFileName = "b", isTicked = false).entries.map { it.songFileName },
    )

    @Test
    fun `a song another device added is kept through a tick`() {
        val synced = setlist.copy(entries = setlist.entries + Setlist.Entry(songFileName = "x"))
        assertEquals(
            listOf("a", "b", "c", "x", "d"),
            synced.withSongTicked(songFileName = "d", isTicked = true).entries.map { it.songFileName },
        )
        assertEquals(
            listOf("b", "c", "x"),
            synced.withSongTicked(songFileName = "a", isTicked = false).entries.map { it.songFileName },
        )
    }

    @Test
    fun `songs added together go to the end in the order given, skipping the ones it names`() = assertEquals(
        listOf("a", "b", "c", "e", "d"),
        setlist.withSongsAdded(listOf("e", "b", "d", "e")).entries.map { it.songFileName },
    )

    @Test
    fun `adding only songs it names changes nothing`() = assertEquals(setlist, setlist.withSongsAdded(listOf("a", "c")))

    @Test
    fun `taking songs out keeps the rest with their overrides`() = assertEquals(
        listOf(Setlist.Entry(songFileName = "b", transposition = 2)),
        setlist.withSongsTakenOut(setOf("a", "c", "x")).entries,
    )

    @Test
    fun `a selection box is ticked, empty or half ticked by what the setlist holds of the selection`() {
        val selection = listOf("a", "b")
        assertEquals(ToggleableState.Off, selectionBoxState(selection, held = emptySet(), added = emptySet()))
        assertEquals(ToggleableState.Indeterminate, selectionBoxState(selection, held = setOf("a"), added = emptySet()))
        assertEquals(ToggleableState.On, selectionBoxState(selection, held = setOf("a"), added = setOf("b")))
        assertEquals(ToggleableState.On, selectionBoxState(selection, held = setOf("a", "b", "x"), added = emptySet()))
    }

    @Test
    fun `a setlist the sheet has not tapped is read as it is now`() {
        val held = heldOf(emptyMap(), setlistOf("a", "b", "c", "x"), selection)

        assertEquals(setOf("a", "b", "c"), held)
        assertEquals(ToggleableState.On, selectionBoxState(selection, held, emptySet()))
    }

    @Test
    fun `a recorded setlist is not read again`() = assertEquals(
        setOf("a"),
        heldOf(mapOf(setlist.fileName to setOf("a")), setlistOf("a", "b"), selection),
    )

    @Test
    fun `a tap records what the setlist holds then, once`() {
        val recorded = withHeldRecorded(emptyMap(), setlistOf("a"), selection)

        assertEquals(mapOf(setlist.fileName to setOf("a")), recorded)
        assertSame(recorded, withHeldRecorded(recorded, setlistOf("a", "b", "c"), selection))
    }

    private fun setlistOf(vararg songFileNames: String) = setlist.copy(entries = songFileNames.map { Setlist.Entry(songFileName = it) })
}
