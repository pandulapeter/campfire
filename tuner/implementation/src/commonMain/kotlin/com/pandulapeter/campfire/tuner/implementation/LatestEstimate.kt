/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.tuner.implementation

/**
 * The detector's answer for the window at a position, kept until the input hands out a new one: an input that hands out
 * the same window again between two of its buffers (an iOS tap) is read again by the tracker, whose timings expect a
 * step every poll, but the window is not analysed twice. A position identifies the window's samples, since it counts
 * the frames delivered up to the window's end and the window's length is fixed for a session.
 */
internal class LatestEstimate(
    private val detect: (window: FloatArray, minFrequency: Float, maxFrequency: Float) -> PitchEstimate,
) {
    private var position = AudioInput.NO_WINDOW
    private var range: ClosedFloatingPointRange<Float>? = null
    private var estimate: PitchEstimate? = null

    fun of(window: FloatArray, position: Long, range: ClosedFloatingPointRange<Float>): PitchEstimate {
        estimate?.takeIf { position == this.position && range == this.range }?.let { return it }
        return detect(window, range.start, range.endInclusive).also {
            this.position = position
            this.range = range
            estimate = it
        }
    }
}
