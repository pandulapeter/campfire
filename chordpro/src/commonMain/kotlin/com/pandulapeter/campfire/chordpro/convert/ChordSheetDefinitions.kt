/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.convert

import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordVoicing
import com.pandulapeter.campfire.chordpro.voicings.ChordProDefinitions

/**
 * The chord shapes a sheet lists for its less common chords, a chord's name and its frets from the lowest string, one
 * a line (`Bb(#11)/A = x03330`, `C: x32010`, `D7 (x-x-0-2-1-2)`, `C 0 0 0 3`), written as the `{define}` lines that
 * draw them. Six frets are a guitar's and four a ukulele's; a shape written as one word is read only with six, since four
 * digits after a word are as likely a year.
 *
 * A star marks a second shape of a chord the sheet also lists plain (`Bb` and `Bb*`), and the converter writes the
 * chord without its star wherever it is played, so neither line is written as a definition where both are listed: one
 * would stand for every place the song plays either. A starred shape listed alone is its chord's.
 */
internal object ChordSheetDefinitions {
    /**
     * The `{define}` line of each line of [lines] at [candidates] that lists a shape, by its index. [chordName] is the
     * name a written chord is defined under, or null for a word that is no chord.
     */
    fun definitions(lines: List<String>, candidates: Collection<Int>, chordName: (String) -> String?): Map<Int, String> {
        val shapes = candidates.mapNotNull { index -> shape(lines[index], chordName)?.let { index to it } }
        val listed = shapes.groupBy { (_, shape) -> shape.name }
        return shapes.filter { (_, shape) -> listed.getValue(shape.name).size == 1 }.associate { (index, shape) ->
            index to ChordProDefinitions.line(shape.name, ChordVoicing.Fretted(shape.frets), shape.instrument)
        }
    }

    private class Shape(val name: String, val frets: List<Int?>, val instrument: ChordInstrument)

    private fun shape(line: String, chordName: (String) -> String?): Shape? {
        val match = pattern.matchEntire(line.trim()) ?: return null
        val (written, value) = match.destructured
        val name = chordName(written.removeSuffix("*")) ?: return null
        val frets = (if (compact.matches(value)) value.map { it.toString() } else value.split(separator)).map { fret ->
            if (fret.equals("x", ignoreCase = true)) null else fret.toIntOrNull()?.takeIf { it in 0..24 } ?: return null
        }
        val instrument = when (frets.size) {
            6 -> ChordInstrument.GUITAR
            4 -> ChordInstrument.UKULELE
            else -> return null
        }
        return if (frets.any { it != null }) Shape(name, frets, instrument) else null
    }

    private val compact = Regex("[0-9xX]{6}")
    private val separator = Regex("[\\s.-]+")
    private val pattern = Regex(
        "^(\\S+?)\\s*(?:[=:]|-+>?|→)?\\s*\\(?([0-9xX]{6}|(?:(?:[0-9]{1,2}|[xX])[\\s.-]+){3,5}(?:[0-9]{1,2}|[xX]))\\)?$",
    )
}
