/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SongFileChangesTest {

    @Test
    fun `a pager follows a renamed song in place and stays on its page`() {
        val pager = CampfireDestination.SongDetails(songFileNames = listOf("a.cho", "b.cho", "c.cho"), setlistFileName = null, initialIndex = 0)
        val renamed = pager.followingSongRename(from = "b.cho", to = "d.cho") as CampfireDestination.SongDetails
        assertEquals(listOf("a.cho", "d.cho", "c.cho"), renamed.songFileNames)
        assertEquals(1, renamed.initialIndex)
        assertEquals(pager.id, renamed.id)
    }

    @Test
    fun `a setlist pager already naming the new file names the song once`() {
        val pager = CampfireDestination.SongDetails(songFileNames = listOf("a.cho", "b.cho", "d.cho"), setlistFileName = "set.setlist.json", initialIndex = 2)
        val renamed = pager.followingSongRename(from = "b.cho", to = "d.cho") as CampfireDestination.SongDetails
        assertEquals(listOf("a.cho", "d.cho"), renamed.songFileNames)
        assertEquals(1, renamed.initialIndex)
        assertEquals("set.setlist.json", renamed.setlistFileName)
    }

    @Test
    fun `an editor follows a renamed song`() {
        val editor = CampfireDestination.SongEditor(fileName = "b.cho", shouldStartInsideFirstSection = true)
        assertEquals(CampfireDestination.SongEditor(fileName = "d.cho", shouldStartInsideFirstSection = true), editor.followingSongRename(from = "b.cho", to = "d.cho"))
    }

    @Test
    fun `a destination that does not name the renamed song is left as it is`() {
        listOf(
            CampfireDestination.Songs,
            CampfireDestination.ImportReport,
            CampfireDestination.SongEditor(fileName = "a.cho"),
            CampfireDestination.SongDetails(songFileNames = listOf("a.cho"), setlistFileName = null, initialIndex = 0),
        ).forEach { destination ->
            assertSame(destination, destination.followingSongRename(from = "b.cho", to = "d.cho"))
        }
    }

    @Test
    fun `the screens showing a deleted song are the editor on it and every pager through it`() {
        assertTrue(CampfireDestination.SongEditor(fileName = "b.cho").isShowingSong("b.cho"))
        assertTrue(CampfireDestination.SongDetails(songFileNames = listOf("a.cho", "b.cho"), setlistFileName = "set.setlist.json", initialIndex = 0).isShowingSong("b.cho"))
        assertFalse(CampfireDestination.SongEditor(fileName = "a.cho").isShowingSong("b.cho"))
        assertFalse(CampfireDestination.Songs.isShowingSong("b.cho"))
        assertFalse(null.isShowingSong("b.cho"))
    }
}
