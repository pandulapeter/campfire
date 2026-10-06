/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

import com.pandulapeter.campfire.data.model.domain.Setlist
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SetlistPickerContentsTest {

    private val songFileName = "song.cho"

    private fun setlist(
        fileName: String,
        isArchived: Boolean,
        vararg songFileNames: String,
    ) = Setlist(
        fileName = fileName,
        title = fileName,
        description = "",
        date = LocalDate(2026, 1, 1),
        isArchived = isArchived,
        entries = songFileNames.map { Setlist.Entry(songFileName = it) },
        size = 0,
    )

    @Test
    fun `no setlists list nothing`() {
        assertFalse(hasListableSetlist(setlists = emptyList(), songFileName = songFileName))
    }

    @Test
    fun `archived setlists without the song list nothing`() {
        val setlists = listOf(setlist("a.setlist.json", true, "other.cho"), setlist("b.setlist.json", true))
        assertFalse(hasListableSetlist(setlists = setlists, songFileName = songFileName))
    }

    @Test
    fun `an archived setlist holding the song is listed`() {
        val setlists = listOf(setlist("a.setlist.json", true, songFileName), setlist("b.setlist.json", true))
        assertTrue(hasListableSetlist(setlists = setlists, songFileName = songFileName))
    }

    @Test
    fun `an unarchived setlist without the song is listed`() {
        assertTrue(hasListableSetlist(setlists = listOf(setlist("a.setlist.json", false)), songFileName = songFileName))
    }
}
