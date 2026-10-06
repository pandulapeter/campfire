/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.metronome.api

import com.pandulapeter.campfire.metronome.api.model.BeatLevel.MUTED
import com.pandulapeter.campfire.metronome.api.model.BeatLevel.NORMAL
import com.pandulapeter.campfire.metronome.api.model.MetronomePattern
import com.pandulapeter.campfire.metronome.api.model.Subdivision
import com.pandulapeter.campfire.metronome.api.model.TimeSignature
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MetronomePatternTest {

    @Test
    fun soundsByDefault() = assertTrue(MetronomePattern(bpm = 120).canSound)

    @Test
    fun isSilentAtZeroVolume() {
        assertFalse(MetronomePattern(bpm = 120, volume = 0f).canSound)
        assertFalse(MetronomePattern(bpm = 120, beatLevels = listOf(NORMAL, NORMAL, NORMAL, NORMAL), volume = 0f).canSound)
    }

    @Test
    fun isSilentWithEveryBeatMuted() {
        val muted = listOf(MUTED, MUTED, MUTED, MUTED)
        assertFalse(MetronomePattern(bpm = 120, timeSignature = TimeSignature(4, 4), beatLevels = muted).canSound)
        assertFalse(
            MetronomePattern(
                bpm = 120,
                timeSignature = TimeSignature(4, 4),
                beatLevels = muted,
                subdivision = Subdivision.SIXTEENTHS,
            ).canSound,
        )
    }

    @Test
    fun soundsWithOneAudibleBeatAtAnyVolume() = assertTrue(
        MetronomePattern(
            bpm = 120,
            timeSignature = TimeSignature(4, 4),
            beatLevels = listOf(MUTED, MUTED, NORMAL, MUTED),
            volume = 0.01f,
        ).canSound,
    )

    @Test
    fun padsAShortListFromTheDefaults() = assertTrue(
        MetronomePattern(bpm = 120, timeSignature = TimeSignature(4, 4), beatLevels = listOf(MUTED)).canSound,
    )

    @Test
    fun cutsALongList() = assertFalse(
        MetronomePattern(bpm = 120, timeSignature = TimeSignature(3, 4), beatLevels = listOf(MUTED, MUTED, MUTED, NORMAL)).canSound,
    )
}
