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

class AudioClockTest {

    @Test
    fun `elapsed microseconds are counted in whole frames`() {
        assertEquals(48_000L, AudioClock.framesIn(1_000_000, 48_000))
        assertEquals(999L, AudioClock.framesIn(20_833, 48_000))
    }

    @Test
    fun `a reported position is extrapolated to now`() {
        assertEquals(1_000L, AudioClock.extrapolatedFrame(1_000, 5_000_000_000, 5_000_000_000, 44_100))
        assertEquals(23_050L, AudioClock.extrapolatedFrame(1_000, 5_000_000_000, 5_500_000_000, 44_100))
    }

    @Test
    fun `a clock in seconds truncates toward zero and is negative before the start`() {
        assertEquals(47_999L, AudioClock.framesSince(10.99999, 10.0, 48_000))
        assertEquals(-24_000L, AudioClock.framesSince(9.5, 10.0, 48_000))
        assertEquals(0L, AudioClock.framesSince(9.99999, 10.0, 48_000))
        assertEquals(480L, AudioClock.latencyFrames(0.01, 48_000))
    }

    @Test
    fun `a track hears nothing before its first timestamp, and only one that never reports one goes by its head`() {
        assertEquals(-1L, AudioClock.heardFrameBeforeTimestamp(headFrame = 0, elapsedNanos = 0))
        assertEquals(-1L, AudioClock.heardFrameBeforeTimestamp(headFrame = 9_600, elapsedNanos = 200_000_000))
        assertEquals(96_000L, AudioClock.heardFrameBeforeTimestamp(headFrame = 96_000, elapsedNanos = 2_000_000_000))
    }

    @Test
    fun `a starved queue adds its gap once and for the rest of the session`() {
        val tracker = PlaybackGapTracker()
        tracker.onBufferFreed(scheduledFrames = 4_800, playerFrame = 4_000)
        tracker.onBufferFreed(scheduledFrames = 4_800, playerFrame = 4_800)
        assertEquals(0L, tracker.silentFrames)
        tracker.onBufferFreed(scheduledFrames = 4_800, playerFrame = 5_800)
        assertEquals(1_000L, tracker.silentFrames)
        tracker.onBufferFreed(scheduledFrames = 5_760, playerFrame = null)
        assertEquals(1_000L, tracker.silentFrames)
        tracker.onBufferFreed(scheduledFrames = 5_760, playerFrame = 7_260)
        assertEquals(1_500L, tracker.silentFrames)
        assertEquals(10_000L - 1_500 - 480, tracker.heardFrame(playerFrame = 10_000, latencyFrames = 480))
    }
}
