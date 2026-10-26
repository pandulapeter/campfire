/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.abc

import com.pandulapeter.campfire.chordpro.ChordProTempo
import com.pandulapeter.campfire.chordpro.ChordProTime

/** The values of the `K:`, `M:`, and `Q:` fields, written as the ChordPro directives that read them expect. */
internal object AbcFieldValues {

    private val minorModes = setOf("min", "aeo", "dor", "phr", "loc")
    private val meterSum = Regex("""\(?(\d+(?:\+\d+)+)\)?/(\d+)""")

    /**
     * The `{key}` for a `K:` value. `ChordProTransposer.keyOf` reads a chord name or a note and a word for major or
     * minor, and takes any other word for a chord suffix (`Edor` would be E major), so a mode becomes the key of the
     * chords it is played over: the minor-like ones (dorian, phrygian, aeolian, locrian) the tonic's minor, the rest its
     * major. ABC reads a mode by its first three letters, in any case, attached to the tonic or not. Clefs, explicit
     * accidentals, and other modifiers are dropped; `none`, the bagpipe keys (`HP`, `Hp`), and a `K:` naming only a
     * clef give no key.
     */
    fun key(value: String): String? {
        val tonic = value.firstOrNull()?.takeIf { it in 'A'..'G' } ?: return null
        val accidental = value.getOrNull(1)?.takeIf { it == '#' || it == 'b' }
        val mode = value.substring(if (accidental == null) 1 else 2).trimStart().takeWhile { it.isLetter() }.lowercase()
        val isMinor = mode == "m" || mode.take(3) in minorModes
        return tonic + accidental?.toString().orEmpty() + if (isMinor) "m" else ""
    }

    /**
     * The `{time}` for an `M:` value, or null where `ChordProTime.parse` can't read it (`M:none`, a free meter). A
     * compound numerator (`(3+2)/8`, `2+2+3/8`) is written as its total, since a metronome counts the bar's beats.
     */
    fun time(value: String): String? {
        val compact = value.filterNot { it.isWhitespace() }
        val time = meterSum.matchEntire(compact)?.let { match ->
            val beats = match.groupValues[1].split('+').sumOf { it.toIntOrNull() ?: 0 }
            "$beats/${match.groupValues[2]}"
        } ?: compact
        return time.takeIf { ChordProTime.parse(it) != null }
    }

    /**
     * The `{tempo}` for a `Q:` value: the number after its `=` (`1/4=120`, `"Allegro" 3/8=60`), since
     * `ChordProTempo.parse` takes the first number it sees, which there is the beat's length. A bare number is taken
     * as written; anything else (`Q:"Lively"`) gives no tempo.
     */
    fun tempo(value: String): String? {
        val unquoted = value.split('"').filterIndexed { index, _ -> index % 2 == 0 }.joinToString(" ")
        val number = if ('=' in unquoted) {
            unquoted.substringAfter('=').trimStart().takeWhile { it.isDigit() || it == '.' }
        } else {
            unquoted.trim().takeIf { text -> text.isNotEmpty() && text.all { it.isDigit() } }
        }
        return number?.takeIf { ChordProTempo.parse(it) != null }
    }
}
