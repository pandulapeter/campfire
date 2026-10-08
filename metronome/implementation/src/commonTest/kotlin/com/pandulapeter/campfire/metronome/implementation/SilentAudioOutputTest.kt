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

import com.pandulapeter.campfire.metronome.api.model.MetronomeAudioIssue
import com.pandulapeter.campfire.metronome.api.model.MetronomePattern
import com.pandulapeter.campfire.metronome.api.model.MetronomeStopReason
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

class SilentAudioOutputTest {

    @Test
    fun `the heard frame follows the clock and is gone after a stop`() = runTest {
        val timeSource = TestTimeSource()
        val output = SilentAudioOutput(backgroundScope, timeSource, StandardTestDispatcher(testScheduler))
        assertEquals(-1L, output.heardFrame())
        output.start(isPreview = false, createStream = { ClickStream(it, MetronomePattern(bpm = 120)) }, listener = NoListener)
        assertEquals(0L, output.heardFrame())
        timeSource += 250.milliseconds
        assertEquals(12_000L, output.heardFrame())
        output.stop()
        assertEquals(-1L, output.heardFrame())
    }

    private object NoListener : AudioOutputListener {
        override fun onLost(reason: MetronomeStopReason) = Unit

        override fun onAudioIssueChanged(issue: MetronomeAudioIssue?) = Unit
    }
}
