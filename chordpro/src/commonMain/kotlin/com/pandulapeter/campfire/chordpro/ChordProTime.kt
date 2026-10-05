/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro

/**
 * Reads the value of `{time}`: a fraction (`3/4`, `6/8`), or the common time and cut time marks a score uses (`C` for
 * 4/4, `C|` for 2/2). A bar of 1 to 16 beats of a whole, half, quarter, eighth or sixteenth note is a signature; anything
 * else is none. Plain numbers rather than a type of its own, since `:chordpro` depends on nothing and the metronome
 * that plays it lives elsewhere.
 */
object ChordProTime {

    private val FRACTION = Regex("""(\d{1,2})\s*/\s*(\d{1,2})""")
    private val UNITS = setOf(1, 2, 4, 8, 16)

    /** The beats of a bar and the note each beat is, or null where [text] is not one of the shapes above. */
    fun parse(text: String?): Pair<Int, Int>? {
        val value = text?.trim() ?: return null
        return when (value) {
            "C" -> 4 to 4
            "C|", "¢" -> 2 to 2
            else -> FRACTION.matchEntire(value)?.let { match ->
                (match.groupValues[1].toInt() to match.groupValues[2].toInt()).takeIf { (beats, unit) -> beats in 1..16 && unit in UNITS }
            }
        }
    }
}
