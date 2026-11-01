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

import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SongPlaybackTest {

    private val song = Song(
        fileName = FILE_NAME,
        title = "Title",
        artist = "",
        key = "G",
        transpose = 0,
        tags = emptyList(),
        languages = emptyList(),
        coverArtUrl = null,
        hasChords = true,
        canUpdateFileName = false,
        lastModified = 0,
        size = 0,
        tempo = 100,
        capo = 2,
    )

    private val library = SongPlace(FILE_NAME, null)
    private val setlist = SongPlace(FILE_NAME, SETLIST)

    private val overrides = PlayingOverridesSnapshot(
        transpositions = Transpositions(SongOverrides<Int>().with(library, 3).with(setlist, 7)),
        capos = Capos().with(library, 5).with(setlist, 1),
        tempos = Tempos().with(library, 120).with(setlist, 90),
    )

    @Test
    fun `a song opened from the library reads the library's overrides`() {
        val playback = songPlaybackOf(song, setlistFileName = null, overrides = overrides)
        assertEquals(3, playback.transposition)
        assertEquals(5, playback.capo.fret)
        assertEquals(120, playback.tempo.bpm)
    }

    @Test
    fun `a song opened from a setlist reads only that setlist's entry, wrapped around the octave`() {
        val playback = songPlaybackOf(song, setlistFileName = SETLIST, overrides = overrides)
        assertEquals(-5, playback.transposition)
        assertEquals(1, playback.capo.fret)
        assertEquals(90, playback.tempo.bpm)
        val other = songPlaybackOf(song, setlistFileName = "other.setlist.json", overrides = overrides)
        assertEquals(0, other.transposition)
        assertEquals(2, other.capo.fret)
        assertEquals(100, other.tempo.bpm)
    }

    @Test
    fun `an override equal to the file's own is the default`() {
        val same = PlayingOverridesSnapshot(capos = Capos().with(library, 2), tempos = Tempos().with(library, 100))
        val playback = songPlaybackOf(song, setlistFileName = null, overrides = same)
        assertTrue(playback.capo.isDefault)
        assertTrue(playback.tempo.isDefault)
    }

    @Test
    fun `the chords sound at the transposition and the effective capo, wrapped around the octave`() {
        assertEquals(-4, songPlaybackOf(song, setlistFileName = null, overrides = overrides).soundingTransposition)
        assertEquals(-4, songPlaybackOf(song, setlistFileName = SETLIST, overrides = overrides).soundingTransposition)
        assertEquals(2, songPlaybackOf(song, setlistFileName = "other.setlist.json", overrides = overrides).soundingTransposition)
    }

    @Test
    fun `a capo override keeps the shapes and raises the pitch`() {
        val guitar = testPreferences().copy(instrument = UserPreferences.Instrument.GUITAR)
        val shapeMove = { playback: SongPlayback -> playback.soundingTransposition - playback.shapeCapoFor(guitar) }
        val own = songPlaybackOf(song, setlistFileName = null, overrides = PlayingOverridesSnapshot())
        assertEquals(2, own.soundingTransposition)
        assertEquals(0, shapeMove(own))
        val raised = songPlaybackOf(song, setlistFileName = null, overrides = PlayingOverridesSnapshot(capos = Capos().with(library, 3)))
        assertEquals(3, raised.soundingTransposition)
        assertEquals(0, shapeMove(raised))
        val takenOff = songPlaybackOf(song, setlistFileName = SETLIST, overrides = PlayingOverridesSnapshot(capos = Capos().with(setlist, 0)))
        assertEquals(0, takenOff.soundingTransposition)
        assertEquals(0, shapeMove(takenOff))
        val transposed = PlayingOverridesSnapshot(
            transpositions = Transpositions(SongOverrides<Int>().with(library, 3)),
            capos = Capos().with(library, 5),
        )
        assertEquals(3, shapeMove(songPlaybackOf(song, setlistFileName = null, overrides = transposed)).mod(12))
    }

    @Test
    fun `a tab's frets move by the transposition alone, whatever the capo`() {
        val guitar = testPreferences().copy(instrument = UserPreferences.Instrument.GUITAR)
        val fretMove = { playback: SongPlayback -> playback.soundingTransposition - playback.tabCapoFor(guitar) }
        assertEquals(0, fretMove(songPlaybackOf(song, setlistFileName = null, overrides = PlayingOverridesSnapshot())))
        assertEquals(0, fretMove(songPlaybackOf(song, setlistFileName = SETLIST, overrides = PlayingOverridesSnapshot(capos = Capos().with(setlist, 0)))))
        val withoutCapo = song.copy(capo = null)
        assertEquals(0, fretMove(songPlaybackOf(withoutCapo, setlistFileName = null, overrides = PlayingOverridesSnapshot(capos = Capos().with(library, 7)))))
        val transposed = PlayingOverridesSnapshot(transpositions = Transpositions(SongOverrides<Int>().with(library, 5)), capos = Capos().with(library, 4))
        assertEquals(5, fretMove(songPlaybackOf(song, setlistFileName = null, overrides = transposed)))
    }

    @Test
    fun `the keyboard plays the sounding chords, the capo counted in`() {
        val keyboard = testPreferences().copy(instrument = UserPreferences.Instrument.KEYBOARD)
        val playback = songPlaybackOf(song, setlistFileName = null, overrides = overrides)
        assertEquals(-4, playback.soundingTransposition)
        assertEquals(0, playback.shapeCapoFor(keyboard))
    }

    @Test
    fun `only the keyboard plays without a capo`() {
        assertTrue(testPreferences().copy(instrument = UserPreferences.Instrument.KEYBOARD).playsWithoutCapo)
        UserPreferences.Instrument.entries.filter { it != UserPreferences.Instrument.KEYBOARD }.forEach {
            assertFalse(testPreferences().copy(instrument = it).playsWithoutCapo)
        }
        assertFalse(null.playsWithoutCapo)
    }

    private companion object {
        const val FILE_NAME = "song.cho"
        const val SETLIST = "set.setlist.json"
    }
}
