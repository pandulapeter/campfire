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

import com.pandulapeter.campfire.data.model.setlist.Setlist
import com.pandulapeter.campfire.data.model.song.Song
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class SetlistSummaryTextTest {

    @Test
    fun `a setlist with nothing to say about it has no summary`() {
        assertFalse(setlistWithSongs().hasSummary)
        assertFalse(setlistWithSongs(description = "  ").hasSummary)
        assertFalse(setlistWithSongs(missing = 2).hasSummary)
        assertFalse(setlistWithSongs(durations = listOf(null, null)).hasSummary)
    }

    @Test
    fun `a description, a countdown or a single song's duration is a summary`() {
        assertTrue(setlistWithSongs(description = "For the encore").hasSummary)
        assertTrue(setlistWithSongs(isCountdownShown = true).hasSummary)
        assertTrue(setlistWithSongs(durations = listOf(null, 3.minutes), missing = 1).hasSummary)
    }

    private fun setlistWithSongs(
        description: String = "",
        isCountdownShown: Boolean = false,
        durations: List<Duration?> = emptyList(),
        missing: Int = 0,
    ): SetlistWithSongs {
        val present = durations.mapIndexed { index, duration -> SetlistWithSongs.Entry.Present(index = index, song = song("$index.cho", duration)) }
        val absent = List(missing) { SetlistWithSongs.Entry.Missing(index = present.size + it, songFileName = "missing_$it.cho") }
        val entries = present + absent
        return SetlistWithSongs(
            setlist = Setlist(
                fileName = "summer.setlist.json",
                title = "Summer",
                description = description,
                date = LocalDate(2026, 1, 1),
                isCountdownShown = isCountdownShown,
                isArchived = false,
                entries = entries.map { Setlist.Entry(songFileName = it.songFileName) },
                size = 0,
            ),
            entries = entries,
        )
    }

    private fun song(fileName: String, duration: Duration?) = Song(
        fileName = fileName, title = fileName, artist = "", key = null, transpose = 0, tags = emptyList(), languages = emptyList(),
        coverArtUrl = null, hasChords = true, canUpdateFileName = false, lastModified = 0, size = 0, duration = duration,
    )
}
