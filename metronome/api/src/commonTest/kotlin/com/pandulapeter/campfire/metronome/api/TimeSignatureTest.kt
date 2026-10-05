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

import com.pandulapeter.campfire.metronome.api.model.BeatLevel.ACCENT
import com.pandulapeter.campfire.metronome.api.model.BeatLevel.NORMAL
import com.pandulapeter.campfire.metronome.api.model.TimeSignature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TimeSignatureTest {

    @Test
    fun parsesWhatItWrites() = assertEquals(TimeSignature(7, 8), TimeSignature.parse(TimeSignature(7, 8).toString()))

    @Test
    fun rejectsSignaturesOutOfRange() {
        assertNull(TimeSignature.parse("17/4"))
        assertNull(TimeSignature.parse("4/3"))
        assertNull(TimeSignature.parse("fast"))
    }

    @Test
    fun accentsTheFirstBeat() = assertEquals(listOf(ACCENT, NORMAL, NORMAL, NORMAL), TimeSignature(4, 4).defaultBeatLevels())

    @Test
    fun accentsEveryGroupOfThreeInACompoundMeter() {
        assertEquals(listOf(ACCENT, NORMAL, NORMAL, ACCENT, NORMAL, NORMAL), TimeSignature(6, 8).defaultBeatLevels())
        assertEquals(listOf(ACCENT, NORMAL, NORMAL, NORMAL, NORMAL, NORMAL), TimeSignature(6, 4).defaultBeatLevels())
    }
}
