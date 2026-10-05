/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.metronome

import com.pandulapeter.campfire.metronome.api.model.MetronomeOrigin
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import kotlin.test.Test
import kotlin.test.assertEquals

class MetronomeContextTest {

    private val songDetails = CampfireDestination.SongDetails(songFileNames = listOf("a.cho", "b.cho"), setlistFileName = "s.setlist.json", initialIndex = 0, id = "1")
    private val currentSong = { destination: CampfireDestination.SongDetails -> destination.songFileNames[destination.initialIndex] }

    @Test
    fun theEditorOverASongKeepsTheSong() = assertEquals(
        MetronomeContext.Song("a.cho", "s.setlist.json"),
        metronomeContextOf(listOf(CampfireDestination.Songs, songDetails, CampfireDestination.SongEditor("a.cho")), currentSong),
    )

    @Test
    fun theUpperOfTwoSongsWins() = assertEquals(
        MetronomeContext.Song("c.cho", null),
        metronomeContextOf(
            listOf(
                CampfireDestination.Songs,
                songDetails,
                CampfireDestination.ImportReport,
                CampfireDestination.SongDetails(songFileNames = listOf("c.cho"), setlistFileName = null, initialIndex = 0, id = "2"),
            ),
            currentSong,
        ),
    )

    @Test
    fun aStackWithoutASongIsStandalone() =
        assertEquals(MetronomeContext.Standalone, metronomeContextOf(listOf(CampfireDestination.Songs, CampfireDestination.Metronome), currentSong))

    @Test
    fun thePageBeingHeadedForIsTheSong() =
        assertEquals(MetronomeContext.Song("b.cho", "s.setlist.json"), metronomeContextOf(listOf(songDetails)) { "b.cho" })

    @Test
    fun aSongClickStopsWhenItsSongCloses() = assertEquals(
        MetronomeRetarget.Stop,
        metronomeRetargetOf(MetronomeContext.Song("a.cho", null), MetronomeContext.Standalone, MetronomeOrigin.Song("a.cho", null)),
    )

    @Test
    fun aTabClickGoesBackToTheTab() = assertEquals(
        MetronomeRetarget.Restart,
        metronomeRetargetOf(MetronomeContext.Song("a.cho", null), MetronomeContext.Standalone, MetronomeOrigin.Standalone),
    )

    @Test
    fun pagingRetargets() = assertEquals(
        MetronomeRetarget.Restart,
        metronomeRetargetOf(MetronomeContext.Song("a.cho", null), MetronomeContext.Song("b.cho", null), MetronomeOrigin.Song("a.cho", null)),
    )

    @Test
    fun nothingHappensWithoutAChangeOrAClick() {
        assertEquals(MetronomeRetarget.None, metronomeRetargetOf(MetronomeContext.Standalone, MetronomeContext.Standalone, MetronomeOrigin.Standalone))
        assertEquals(MetronomeRetarget.None, metronomeRetargetOf(MetronomeContext.Standalone, MetronomeContext.Song("a.cho", null), null))
    }
}
