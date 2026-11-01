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

import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SongAsPlayedTest {

    private val guitar = testPreferences().copy(instrument = UserPreferences.Instrument.GUITAR)
    private val keyboard = testPreferences().copy(instrument = UserPreferences.Instrument.KEYBOARD)

    /** G shapes at the second fret, which sound in A. */
    private val capoChart = "{title: Song}\n{key: G}\n{capo: 2}\n[G]One [C]two\n"

    private fun playback(transposition: Int = 0, capo: Int = 2, bpm: Int? = null, capoKeepsKey: Boolean = false) = SongPlayback(
        transposition = transposition,
        capo = EffectiveCapo(fret = capo, songFret = 2),
        tempo = if (bpm == null) {
            EffectiveTempo(bpm = 120, source = EffectiveTempo.Source.DEFAULT, songBpm = 120)
        } else {
            EffectiveTempo(bpm = bpm, source = EffectiveTempo.Source.LIBRARY_OVERRIDE, songBpm = 120)
        },
        capoKeepsKey = capoKeepsKey,
    )

    private fun asPlayed(playback: SongPlayback, preferences: UserPreferences = guitar) = songAsPlayedOf(playback, preferences)?.applyTo(capoChart)

    @Test
    fun `a song played as its file says is not rewritten`() {
        assertNull(songAsPlayedOf(playback(), guitar))
        assertNull(songAsPlayedOf(playback(capo = 4, capoKeepsKey = true).copy(capo = EffectiveCapo(fret = 2, songFret = 2)), guitar))
    }

    @Test
    fun `an override a switch hides is not written`() {
        assertNull(songAsPlayedOf(playback(transposition = 2, capo = 4), guitar.copy(areChordsEnabled = false)))
        assertNull(songAsPlayedOf(playback(bpm = 90), guitar.copy(isMetronomeEnabled = false)))
        assertEquals(
            "{title: Song}\n{key: G}\n{capo: 2}\n{tempo: 90}\n[G]One [C]two\n",
            asPlayed(playback(transposition = 2, bpm = 90), guitar.copy(areChordsEnabled = false)),
        )
    }

    @Test
    fun `a transposition moves the chords and the key, the capo staying`() =
        assertEquals("{title: Song}\n{key: A}\n{capo: 2}\n[A]One [D]two\n", asPlayed(playback(transposition = 2)))

    @Test
    fun `a capo that raises the pitch is written over the same shapes`() =
        assertEquals("{title: Song}\n{key: G}\n{capo: 4}\n[G]One [C]two\n", asPlayed(playback(capo = 4)))

    @Test
    fun `a capo that keeps the key is written over the shapes it is played with, so the file still sounds in A`() {
        assertEquals("{title: Song}\n{key: F}\n{capo: 4}\n[F]One [Bb]two\n", asPlayed(playback(capo = 4, capoKeepsKey = true)))
        // Taken off, the A is played open and the file names no capo.
        assertEquals("{title: Song}\n{key: A}\n[A]One [D]two\n", asPlayed(playback(capo = 0, capoKeepsKey = true)))
    }

    @Test
    fun `a keyboard player who keeps the key and changes nothing shown rewrites nothing`() {
        assertNull(songAsPlayedOf(playback(capo = 2, capoKeepsKey = true), keyboard))
        assertNull(songAsPlayedOf(playback(capo = 4, capoKeepsKey = true), keyboard))
        assertNull(songAsPlayedOf(playback(capo = 4, capoKeepsKey = true).copy(capo = EffectiveCapo(fret = 4, songFret = 0)), keyboard))
    }

    @Test
    fun `a keyboard player who keeps the key moves the chords by the transposition alone, the file's capo staying`() {
        val asPlayed = assertNotNull(songAsPlayedOf(playback(transposition = 2, capo = 4, capoKeepsKey = true), keyboard))
        assertNull(asPlayed.capo)
        assertEquals("{title: Song}\n{key: A}\n{capo: 2}\n[A]One [D]two\n", asPlayed.applyTo(capoChart))
    }

    @Test
    fun `a tempo alone is written, the chords keeping their spelling`() {
        val flats = guitar.copy(chordSpelling = guitar.chordSpelling.copy(accidentals = UserPreferences.Accidentals.FLATS))
        val written = assertNotNull(songAsPlayedOf(playback(bpm = 96), flats)).applyTo("{title: Song}\n{capo: 2}\n[A#]One\n")
        assertTrue("{tempo: 96}" in written, written)
        assertTrue("[A#]One" in written, written)
    }

    @Test
    fun `only the songs named are rewritten`() {
        val rewrite = mapOf("a.cho" to SongAsPlayed(semitones = 2, capo = null, tempo = null, transposition = 2, preferFlats = null))
        assertEquals("[A]la", rewrite.rewritten("a.cho", "[G]la"))
        assertEquals("[G]la", rewrite.rewritten("b.cho", "[G]la"))
    }

    private fun atTempo(bpm: Int, text: String) = assertNotNull(songAsPlayedOf(playback(bpm = bpm), guitar)).applyTo(text)

    @Test
    fun `a later tempo is scaled with the one played, as the page plays it`() = assertEquals(
        "{title: Song}\n{tempo: 110}\n[G]One\n{tempo: 55}\n[C]two\n",
        atTempo(110, "{title: Song}\n{tempo: 120}\n[G]One\n{tempo: 60}\n[C]two\n"),
    )

    @Test
    fun `a later tempo stays as written where the tempo is not overridden`() {
        val written = assertNotNull(songAsPlayedOf(playback(transposition = 2), guitar))
            .applyTo("{title: Song}\n{tempo: 120}\n[G]One\n{tempo: 60}\n[C]two\n")
        assertEquals("{title: Song}\n{tempo: 120}\n[A]One\n{tempo: 60}\n[D]two\n", written)
    }

    @Test
    fun `a later tempo with no number is kept as written`() = assertEquals(
        "{title: Song}\n{tempo: 110}\n[G]One\n{tempo: fast}\n[C]two\n",
        atTempo(110, "{title: Song}\n{tempo: 120}\n[G]One\n{tempo: fast}\n[C]two\n"),
    )

    @Test
    fun `a song whose tempo is its first change in the body is scaled once`() = assertEquals(
        "{title: Song}\n[G]One\n{tempo: 110}\n[C]two\n{tempo: 55}\n[D]three\n",
        atTempo(110, "{title: Song}\n[G]One\n{tempo: 120}\n[C]two\n{tempo: 60}\n[D]three\n"),
    )

    @Test
    fun `an unreadable opening tempo leaves its changes as written`() = assertEquals(
        "{title: Song}\n{tempo: 110}\n[G]One\n{tempo: 60}\n[C]two\n",
        atTempo(110, "{title: Song}\n{tempo: fast}\n[G]One\n{tempo: 60}\n[C]two\n"),
    )

    @Test
    fun `a change the metronome's range holds at its own number keeps its spelling`() = assertEquals(
        "{title: Song}\n{tempo: 110}\n[G]One\n{tempo: 30 bpm}\n[C]two\n",
        atTempo(110, "{title: Song}\n{tempo: 120}\n[G]One\n{tempo: 30 bpm}\n[C]two\n"),
    )
}
