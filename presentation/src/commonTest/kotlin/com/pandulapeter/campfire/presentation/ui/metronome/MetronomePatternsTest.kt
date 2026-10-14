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

import com.pandulapeter.campfire.data.model.preferences.MetronomeSettings
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.metronome.api.model.BeatLevel
import com.pandulapeter.campfire.metronome.api.model.MetronomeSound
import com.pandulapeter.campfire.metronome.api.model.Subdivision
import com.pandulapeter.campfire.metronome.api.model.TimeSignature
import com.pandulapeter.campfire.presentation.ui.playing.SongPlace
import com.pandulapeter.campfire.presentation.ui.playing.Tempos
import kotlin.test.Test
import kotlin.test.assertEquals

class MetronomePatternsTest {

    private val song = Song(
        fileName = "a.cho",
        title = "Title",
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
        tempo = 96,
        time = "7/8",
    )

    private val settings = MetronomeSettings(
        soundId = "cowbell",
        subdivisionId = "triplets",
        beatLevels = mapOf("7/8" to listOf("accent", "normal", "accent", "normal", "accent", "normal", "normal")),
        bpm = 140,
        timeSignature = "3/4",
    )

    @Test
    fun `a song plays its own tempo and signature with the accents drawn for it`() {
        val pattern = metronomePatternOf(MetronomeContext.Song("a.cho", null), settings, mapOf("a.cho" to song)::get, Tempos(), emptyMap())
        assertEquals(96, pattern.bpm)
        assertEquals(TimeSignature(7, 8), pattern.timeSignature)
        assertEquals(BeatLevel.ACCENT, pattern.beatLevels[2])
        assertEquals(MetronomeSound.COWBELL, pattern.sound)
        assertEquals(Subdivision.TRIPLETS, pattern.subdivision)
    }

    @Test
    fun `a stretch after a change plays its own tempo and signature`() {
        val timing = SongTiming(index = 0, bpm = 55, timeSignature = TimeSignature(3, 4))
        val pattern = metronomePatternOf(MetronomeContext.Song("a.cho", null, timing), settings, mapOf("a.cho" to song)::get, Tempos(), emptyMap())
        assertEquals(55, pattern.bpm)
        assertEquals(TimeSignature(3, 4), pattern.timeSignature)
        assertEquals(TimeSignature(3, 4).defaultBeatLevels(), pattern.beatLevels)
    }

    /** A stretch of a song that names no tempo is played at the song's own, an override of it included. */
    @Test
    fun `a stretch with no tempo plays the song's own`() {
        val timing = SongTiming(index = 0, bpm = null, timeSignature = TimeSignature(6, 8))
        val tempos = Tempos().with(SongPlace("a.cho", null), 110)
        val pattern = metronomePatternOf(MetronomeContext.Song("a.cho", null, timing), settings, mapOf("a.cho" to song)::get, tempos, emptyMap())
        assertEquals(110, pattern.bpm)
        assertEquals(TimeSignature(6, 8), pattern.timeSignature)
    }

    @Test
    fun `the tab plays its own`() {
        val pattern = metronomePatternOf(MetronomeContext.Standalone, settings, { null }, Tempos(), emptyMap())
        assertEquals(140, pattern.bpm)
        assertEquals(TimeSignature(3, 4), pattern.timeSignature)
        assertEquals(TimeSignature(3, 4).defaultBeatLevels(), pattern.beatLevels)
    }

    @Test
    fun `a song that says nothing is common time at the default`() {
        val pattern = metronomePatternOf(MetronomeContext.Song("b.cho", null), settings, { null }, Tempos(), emptyMap())
        assertEquals(120, pattern.bpm)
        assertEquals(TimeSignature.COMMON_TIME, pattern.timeSignature)
    }

    @Test
    fun `unknown IDs and accents of the wrong length fall back`() {
        val broken = MetronomeSettings(soundId = "kazoo", subdivisionId = "quintuplets", beatLevels = mapOf("4/4" to listOf("accent")))
        val pattern = metronomePatternOf(MetronomeContext.Standalone, broken, { null }, Tempos(), emptyMap())
        assertEquals(MetronomeSound.CLICK, pattern.sound)
        assertEquals(Subdivision.NONE, pattern.subdivision)
        assertEquals(TimeSignature.COMMON_TIME.defaultBeatLevels(), pattern.beatLevels)
    }

    @Test
    fun `a song's own accents win over the ones drawn for its signature, and only for that song`() {
        val own = mapOf("a.cho" to mapOf("7/8" to listOf("accent", "normal", "normal", "accent", "normal", "accent", "normal")))
        val songs = mapOf("a.cho" to song, "b.cho" to song.copy(fileName = "b.cho"))::get
        assertEquals(BeatLevel.ACCENT, metronomePatternOf(MetronomeContext.Song("a.cho", null), settings, songs, Tempos(), own).beatLevels[3])
        assertEquals(BeatLevel.NORMAL, metronomePatternOf(MetronomeContext.Song("b.cho", null), settings, songs, Tempos(), own).beatLevels[3])
    }

    @Test
    fun `a song's accents for another signature, or of the wrong length, fall back on the signature's`() {
        val own = mapOf("a.cho" to mapOf("7/8" to listOf("muted"), "3/4" to listOf("muted", "muted", "muted")))
        val pattern = metronomePatternOf(MetronomeContext.Song("a.cho", null), settings, mapOf("a.cho" to song)::get, Tempos(), own)
        assertEquals(settings.beatLevelsOf(TimeSignature(7, 8)), pattern.beatLevels)
    }

    @Test
    fun `the tab never plays a song's accents`() {
        val own = mapOf("a.cho" to mapOf("3/4" to listOf("muted", "muted", "muted")))
        val pattern = metronomePatternOf(MetronomeContext.Standalone, settings, { null }, Tempos(), own)
        assertEquals(TimeSignature(3, 4).defaultBeatLevels(), pattern.beatLevels)
    }

    @Test
    fun `accents tapped for a song are kept for it, and taken off once they are the signature's again`() {
        val waltz = TimeSignature(3, 4)
        val tapped = listOf(BeatLevel.ACCENT, BeatLevel.MUTED, BeatLevel.MUTED)
        val other = mapOf("b.cho" to mapOf("4/4" to listOf("accent", "normal", "accent", "normal")))
        val kept = other.withSongBeatLevels("a.cho", waltz, tapped, settings)
        assertEquals(tapped, settings.beatLevelsOf(waltz, kept["a.cho"]))
        assertEquals(other, kept.withSongBeatLevels("a.cho", waltz, settings.beatLevelsOf(waltz), settings))
    }
}
