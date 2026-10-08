/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.playing

import com.pandulapeter.campfire.data.model.domain.Setlist
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SongOverridesTest {

    private fun setlist(fileName: String, vararg entries: Setlist.Entry) = Setlist(
        fileName = fileName,
        title = fileName,
        description = "",
        date = LocalDate(2026, 1, 1),
        isArchived = false,
        entries = entries.toList(),
        size = 0,
    )

    private val overrides = SongOverrides.of(
        library = mapOf("a.cho" to 100),
        setlists = listOf(
            setlist(FIRST, Setlist.Entry(songFileName = "a.cho", tempo = 90), Setlist.Entry(songFileName = "b.cho")),
            setlist(SECOND, Setlist.Entry(songFileName = "b.cho", tempo = 80)),
        ),
    ) { it.tempo }

    @Test
    fun `the library and a setlist are read apart and never mixed`() {
        assertEquals(100, overrides["a.cho", null])
        assertEquals(90, overrides["a.cho", FIRST])
        assertNull(overrides["b.cho", null])
        assertNull(overrides["b.cho", FIRST])
        assertEquals(80, overrides[SongPlace("b.cho", SECOND)])
        assertNull(overrides["a.cho", SECOND])
    }

    @Test
    fun `an unknown setlist has no overrides`() = assertNull(overrides["a.cho", "unknown.setlist.json"])

    @Test
    fun `a null value removes the override`() {
        val changed = overrides.with(SongPlace("a.cho", null), null).with(SongPlace("a.cho", FIRST), null)
        assertNull(changed["a.cho", null])
        assertNull(changed["a.cho", FIRST])
    }

    @Test
    fun `a setlist's override leaves the library and the other setlists alone`() {
        val changed = overrides.with(SongPlace("b.cho", FIRST), 120)
        assertEquals(120, changed["b.cho", FIRST])
        assertEquals(80, changed["b.cho", SECOND])
        assertNull(changed["b.cho", null])
        assertEquals(90, changed["a.cho", FIRST])
    }

    @Test
    fun `a library override leaves the setlists alone`() {
        val changed = overrides.with(SongPlace("a.cho", null), 110)
        assertEquals(110, changed["a.cho", null])
        assertEquals(90, changed["a.cho", FIRST])
    }

    private companion object {
        const val FIRST = "first.setlist.json"
        const val SECOND = "second.setlist.json"
    }
}
