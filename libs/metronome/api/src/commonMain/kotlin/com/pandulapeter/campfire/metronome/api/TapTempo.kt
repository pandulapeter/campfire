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

import com.pandulapeter.campfire.metronome.api.model.MetronomePattern
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Turns taps into a tempo. The tempo is a minute over the median of the last few intervals rather than their mean, so
 * one late or early tap does not move it, and a pause longer than [RESET_GAP] starts a new series, since nobody taps
 * a beat that slow and the previous series was about something else.
 */
public class TapTempo(private val timeSource: TimeSource = TimeSource.Monotonic) {

    private val intervals = ArrayDeque<Duration>()
    private var lastTap: TimeMark? = null

    /** Records a tap now and returns the tempo it makes, from the second tap of a series on, within the range. */
    public fun tap(): Int? {
        val gap = lastTap?.elapsedNow()
        lastTap = timeSource.markNow()
        if (gap == null || gap > RESET_GAP || !gap.isPositive()) {
            intervals.clear()
            return null
        }
        intervals.addLast(gap)
        while (intervals.size > MAXIMUM_INTERVALS) intervals.removeFirst()
        val sorted = intervals.sorted()
        val median = if (sorted.size % 2 == 1) sorted[sorted.size / 2] else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
        return MetronomePattern.coerceBpm((1.minutes / median).roundToInt())
    }

    /** Forgets the series, so that the next tap is a first one. */
    public fun reset() {
        intervals.clear()
        lastTap = null
    }

    private companion object {
        val RESET_GAP = 2.seconds
        const val MAXIMUM_INTERVALS = 8
    }
}
