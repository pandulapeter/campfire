/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.backup

import kotlin.test.Test
import kotlin.test.assertEquals

class ForeignSongHeaderTest {

    @Test
    fun `names of one batch are numbered past every name already taken, whatever its case`() {
        val usedNames = BatchNames()
        val names = listOf("Song", "Song", "song 2", "SONG", "Other").map { ForeignSongHeader.uniqueName(it, ".cho", usedNames) }

        assertEquals(listOf("Song.cho", "Song 2.cho", "song 2 2.cho", "SONG 3.cho", "Other.cho"), names)
    }

    @Test
    fun `tens of thousands of songs under one title are each named`() {
        val usedNames = BatchNames()
        val last = (1..60_000).map { ForeignSongHeader.uniqueName("Song", ".cho", usedNames) }.last()

        assertEquals("Song 60000.cho", last)
    }
}
