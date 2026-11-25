/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.autoScroll

import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import kotlin.test.Test
import kotlin.test.assertEquals

internal class AutoScrollCoverTest {

    private val destination = CampfireDestination.SongDetails(songFileNames = listOf("a.cho"), setlistFileName = null, initialIndex = 0)

    @Test
    fun `a song with nothing over it is uncovered`() {
        assertEquals(AutoScrollCover.NONE, autoScrollCoverOf(visibleDialog = null, top = destination))
    }

    @Test
    fun `a chord's or a scale's sheet only pauses the song`() {
        assertEquals(AutoScrollCover.DOCK_SHEET, autoScrollCoverOf(visibleDialog = DialogType.Scale(scaleId = "major", rootName = "C"), top = destination))
        assertEquals(
            AutoScrollCover.DOCK_SHEET,
            autoScrollCoverOf(visibleDialog = DialogType.ChordShape(song = song(), setlistFileName = null, chordName = "Am"), top = destination),
        )
    }

    @Test
    fun `any other sheet or another screen stops it`() {
        assertEquals(AutoScrollCover.OTHER, autoScrollCoverOf(visibleDialog = DialogType.Playing(song = song(), setlistFileName = null), top = destination))
        assertEquals(AutoScrollCover.OTHER, autoScrollCoverOf(visibleDialog = DialogType.Tuner, top = destination))
        assertEquals(AutoScrollCover.OTHER, autoScrollCoverOf(visibleDialog = null, top = CampfireDestination.Songs))
    }

    private fun song() = Song(
        fileName = "a.cho", title = "A", artist = "", key = null, transpose = 0, tags = emptyList(), languages = emptyList(),
        coverArtUrl = null, hasChords = true, canUpdateFileName = false, lastModified = 0, size = 0,
    )
}
