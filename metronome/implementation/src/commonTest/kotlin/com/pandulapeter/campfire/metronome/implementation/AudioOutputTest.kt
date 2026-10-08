/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.metronome.implementation

import kotlin.test.Test
import kotlin.test.assertEquals

class AudioOutputTest {

    @Test
    fun `chunks and queues are counted in whole frames of the rate`() {
        assertEquals(960, AudioOutput.chunkFrames(48_000))
        assertEquals(4_800, AudioOutput.queuedFrames(48_000))
        assertEquals(882, AudioOutput.chunkFrames(44_100))
        assertEquals(4_410, AudioOutput.queuedFrames(44_100))
    }
}
