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

import com.pandulapeter.campfire.data.model.domain.MetronomeSettings
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.metronome.api.model.TimeSignature
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MetronomeContextTest {

    private val songDetails = CampfireDestination.SongDetails(songFileNames = listOf("a.cho", "b.cho"), setlistFileName = "s.setlist.json", initialIndex = 0, id = "1")
    private val currentSong = { destination: CampfireDestination.SongDetails -> destination.songFileNames[destination.initialIndex] }

    @Test
    fun `the song on top is the context`() = assertEquals(
        MetronomeContext.Song("a.cho", "s.setlist.json"),
        metronomeContextOf(listOf(CampfireDestination.Songs, songDetails), currentSong),
    )

    /** A click is stopped with the screen it was started on, so a song under another screen plays for nothing. */
    @Test
    fun `a song under another screen is not the context`() = assertEquals(
        MetronomeContext.Standalone,
        metronomeContextOf(listOf(CampfireDestination.Songs, songDetails, CampfireDestination.SongEditor("a.cho")), currentSong),
    )

    @Test
    fun `a stack without a song is standalone`() =
        assertEquals(MetronomeContext.Standalone, metronomeContextOf(listOf(CampfireDestination.Songs, CampfireDestination.Metronome), currentSong))

    @Test
    fun `the page being headed for is the song`() =
        assertEquals(MetronomeContext.Song("b.cho", "s.setlist.json"), metronomeContextOf(listOf(songDetails), currentSongOf = { "b.cho" }))

    /** A song whose file the library does not hold yet has no tempo to play, so the tab's own pattern stands in. */
    @Test
    fun `a song with no file is standalone`() =
        assertEquals(MetronomeContext.Standalone, metronomeContextOf(listOf(songDetails), currentSongOf = { null }))

    @Test
    fun `the stretch the page is headed for is part of the context`() {
        val timing = SongTiming(index = 0, bpm = 90, timeSignature = TimeSignature(3, 4))
        assertEquals(
            MetronomeContext.Song("a.cho", "s.setlist.json", timing),
            metronomeContextOf(listOf(songDetails), currentSongOf = currentSong, currentTimingOf = { timing }),
        )
    }

    @Test
    fun `the same song staying on top keeps the click`() = assertFalse(isMetronomeScreenLeft(songDetails, songDetails.copy()))

    /** A rename rewrites the screen's file names in place, which is still the screen the click was started on. */
    @Test
    fun `the same song under new file names keeps the click`() =
        assertFalse(isMetronomeScreenLeft(songDetails, songDetails.copy(songFileNames = listOf("c.cho", "b.cho"))))

    @Test
    fun `the metronome tab staying on top keeps the click`() = assertFalse(isMetronomeScreenLeft(CampfireDestination.Metronome, CampfireDestination.Metronome))

    @Test
    fun `a song opened over the metronome tab stops the click`() = assertTrue(isMetronomeScreenLeft(CampfireDestination.Metronome, songDetails))

    @Test
    fun `a song opened over another song stops the click`() = assertTrue(isMetronomeScreenLeft(songDetails, songDetails.copy(id = "2")))

    @Test
    fun `a screen without a metronome stops the click`() {
        assertTrue(isMetronomeScreenLeft(songDetails, CampfireDestination.SongEditor("a.cho")))
        assertTrue(isMetronomeScreenLeft(songDetails, CampfireDestination.Songs))
    }

    @Test
    fun `the metronome tab selected from a song stops the click`() = assertTrue(isMetronomeScreenLeft(songDetails, CampfireDestination.Metronome))

    /** A rename is the same song under a new name, so the click goes on in its bar. */
    @Test
    fun `a renamed song is not moved`() = assertFalse(
        isMetronomeContextMoved(MetronomeContext.Song("a.cho", "s.setlist.json"), MetronomeContext.Song("b.cho", "s.setlist.json"), mapOf("a.cho" to "b.cho")),
    )

    @Test
    fun `another song is moved`() = assertTrue(
        isMetronomeContextMoved(MetronomeContext.Song("a.cho", "s.setlist.json"), MetronomeContext.Song("b.cho", "s.setlist.json"), emptyMap()),
    )

    @Test
    fun `the same song in another setlist is moved`() = assertTrue(
        isMetronomeContextMoved(MetronomeContext.Song("a.cho", "s.setlist.json"), MetronomeContext.Song("b.cho", null), mapOf("a.cho" to "b.cho")),
    )

    @Test
    fun `a song after the tab is moved`() =
        assertTrue(isMetronomeContextMoved(MetronomeContext.Standalone, MetronomeContext.Song("a.cho", null), emptyMap()))

    @Test
    fun `another stretch of the song is moved`() {
        val opening = MetronomeContext.Song("a.cho", null)
        val bridge = MetronomeContext.Song("a.cho", null, SongTiming(index = 0, bpm = 90, timeSignature = TimeSignature(3, 4)))
        assertTrue(isMetronomeContextMoved(opening, bridge, emptyMap()))
        assertTrue(isMetronomeContextMoved(bridge, bridge.copy(timing = bridge.timing!!.copy(index = 1)), emptyMap()))
        assertTrue(isMetronomeContextMoved(bridge, opening, emptyMap()))
    }

    /** A stretch whose tempo scales with the opening one stepped is the same stretch, played on from the next beat. */
    @Test
    fun `the same stretch at another tempo is not moved`() {
        val bridge = MetronomeContext.Song("a.cho", null, SongTiming(index = 0, bpm = 90, timeSignature = TimeSignature(3, 4)))
        assertFalse(isMetronomeContextMoved(bridge, bridge.copy(timing = bridge.timing!!.copy(bpm = 95)), emptyMap()))
    }

    @Test
    fun `the same context is not moved`() =
        assertFalse(isMetronomeContextMoved(MetronomeContext.Song("a.cho", null), MetronomeContext.Song("a.cho", null), emptyMap()))

    @Test
    fun `only the metronome tab and a song can start a click, and only with the feature on`() {
        val startable = listOf(
            CampfireDestination.Metronome,
            CampfireDestination.SongDetails(songFileNames = listOf("a.cho"), setlistFileName = null, initialIndex = 0),
        )
        val others = listOf(
            null,
            CampfireDestination.Songs,
            CampfireDestination.Setlists,
            CampfireDestination.Settings,
            CampfireDestination.SongEditor(fileName = "a.cho"),
            CampfireDestination.ImportReport,
        )
        startable.forEach { assertTrue(isMetronomeStartable(it, isMetronomeEnabled = true), it.toString()) }
        others.forEach { assertFalse(isMetronomeStartable(it, isMetronomeEnabled = true), it.toString()) }
        (startable + others).forEach { assertFalse(isMetronomeStartable(it, isMetronomeEnabled = false), it.toString()) }
    }

    @Test
    fun `the bar the panel draws is the stretch's, then the song's, then the default, and the tab's own standalone`() {
        val song = Song(
            fileName = "a.cho",
            title = "A",
            artist = "",
            key = null,
            transpose = 0,
            tags = emptyList(),
            languages = emptyList(),
            coverArtUrl = null,
            hasChords = true,
            canUpdateFileName = false,
            lastModified = 0,
            size = 0,
            time = "6/8",
        )
        val settings = MetronomeSettings(timeSignature = "3/4")
        val songOf = { fileName: String -> song.takeIf { it.fileName == fileName } }
        assertEquals(TimeSignature(6, 8), metronomeTimeSignatureOf(MetronomeContext.Song("a.cho", null), settings, songOf))
        assertEquals(
            TimeSignature(2, 4),
            metronomeTimeSignatureOf(MetronomeContext.Song("a.cho", null, SongTiming(index = 1, bpm = null, timeSignature = TimeSignature(2, 4))), settings, songOf),
        )
        assertEquals(TimeSignature.COMMON_TIME, metronomeTimeSignatureOf(MetronomeContext.Song("gone.cho", null), settings, songOf))
        assertEquals(TimeSignature(3, 4), metronomeTimeSignatureOf(MetronomeContext.Standalone, settings, songOf))
    }
}
