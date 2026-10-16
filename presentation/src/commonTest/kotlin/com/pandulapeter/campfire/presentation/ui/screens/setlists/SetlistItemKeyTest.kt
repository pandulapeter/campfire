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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SetlistItemKeyTest {

    @Test
    fun `every item of a setlist is told apart as that setlist's`() {
        val fileName = "summer.setlist.json"

        assertEquals(fileName, SetlistItemKey.setlistFileNameOf(SetlistItemKey.header(fileName)))
        assertEquals(fileName, SetlistItemKey.setlistFileNameOf(SetlistItemKey.summary(fileName)))
        assertEquals(fileName, SetlistItemKey.setlistFileNameOf(SetlistItemKey.chooseSongs(fileName)))
        assertEquals(fileName, SetlistItemKey.setlistFileNameOf(SetlistItemKey(setlistFileName = fileName, songFileName = "a.cho").string))
    }

    @Test
    fun `a song of a setlist whose name starts like a header's is still that setlist's`() {
        val fileName = "setlist_rock.setlist.json"

        assertEquals(fileName, SetlistItemKey.setlistFileNameOf(SetlistItemKey(setlistFileName = fileName, songFileName = "a.cho").string))
        assertEquals(fileName, SetlistItemKey.setlistFileNameOf(SetlistItemKey.header(fileName)))
    }

    @Test
    fun `an item of no setlist belongs to none`() {
        assertNull(SetlistItemKey.setlistFileNameOf("placeholder"))
        assertNull(SetlistItemKey.setlistFileNameOf("show_archived_setlists"))
        assertNull(SetlistItemKey.setlistFileNameOf(null))
        assertNull(SetlistItemKey.setlistFileNameOf(42))
    }
}
