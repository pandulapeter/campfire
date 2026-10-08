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
    fun `a signature parses back from what it writes`() = assertEquals(TimeSignature(7, 8), TimeSignature.parse(TimeSignature(7, 8).toString()))

    @Test
    fun `a signature out of range is rejected`() {
        assertNull(TimeSignature.parse("17/4"))
        assertNull(TimeSignature.parse("4/3"))
        assertNull(TimeSignature.parse("fast"))
    }

    @Test
    fun `a simple meter accents the first beat`() = assertEquals(listOf(ACCENT, NORMAL, NORMAL, NORMAL), TimeSignature(4, 4).defaultBeatLevels())

    @Test
    fun `a compound meter accents every group of three`() {
        assertEquals(listOf(ACCENT, NORMAL, NORMAL, ACCENT, NORMAL, NORMAL), TimeSignature(6, 8).defaultBeatLevels())
        assertEquals(listOf(ACCENT, NORMAL, NORMAL, NORMAL, NORMAL, NORMAL), TimeSignature(6, 4).defaultBeatLevels())
    }
}
