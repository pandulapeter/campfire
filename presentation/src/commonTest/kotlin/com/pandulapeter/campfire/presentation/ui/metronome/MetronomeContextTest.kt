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

import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MetronomeContextTest {

    private val songDetails = CampfireDestination.SongDetails(songFileNames = listOf("a.cho", "b.cho"), setlistFileName = "s.setlist.json", initialIndex = 0, id = "1")
    private val currentSong = { destination: CampfireDestination.SongDetails -> destination.songFileNames[destination.initialIndex] }

    @Test
    fun theSongOnTopIsTheContext() = assertEquals(
        MetronomeContext.Song("a.cho", "s.setlist.json"),
        metronomeContextOf(listOf(CampfireDestination.Songs, songDetails), currentSong),
    )

    /** A click is stopped with the screen it was started on, so a song under another screen plays for nothing. */
    @Test
    fun aSongUnderAnotherScreenIsNotTheContext() = assertEquals(
        MetronomeContext.Standalone,
        metronomeContextOf(listOf(CampfireDestination.Songs, songDetails, CampfireDestination.SongEditor("a.cho")), currentSong),
    )

    @Test
    fun aStackWithoutASongIsStandalone() =
        assertEquals(MetronomeContext.Standalone, metronomeContextOf(listOf(CampfireDestination.Songs, CampfireDestination.Metronome), currentSong))

    @Test
    fun thePageBeingHeadedForIsTheSong() =
        assertEquals(MetronomeContext.Song("b.cho", "s.setlist.json"), metronomeContextOf(listOf(songDetails)) { "b.cho" })

    /** A song whose file the library does not hold yet has no tempo to play, so the tab's own pattern stands in. */
    @Test
    fun aSongWithNoFileIsStandalone() =
        assertEquals(MetronomeContext.Standalone, metronomeContextOf(listOf(songDetails)) { null })

    @Test
    fun theSameSongStayingOnTopKeepsTheClick() = assertFalse(isMetronomeScreenLeft(songDetails, songDetails.copy()))

    /** A rename rewrites the screen's file names in place, which is still the screen the click was started on. */
    @Test
    fun theSameSongUnderNewFileNamesKeepsTheClick() =
        assertFalse(isMetronomeScreenLeft(songDetails, songDetails.copy(songFileNames = listOf("c.cho", "b.cho"))))

    @Test
    fun theMetronomeTabStayingOnTopKeepsTheClick() = assertFalse(isMetronomeScreenLeft(CampfireDestination.Metronome, CampfireDestination.Metronome))

    @Test
    fun aSongOpenedOverTheMetronomeTabStopsTheClick() = assertTrue(isMetronomeScreenLeft(CampfireDestination.Metronome, songDetails))

    @Test
    fun aSongOpenedOverAnotherSongStopsTheClick() = assertTrue(isMetronomeScreenLeft(songDetails, songDetails.copy(id = "2")))

    @Test
    fun aScreenWithoutAMetronomeStopsTheClick() {
        assertTrue(isMetronomeScreenLeft(songDetails, CampfireDestination.SongEditor("a.cho")))
        assertTrue(isMetronomeScreenLeft(songDetails, CampfireDestination.Songs))
    }

    @Test
    fun theMetronomeTabSelectedFromASongStopsTheClick() = assertTrue(isMetronomeScreenLeft(songDetails, CampfireDestination.Metronome))

    /** A rename is the same song under a new name, so the click goes on in its bar. */
    @Test
    fun aRenamedSongIsNotMoved() = assertFalse(
        isMetronomeContextMoved(MetronomeContext.Song("a.cho", "s.setlist.json"), MetronomeContext.Song("b.cho", "s.setlist.json"), mapOf("a.cho" to "b.cho")),
    )

    @Test
    fun anotherSongIsMoved() = assertTrue(
        isMetronomeContextMoved(MetronomeContext.Song("a.cho", "s.setlist.json"), MetronomeContext.Song("b.cho", "s.setlist.json"), emptyMap()),
    )

    @Test
    fun theSameSongInAnotherSetlistIsMoved() = assertTrue(
        isMetronomeContextMoved(MetronomeContext.Song("a.cho", "s.setlist.json"), MetronomeContext.Song("b.cho", null), mapOf("a.cho" to "b.cho")),
    )

    @Test
    fun aSongAfterTheTabIsMoved() =
        assertTrue(isMetronomeContextMoved(MetronomeContext.Standalone, MetronomeContext.Song("a.cho", null), emptyMap()))

    @Test
    fun theSameContextIsNotMoved() =
        assertFalse(isMetronomeContextMoved(MetronomeContext.Song("a.cho", null), MetronomeContext.Song("a.cho", null), emptyMap()))
}
