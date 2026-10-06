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
import com.pandulapeter.campfire.metronome.api.model.BeatLevel
import com.pandulapeter.campfire.metronome.api.model.MetronomeSound
import com.pandulapeter.campfire.metronome.api.model.Subdivision
import com.pandulapeter.campfire.metronome.api.model.TimeSignature
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
    fun aSongPlaysItsOwnTempoAndSignatureWithTheAccentsDrawnForIt() {
        val pattern = metronomePatternOf(MetronomeContext.Song("a.cho", null), settings, mapOf("a.cho" to song)::get, Tempos())
        assertEquals(96, pattern.bpm)
        assertEquals(TimeSignature(7, 8), pattern.timeSignature)
        assertEquals(BeatLevel.ACCENT, pattern.beatLevels[2])
        assertEquals(MetronomeSound.COWBELL, pattern.sound)
        assertEquals(Subdivision.TRIPLETS, pattern.subdivision)
    }

    @Test
    fun aStretchAfterAChangePlaysItsOwnTempoAndSignature() {
        val timing = SongTiming(index = 0, bpm = 55, timeSignature = TimeSignature(3, 4))
        val pattern = metronomePatternOf(MetronomeContext.Song("a.cho", null, timing), settings, mapOf("a.cho" to song)::get, Tempos())
        assertEquals(55, pattern.bpm)
        assertEquals(TimeSignature(3, 4), pattern.timeSignature)
        assertEquals(TimeSignature(3, 4).defaultBeatLevels(), pattern.beatLevels)
    }

    /** A stretch of a song that names no tempo is played at the song's own, an override of it included. */
    @Test
    fun aStretchWithNoTempoPlaysTheSongsOwn() {
        val timing = SongTiming(index = 0, bpm = null, timeSignature = TimeSignature(6, 8))
        val tempos = Tempos().with(TempoKey("a.cho", null), 110)
        val pattern = metronomePatternOf(MetronomeContext.Song("a.cho", null, timing), settings, mapOf("a.cho" to song)::get, tempos)
        assertEquals(110, pattern.bpm)
        assertEquals(TimeSignature(6, 8), pattern.timeSignature)
    }

    @Test
    fun theTabPlaysItsOwn() {
        val pattern = metronomePatternOf(MetronomeContext.Standalone, settings, { null }, Tempos())
        assertEquals(140, pattern.bpm)
        assertEquals(TimeSignature(3, 4), pattern.timeSignature)
        assertEquals(TimeSignature(3, 4).defaultBeatLevels(), pattern.beatLevels)
    }

    @Test
    fun aSongThatSaysNothingIsCommonTimeAtTheDefault() {
        val pattern = metronomePatternOf(MetronomeContext.Song("b.cho", null), settings, { null }, Tempos())
        assertEquals(120, pattern.bpm)
        assertEquals(TimeSignature.COMMON_TIME, pattern.timeSignature)
    }

    @Test
    fun unknownIdsAndAccentsOfTheWrongLengthFallBack() {
        val broken = MetronomeSettings(soundId = "kazoo", subdivisionId = "quintuplets", beatLevels = mapOf("4/4" to listOf("accent")))
        val pattern = metronomePatternOf(MetronomeContext.Standalone, broken, { null }, Tempos())
        assertEquals(MetronomeSound.CLICK, pattern.sound)
        assertEquals(Subdivision.NONE, pattern.subdivision)
        assertEquals(TimeSignature.COMMON_TIME.defaultBeatLevels(), pattern.beatLevels)
    }
}
