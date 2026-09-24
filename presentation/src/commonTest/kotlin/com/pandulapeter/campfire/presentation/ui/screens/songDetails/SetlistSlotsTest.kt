/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import com.pandulapeter.campfire.data.model.domain.Setlist
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SetlistSlotsTest {

    @Test
    fun preservesPositionsIncludingMissingEntries() {
        val entries = listOf("first.cho", "missing.cho", "last.cho").map { Setlist.Entry(it) }
        assertEquals(SetlistSlots(listOf(0, 2), 3), buildSetlistSlots(entries, listOf("first.cho", "last.cho")))
    }

    @Test
    fun duplicateNamesUseFirstEntry() {
        val entries = listOf("song.cho", "song.cho").map { Setlist.Entry(it) }
        assertEquals(SetlistSlots(listOf(0), 2), buildSetlistSlots(entries, listOf("song.cho")))
    }

    @Test
    fun unavailablePagerSongInvalidatesAllSlots() {
        val entries = listOf(Setlist.Entry("present.cho"))
        assertNull(buildSetlistSlots(entries, listOf("present.cho", "removed.cho")))
        assertNull(buildSetlistSlots(emptyList(), listOf("renaming.cho")))
    }
}
