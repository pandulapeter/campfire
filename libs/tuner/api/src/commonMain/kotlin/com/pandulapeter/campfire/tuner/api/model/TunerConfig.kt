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
 * What a sound is read against: the nearest of [strings], or the nearest semitone where that is null (chromatic).
 *
 * @param referencePitch A4 in Hz, within `Pitch.REFERENCE_PITCH_RANGE`.
 * @param courses How [tuning] is strung. Paired strings add the second string of each course to what is listened for: a
 * twelve-string's octave string plucked alone is a note of its own, which the six strings would read as the wrong string
 * far out of tune.
 */
public data class TunerConfig(
    public val referencePitch: Int,
    public val tuning: InstrumentTuning?,
    public val courses: StringCourses = StringCourses.SINGLE,
) {

    /** The second string of each course of [tuning], in the order of its strings; null for single strings and chromatic. */
    public val partners: List<Int>? = tuning?.let(courses::partnersOf)

    /** The notes of every string listened for, both of each course where they are paired; null for chromatic. */
    public val strings: List<Int>? = tuning?.let { (it.strings + partners.orEmpty()).distinct() }
}
