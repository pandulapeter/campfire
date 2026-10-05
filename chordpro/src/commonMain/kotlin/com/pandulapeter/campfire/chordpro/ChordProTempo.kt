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

import kotlin.math.roundToInt

/**
 * Reads the value of `{tempo}`, which ChordPro documents as beats per minute but which people write every way a
 * score does: `120`, `120 bpm`, `♩ = 96`, `97.5`. The first number in it counts, rounded to a whole one; a value with
 * no number, or one that is not positive, is no tempo. Holding it within what a metronome plays is the caller's, since
 * the page shows what the file says.
 */
object ChordProTempo {

    private val NUMBER = Regex("""\d+(?:[.,]\d+)?""")

    fun parse(text: String?): Int? {
        val number = NUMBER.find(text ?: return null)?.value ?: return null
        return number.replace(',', '.').toDoubleOrNull()?.roundToInt()?.takeIf { it > 0 }
    }
}
