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

import com.pandulapeter.campfire.metronome.api.model.MetronomePattern
import com.pandulapeter.campfire.metronome.api.model.MetronomePlayback
import com.pandulapeter.campfire.metronome.api.model.TimeSignature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PendingTempoTest {

    @Test
    fun `a tempo waiting for the next bar is named with the one heard until then`() {
        val playback = MetronomePlayback.Playing(MetronomePattern(bpm = 96), pendingPattern = MetronomePattern(bpm = 120))
        assertEquals(PendingTempo(fromBpm = 96, toBpm = 120), pendingTempoOf(playback))
    }

    @Test
    fun `nothing is pending where nothing waits, the click is stopped or the tempo stays`() {
        assertNull(pendingTempoOf(MetronomePlayback.Playing(MetronomePattern(bpm = 96))))
        assertNull(pendingTempoOf(MetronomePlayback.Stopped()))
        val sameTempo = MetronomePattern(bpm = 96, timeSignature = TimeSignature(3, 4))
        assertNull(pendingTempoOf(MetronomePlayback.Playing(MetronomePattern(bpm = 96), pendingPattern = sameTempo)))
    }
}
