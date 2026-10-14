/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songs

import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.domain.api.models.SongSection
import com.pandulapeter.campfire.presentation.ui.components.checklist.ChecklistOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class SongSelectionGroupsTest {

    private val groups = listOf(
        SongGroup(header = SongGroupHeader.Section(SongSection.Header.Letter('A')), songs = listOf(song("a1"), song("a2"))),
        SongGroup(header = SongGroupHeader.Section(SongSection.Header.Letter('B')), songs = listOf(song("b1"))),
        SongGroup(header = SongGroupHeader.Section(SongSection.Header.Letter('C')), songs = listOf(song("c1"), song("c2"))),
    )

    @Test
    fun `with nothing ticked the list is the one it was given`() = assertSame(groups, groups.withSelectionLeading(ChecklistOrder(emptySet())))

    @Test
    fun `the songs ticked at the refresh lead in the list's order and leave their sections`() {
        val result = groups.withSelectionLeading(ChecklistOrder(setOf("c1.cho", "a2.cho")))
        assertEquals(SongGroupHeader.Selection, result.first().header)
        assertEquals(listOf("a2", "c1"), result.first().songs.map { it.title })
        assertEquals(listOf(listOf("a1"), listOf("b1"), listOf("c2")), result.drop(1).map { group -> group.songs.map { it.title } })
    }

    @Test
    fun `a later tick joins the end of the selection as a copy and stays in its section`() {
        val order = ChecklistOrder(setOf("c1.cho")).withCheckedKeys(setOf("c1.cho", "a1.cho"))
        val result = groups.withSelectionLeading(order)
        assertEquals(listOf("c1", "a1"), result.first().songs.map { it.title })
        assertEquals(setOf("a1.cho"), result.first().copiedFileNames)
        assertEquals(listOf(listOf("a1", "a2"), listOf("b1"), listOf("c2")), result.drop(1).map { group -> group.songs.map { it.title } })
    }

    @Test
    fun `a song ticked since the refresh is copied once, however many sections hold it`() {
        val twice = groups + SongGroup(header = SongGroupHeader.Section(SongSection.Header.Letter('D')), songs = listOf(song("a1")))
        val result = twice.withSelectionLeading(ChecklistOrder(setOf("b1.cho")).withCheckedKeys(setOf("b1.cho", "a1.cho")))
        assertEquals(listOf("b1", "a1"), result.first().songs.map { it.title })
        assertEquals(setOf("a1.cho"), result.first().copiedFileNames)
    }

    @Test
    fun `an unticked song stays in the selection until the next refresh`() {
        val order = ChecklistOrder(setOf("a1.cho")).withCheckedKeys(setOf("a1.cho", "b1.cho")).withCheckedKeys(emptySet())
        assertEquals(listOf("a1", "b1"), groups.withSelectionLeading(order).first().songs.map { it.title })
    }

    @Test
    fun `a section the selection empties at a refresh goes with its header`() {
        val result = groups.withSelectionLeading(ChecklistOrder(setOf("b1.cho")))
        assertEquals(
            listOf(
                SongGroupHeader.Selection,
                SongGroupHeader.Section(SongSection.Header.Letter('A')),
                SongGroupHeader.Section(SongSection.Header.Letter('C')),
            ),
            result.map { it.header },
        )
    }

    @Test
    fun `a ticked song the list does not hold is not listed`() {
        val result = groups.withSelectionLeading(ChecklistOrder(setOf("gone.cho", "b1.cho")))
        assertEquals(listOf("b1"), result.first().songs.map { it.title })
    }

    @Test
    fun `search results lead with the selection too`() {
        val results = listOf(SongGroup(header = null, songs = listOf(song("a1"), song("b1"))))
        val result = results.withSelectionLeading(ChecklistOrder(setOf("b1.cho")))
        assertEquals(listOf(SongGroupHeader.Selection, null), result.map { it.header })
        assertEquals(listOf(listOf("b1"), listOf("a1")), result.map { group -> group.songs.map { it.title } })
    }

    @Test
    fun `the selection's item keys change with a tick and not with an untick`() {
        val before = groups.withSelectionLeading(ChecklistOrder(setOf("a1.cho")))
        val ticked = groups.withSelectionLeading(ChecklistOrder(setOf("a1.cho")).withCheckedKeys(setOf("a1.cho", "c2.cho")))
        val unticked = groups.withSelectionLeading(ChecklistOrder(setOf("a1.cho")).withCheckedKeys(emptySet()))
        assertEquals(listOf(songItemKey(song("a1"))), before.selectionItemKeys())
        assertEquals(listOf(songItemKey(song("a1")), ticked.first().itemKey(song("c2"))), ticked.selectionItemKeys())
        assertEquals(before.selectionItemKeys(), unticked.selectionItemKeys())
        assertEquals(emptyList(), groups.selectionItemKeys())
    }

    private fun song(name: String) = Song(
        fileName = "$name.cho",
        title = name,
        artist = "",
        key = null,
        transpose = 0,
        tags = emptyList(),
        languages = emptyList(),
        coverArtUrl = null,
        hasChords = true,
        canUpdateFileName = false,
        lastModified = 0,
        size = 0,
    )
}
