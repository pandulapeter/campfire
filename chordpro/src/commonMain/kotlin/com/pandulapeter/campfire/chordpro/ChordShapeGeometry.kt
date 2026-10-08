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

import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordVoicing

/** What a hand does with a fretted shape: where its diagram starts, how many fingers it takes and whether it can be held. */
internal object ChordShapeGeometry {

    /**
     * The fret a diagram of [frets] starts at: the nut where the shape fits into the first four frets, and otherwise
     * its lowest stopped fret. A definition's `base-fret` is written the same way, so that the line and the diagram
     * agree about where the shape is.
     */
    fun baseFret(frets: List<Int?>): Int {
        val stopped = frets.filterNotNull().filter { it > 0 }
        return if (stopped.isEmpty() || stopped.max() <= DIAGRAM_FRETS) 1 else stopped.min()
    }

    /**
     * How many fingers [frets] takes. The lowest stopped fret may be held as a barre, one finger across every string
     * from the first to the last it stops, where no string in between is open or muted (which the barre would stop),
     * and neighbouring strings stopped at the same fret higher up are one finger laid flat across them, the way the
     * ring finger holds the three strings of an A shaped barre chord. Every other stopped string takes a finger of its
     * own.
     */
    fun fingerCount(frets: List<Int?>): Int {
        val stopped = frets.indices.filter { (frets[it] ?: 0) > 0 }
        if (stopped.isEmpty()) return 0
        val lowest = stopped.minOf { frets[it]!! }
        val atLowest = stopped.filter { frets[it] == lowest }
        val barres = listOf(emptySet<Int>()) + atLowest.flatMap { first ->
            atLowest.filter { it > first && (first..it).all { string -> (frets[string] ?: 0) >= lowest } }.map { last ->
                atLowest.filter { it in first..last }.toSet()
            }
        }
        return barres.minOf { barre ->
            val rest = stopped.filter { it !in barre }
            (if (barre.isEmpty()) 0 else 1) + rest.count { string -> string - 1 !in rest || frets[string - 1] != frets[string] }
        }
    }

    /** Whether a hand can hold [frets]: four fingers at most, a barre counting as one, within the first fifteen frets. */
    fun isHoldable(frets: List<Int?>) = fingerCount(frets) <= MAX_FINGERS && frets.all { it == null || it in 0..MAX_HOLDABLE_FRET }

    /** The pitch classes [voicing] sounds on [instrument]. */
    fun pitchClasses(voicing: ChordVoicing, instrument: ChordInstrument): Set<Int> = when (voicing) {
        is ChordVoicing.Fretted -> voicing.frets.mapIndexedNotNull { string, fret -> fret?.let { (instrument.tuning[string] + it) % 12 } }.toSet()
        is ChordVoicing.Keys -> (voicing.notes + listOfNotNull(voicing.bass)).map { it % 12 }.toSet()
    }

    /** The lowest fret [frets] stops, or 0 for a shape that stops none. */
    fun position(frets: List<Int?>) = frets.filterNotNull().filter { it > 0 }.minOrNull() ?: 0

    /** How far apart the lowest and the highest stopped fret of [frets] are. */
    fun stretch(frets: List<Int?>) = frets.filterNotNull().filter { it > 0 }.let { if (it.isEmpty()) 0 else it.max() - it.min() }

    /** How many frets a diagram shows, which is also the widest a shape found by the search may reach. */
    const val DIAGRAM_FRETS = 4

    /** The frets an open string may ring with a stopped one: past them a hand high up the neck is far from the nut. */
    const val OPEN_POSITION_FRETS = 5

    /**
     * The fingers a fretting hand holds a shape with, the thumb not counted: the most a shape may need, and the highest
     * finger number a moved definition keeps.
     */
    const val MAX_FINGERS = 4

    private const val MAX_HOLDABLE_FRET = 15
}
