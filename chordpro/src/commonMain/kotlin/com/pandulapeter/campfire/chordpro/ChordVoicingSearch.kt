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

import com.pandulapeter.campfire.chordpro.ChordShapeGeometry.DIAGRAM_FRETS
import com.pandulapeter.campfire.chordpro.ChordShapeGeometry.MAX_FINGERS
import com.pandulapeter.campfire.chordpro.ChordShapeGeometry.OPEN_POSITION_FRETS
import com.pandulapeter.campfire.chordpro.ChordShapeGeometry.fingerCount
import com.pandulapeter.campfire.chordpro.ChordShapeGeometry.position
import com.pandulapeter.campfire.chordpro.ChordShapeGeometry.stretch
import com.pandulapeter.campfire.chordpro.model.Chord
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordVoicing

/**
 * Where the shapes of [ChordVoicings] come from, without remembering any of them: the tables' shapes of a chord, moved
 * to it from another root where they have no open string, the search for every other shape of a fretted instrument,
 * and the keyboard's inversions. See [ChordVoicings] for the rules each follows.
 */
internal object ChordVoicingSearch {

    /** The tables of [instrument], read once. */
    private fun table(instrument: ChordInstrument) = when (instrument) {
        ChordInstrument.GUITAR -> guitarTable
        ChordInstrument.UKULELE -> ukuleleTable
        ChordInstrument.KEYBOARD -> emptyList()
    }

    private class TableShape(val chord: Chord, val voicing: ChordVoicing.Fretted) {
        val isMovable = voicing.frets.none { it == 0 }
    }

    private val guitarTable by lazy { readTable(ChordVoicingTables.guitar, ChordInstrument.GUITAR) }
    private val ukuleleTable by lazy { readTable(ChordVoicingTables.ukulele, ChordInstrument.UKULELE) }

    private fun readTable(lines: List<String>, instrument: ChordInstrument) = lines.map { line ->
        val reading = ChordProDefinitions.read(line) as ChordProDefinitions.Reading.Shape
        check(reading.instrument == instrument) { line }
        TableShape(checkNotNull(ChordProChords.parse(reading.name)) { line }, reading.voicing as ChordVoicing.Fretted)
    }

    /**
     * The tables' shapes of [chord]: the open position shapes filed under it, in the tables' order, and then every
     * shape with no open string, filed under it or moved to it from another root, from the lowest position up.
     */
    fun tableShapes(chord: Chord, instrument: ChordInstrument): List<ChordVoicing.Fretted> {
        val target = playedOn(chord, instrument)
        val shapes = table(instrument)
        val open = shapes.filter { !it.isMovable && it.chord == target }.map { it.voicing }
        val movable = shapes.filter { it.isMovable && it.chord.intervals == target.intervals && it.chord.bassInterval == target.bassInterval }
            .flatMap { shape -> shiftsOf(shape.voicing.frets, (target.root - shape.chord.root).mod(12)).map { shape.voicing.shiftedBy(it) } }
            .sortedWith(compareBy({ position(it.frets) }, { -it.frets.count { fret -> fret != null } }))
        return (open + movable).distinctBy { it.frets }
    }

    private val Chord.bassInterval get() = bass?.let { (it - root).mod(12) }

    /** The ukulele's fourth string is not its lowest, so a slash chord is played on it as the chord over it. */
    private fun playedOn(chord: Chord, instrument: ChordInstrument) = if (instrument == ChordInstrument.UKULELE) chord.copy(bass = null) else chord

    /**
     * The moves by [interval] and its octaves that keep a shape with no open string between the first and the ninth
     * fret. Higher up a shape is still found by the search, but it is no longer one a chart would show for the chord.
     */
    private fun shiftsOf(frets: List<Int?>, interval: Int): List<Int> {
        val lowest = frets.filterNotNull().min()
        return (-1..1).map { interval + it * 12 }.filter { shift -> lowest + shift in 1..MAX_TABLE_POSITION }
    }

    private fun ChordVoicing.Fretted.shiftedBy(frets: Int) = if (frets == 0) this else copy(frets = this.frets.map { it?.plus(frets) })

    /** Every shape the search finds for [chord] on a fretted [instrument], the lowest position first. */
    fun search(chord: Chord, instrument: ChordInstrument): List<ChordVoicing.Fretted> {
        val played = playedOn(chord, instrument)
        val tuning = instrument.tuning
        val pitchClasses = played.pitchClasses
        val required = pitchClasses - omissions(played, tuning.size)
        // Every string sounds one note, so a chord that needs more notes than there are strings has no shape at all.
        if (required.size > tuning.size) return emptyList()
        val lowest = if (instrument == ChordInstrument.UKULELE) null else played.bass ?: played.root.takeIf { it in required }
        val minimumStrings = if (instrument == ChordInstrument.UKULELE) tuning.size else if (pitchClasses.size <= 2) 3 else 4
        val found = mutableSetOf<List<Int?>>()
        for (position in 1..MAX_POSITION) {
            val options = tuning.map { open ->
                listOf<Int?>(null) + (listOf(0) + (position until position + DIAGRAM_FRETS)).filter { (open + it) % 12 in pitchClasses }
            }
            enumerate(options) { frets ->
                if (frets in found) return@enumerate
                val sounding = frets.indices.filter { frets[it] != null }
                if (sounding.size < minimumStrings) return@enumerate
                if (sounding.last() - sounding.first() + 1 != sounding.size) return@enumerate
                val stopped = frets.filterNotNull().filter { it > 0 }
                if (stopped.isNotEmpty() && stopped.max() - stopped.min() >= DIAGRAM_FRETS) return@enumerate
                // An open string rings a long way from a hand high up the neck, which is not a shape anybody reads off a chart.
                if (0 in frets && stopped.isNotEmpty() && stopped.max() > OPEN_POSITION_FRETS) return@enumerate
                val notes = sounding.map { tuning[it] + frets[it]!! }
                if (!notes.map { it % 12 }.toSet().containsAll(required)) return@enumerate
                if (lowest != null && notes.min() % 12 != lowest) return@enumerate
                if (fingerCount(frets) > MAX_FINGERS) return@enumerate
                found += frets
            }
        }
        // A shape that is another one with a string left out is that shape played more quietly, not a variation of its own.
        return found.filter { frets -> found.none { other -> other != frets && frets.indices.all { frets[it] == null || frets[it] == other[it] } } }
            .map { ChordVoicing.Fretted(it) }
            .sortedWith(
                compareBy<ChordVoicing.Fretted>({ position(it.frets) }, { -it.frets.count { fret -> fret != null } }, { stretch(it.frets) })
                    .thenComparator { a, b -> compareFrets(a.frets, b.frets) },
            )
    }

    /**
     * The notes of [chord] a shape may leave out on an instrument of [strings] strings: the fifth of any chord of four
     * notes or more, which says nothing the root does not, and the root as well where there are more notes than strings.
     * The bass of a slash chord is never one of them.
     */
    private fun omissions(chord: Chord, strings: Int): Set<Int> {
        val fifth = ((chord.root + 7) % 12).takeIf { 7 in chord.intervals && chord.intervals.size >= 4 && it != chord.bass }
        val root = chord.root.takeIf { 0 in chord.intervals && chord.pitchClasses.size > strings && it != chord.bass }
        return setOfNotNull(fifth, root)
    }

    private fun compareFrets(a: List<Int?>, b: List<Int?>): Int {
        a.indices.forEach { index ->
            val difference = (a[index] ?: -1) - (b[index] ?: -1)
            if (difference != 0) return difference
        }
        return 0
    }

    private fun enumerate(options: List<List<Int?>>, visit: (List<Int?>) -> Unit) {
        val current = arrayOfNulls<Int>(options.size)
        // 0: no string sounds yet, 1: strings sound, 2: a muted string ended them, so every string after it is muted.
        fun step(string: Int, run: Int) {
            if (string == options.size) {
                visit(current.toList())
                return
            }
            options[string].forEach { fret ->
                val next = when {
                    fret != null && run == 2 -> return@forEach
                    fret != null -> 1
                    run == 1 -> 2
                    else -> run
                }
                current[string] = fret
                step(string + 1, next)
            }
        }
        step(0, 0)
    }

    /** The keyboard shapes of [chord]: its notes from the root up, then each inversion. */
    fun keyboard(chord: Chord): List<ChordVoicing.Keys> {
        val omittable = listOfNotNull(7.takeIf { it in chord.intervals }, 0.takeIf { it in chord.intervals })
        val intervals = chord.intervals - omittable.take((chord.intervals.size - MAX_KEYS).coerceAtLeast(0)).toSet()
        if (intervals.isEmpty()) return emptyList()
        val rootPosition = intervals.map { chord.root + it }
        val bass = chord.bass
        return rootPosition.indices.map { inversion ->
            val notes = (rootPosition.drop(inversion) + rootPosition.take(inversion).map { it + 12 }).let { notes ->
                val octaves = notes.min() / 12
                notes.map { it - octaves * 12 + if (bass != null) 12 else 0 }
            }
            ChordVoicing.Keys(notes.sorted(), bass)
        }
    }

    private const val MAX_POSITION = 12
    private const val MAX_TABLE_POSITION = 9

    /** How many notes a keyboard shape aims at: the fifth and the root are left out above it, never a note the name asks for. */
    private const val MAX_KEYS = 5
}
