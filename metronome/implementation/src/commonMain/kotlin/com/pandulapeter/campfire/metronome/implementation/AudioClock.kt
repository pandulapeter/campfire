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

/**
 * The arithmetic that turns an output's clock into a frame of the click stream, kept out of the platform source sets so
 * that it can be tested. Each function is the expression an output wrote inline, in the same integer and floating
 * types and the same order of operations, so that the frames come out bit for bit as they did.
 */
internal object AudioClock {

    /** The frames [elapsedMicros] microseconds hold at [sampleRate], truncated. */
    fun framesIn(elapsedMicros: Long, sampleRate: Int): Long = elapsedMicros * sampleRate / 1_000_000

    /** The frame being played at [nowNanos], extrapolated from the [framePosition] the output reported at [framePositionNanos]. */
    fun extrapolatedFrame(framePosition: Long, framePositionNanos: Long, nowNanos: Long, sampleRate: Int): Long =
        framePosition + (nowNanos - framePositionNanos) * sampleRate / 1_000_000_000L

    /** The frames from [startSeconds] to [nowSeconds] on a clock counted in seconds, truncated toward zero: negative before the start. */
    fun framesSince(nowSeconds: Double, startSeconds: Double, sampleRate: Int): Long = ((nowSeconds - startSeconds) * sampleRate).toLong()

    /** The frames a latency of [latencySeconds] holds at [sampleRate], truncated. */
    fun latencyFrames(latencySeconds: Double, sampleRate: Int): Long = (latencySeconds * sampleRate).toLong()
}
