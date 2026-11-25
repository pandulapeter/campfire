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
 * A star marks a second shape of a chord the sheet also lists plain (`G/B` and `G/B*`). The converter writes the chord
 * without its star wherever it is played, and the app keeps it only on a tab's row of chord names, where it names no
 * chord it reads, so the starred shape has no name to be defined under: one under the plain name would mislabel the
 * plain chord, and one under the starred name would keep that name through a transposition that moves its frets. The
 * plain line is its chord's definition; the starred one is left out where every other line of its list became a
 * definition, and so is the label right above such a list (`Chords used:`), which would otherwise stand alone as a lyric.
 * Elsewhere it stays the line of text it was. A starred shape listed alone is its chord's, and a chord listed twice
 * plain is defined by neither line.
 */
internal object ChordSheetDefinitions {
    /** The `{define}` line of each line that lists a shape ([defines], by its index), and the lines that go without one. */
    class Listed(val defines: Map<Int, String>, val dropped: Set<Int>)

    /**
     * The shapes the lines of [lines] at [candidates] list. [chordName] is the name a written chord is defined under, or
     * null for a word that is no chord.
     */
    fun definitions(lines: List<String>, candidates: Collection<Int>, chordName: (String) -> String?): Listed {
        val shapes = candidates.sorted().mapNotNull { index -> shape(lines[index], chordName)?.let { index to it } }.toMap()
        val listed = shapes.values.groupBy { it.name }
        val defines = shapes.filterValues { shape ->
            val named = listed.getValue(shape.name)
            shape === (named.filter { !it.isStarred }.singleOrNull() ?: named.singleOrNull())
        }
        val variants = shapes.filter { (index, shape) -> index !in defines && shape.isStarred && listed.getValue(shape.name).any { it in defines.values } }.keys
        val dropped = mutableSetOf<Int>()
        for (run in runs(shapes.keys)) {
            if (run.all { it in defines || it in variants }) {
                dropped += run.filter { it in variants }
                (run.first() - 1).takeIf { it >= 0 && ChordSheetChordList.isLabel(lines[it]) }?.let { dropped += it }
            }
        }
        return Listed(
            defines = defines.mapValues { (_, shape) -> ChordProDefinitions.line(shape.name, ChordVoicing.Fretted(shape.frets), shape.instrument) },
            dropped = dropped,
        )
    }

    /** The [indices] (in order) cut into runs of consecutive lines: one list each. */
    private fun runs(indices: Collection<Int>): List<List<Int>> = indices.fold(mutableListOf<MutableList<Int>>()) { runs, index ->
        runs.apply { if (lastOrNull()?.last() == index - 1) last() += index else this += mutableListOf(index) }
    }

    private class Shape(val name: String, val frets: List<Int?>, val instrument: ChordInstrument, val isStarred: Boolean)

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
        return if (frets.any { it != null }) Shape(name, frets, instrument, written.endsWith("*")) else null
    }

    private val compact = Regex("[0-9xX]{6}")
    private val separator = Regex("[\\s.-]+")
    private val pattern = Regex(
        "^(\\S+?)\\s*(?:[=:]|-+>?|→)?\\s*\\(?([0-9xX]{6}|(?:(?:[0-9]{1,2}|[xX])[\\s.-]+){3,5}(?:[0-9]{1,2}|[xX]))\\)?$",
    )
}
