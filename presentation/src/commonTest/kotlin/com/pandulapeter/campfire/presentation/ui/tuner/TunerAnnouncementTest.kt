/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.tuner

import kotlin.test.Test
import kotlin.test.assertEquals

class TunerAnnouncementTest {

    @Test
    fun `a reading the tracker calls in tune is in tune whatever its cents`() {
        assertEquals(TunerOffset.IN_TUNE, tunerOffsetOf(3f, isInTune = true))
        assertEquals(TunerOffset.IN_TUNE, tunerOffsetOf(-4.9f, isInTune = true))
    }

    @Test
    fun `a reading within fifteen cents is flat or sharp by its sign`() {
        assertEquals(TunerOffset.FLAT, tunerOffsetOf(-15f, isInTune = false))
        assertEquals(TunerOffset.FLAT, tunerOffsetOf(-1f, isInTune = false))
        assertEquals(TunerOffset.SHARP, tunerOffsetOf(0.5f, isInTune = false))
        assertEquals(TunerOffset.SHARP, tunerOffsetOf(15f, isInTune = false))
    }

    @Test
    fun `a reading beyond fifteen cents is far flat or far sharp`() {
        assertEquals(TunerOffset.FAR_FLAT, tunerOffsetOf(-15.1f, isInTune = false))
        assertEquals(TunerOffset.FAR_FLAT, tunerOffsetOf(-40f, isInTune = false))
        assertEquals(TunerOffset.FAR_SHARP, tunerOffsetOf(15.1f, isInTune = false))
        assertEquals(TunerOffset.FAR_SHARP, tunerOffsetOf(60f, isInTune = false))
    }
}
