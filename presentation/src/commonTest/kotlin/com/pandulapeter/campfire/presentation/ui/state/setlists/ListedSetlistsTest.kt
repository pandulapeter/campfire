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

import com.pandulapeter.campfire.data.model.setlist.Setlist
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ListedSetlistsTest {

    @Test
    fun `the shortcut's setlist is the only one listed, or the one being reordered, never a guess among several`() {
        val first = setlist("first.setlist.json")
        val second = setlist("second.setlist.json")
        assertEquals(first, listed(first).shortcutSetlist(reorderingFileName = null))
        assertNull(listed(first, second).shortcutSetlist(reorderingFileName = null))
        assertEquals(second, listed(first, second).shortcutSetlist(reorderingFileName = second.fileName))
        assertNull(listed().shortcutSetlist(reorderingFileName = null))
    }

    private fun listed(vararg setlists: Setlist) = ListedSetlists.Empty.copy(setlists = setlists.map { SetlistWithSongs(setlist = it, entries = emptyList()) })

    private fun setlist(fileName: String) = Setlist(
        fileName = fileName,
        title = fileName,
        description = "",
        date = LocalDate(2026, 1, 1),
        isArchived = false,
        entries = emptyList(),
        size = 0L,
    )
}
