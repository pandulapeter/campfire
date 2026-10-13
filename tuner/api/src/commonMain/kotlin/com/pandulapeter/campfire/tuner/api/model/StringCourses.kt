/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.tuner.api.model

/**
 * How an instrument strings a tuning: one string to each note of [InstrumentTuning.strings], or a course of two strings
 * played as one, whose second strings ([partnersOf]) the tuner listens for too, since a course is tuned a string at a time
 * and the second string plucked alone may be a note of its own.
 */
public enum class StringCourses {
    SINGLE,

    /** Every course a pair in unison, the mandolin's. */
    UNISON_PAIRS,

    /**
     * A twelve-string guitar's six courses, strung in a six-string guitar's tuning: the four lowest paired with a string
     * an octave up, the two highest in unison.
     */
    TWELVE_STRING,
    ;

    /** The second string of each course of [tuning], in the same order as its strings, or null where there are none. */
    public fun partnersOf(tuning: InstrumentTuning): List<Int>? = when (this) {
        SINGLE -> null
        UNISON_PAIRS -> tuning.strings
        TWELVE_STRING -> tuning.strings.mapIndexed { index, note -> if (index < TWELVE_STRING_OCTAVE_COURSES) note + SEMITONES_PER_OCTAVE else note }
    }

    private companion object {
        const val TWELVE_STRING_OCTAVE_COURSES = 4
        const val SEMITONES_PER_OCTAVE = 12
    }
}
