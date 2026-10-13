/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.chords

import com.pandulapeter.campfire.chordpro.chords.ChordShapeGeometry.DIAGRAM_FRETS
import com.pandulapeter.campfire.chordpro.chords.ChordShapeGeometry.MAX_FINGERS
import com.pandulapeter.campfire.chordpro.chords.ChordShapeGeometry.OPEN_POSITION_FRETS
import com.pandulapeter.campfire.chordpro.chords.ChordShapeGeometry.fingerCount
import com.pandulapeter.campfire.chordpro.chords.ChordShapeGeometry.position
import com.pandulapeter.campfire.chordpro.chords.ChordShapeGeometry.stretch
import com.pandulapeter.campfire.chordpro.model.Chord
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.chordpro.model.ChordVoicing

/**
 * Where the shapes of [ChordVoicings] come from, without remembering any of them: the tables' shapes of a chord, moved
 * to it from another root where they have no open string, the search for every other shape of a fretted instrument,
 * and the keyboard's inversions. See [ChordVoicings] for the rules each follows.
 *
 * A tuning other than the standard one is answered from the standard one, always with shapes that sound the chord
 * asked for. A shifted tuning's shapes are the standard shapes of the chord the shift is undone on ([lookupChord]). A dropped tuning's are its own seed table's, then the standard
 * table shapes re-fretted on the lowered strings wherever that still makes the chord ([derived]), then the search's on
 * the strings it has.
 */
internal object ChordVoicingSearch {

    /** The tables of [instrument], read once. */
    private fun table(instrument: ChordInstrument) = when (instrument) {
        ChordInstrument.GUITAR -> guitarTable
        ChordInstrument.UKULELE -> ukuleleTable
        ChordInstrument.MANDOLIN -> mandolinTable
        ChordInstrument.KEYBOARD -> emptyList()
    }

    /** The shapes a tuning's charts print that no standard shape is re-fretted into, read once. */
    private fun seedTable(tuning: ChordTuning) = when (tuning.kind) {
        is ChordTuning.Kind.Dropped -> dropTable
        ChordTuning.Kind.Standard, is ChordTuning.Kind.Shifted -> emptyList()
    }

    private class TableShape(val chord: Chord, val voicing: ChordVoicing.Fretted) {
        val isMovable = voicing.frets.none { it == 0 }
    }

    private val guitarTable by lazy { readTable(ChordVoicingTables.guitar, ChordInstrument.GUITAR) }
    private val ukuleleTable by lazy { readTable(ChordVoicingTables.ukulele, ChordInstrument.UKULELE) }
    private val mandolinTable by lazy { readTable(ChordVoicingTables.mandolin, ChordInstrument.MANDOLIN) }
    private val dropTable by lazy { readTable(ChordVoicingTables.drop, ChordInstrument.GUITAR) }

    private fun readTable(lines: List<String>, instrument: ChordInstrument) = lines.map { line ->
        val reading = ChordProDefinitions.read(line, instrument.id) as ChordProDefinitions.Reading.Shape
        check(reading.instrument == instrument) { line }
        TableShape(checkNotNull(ChordProChords.parse(reading.name)) { line }, reading.voicing as ChordVoicing.Fretted)
    }

    /**
     * The tables' shapes of [chord] in [tuning]: the open position shapes filed under it, in the tables' order, and then
     * every shape with no open string, filed under it or moved to it from another root, from the lowest position up. In
     * a dropped tuning the seed table's come first, and the standard tables' follow re-fretted, where they still play it.
     */
    fun tableShapes(chord: Chord, tuning: ChordTuning): List<ChordVoicing.Fretted> {
        val target = playedOn(lookupChord(chord, tuning), tuning.instrument)
        val standard = tableShapesOf(target, table(tuning.instrument))
        return when (tuning.kind) {
            ChordTuning.Kind.Standard, is ChordTuning.Kind.Shifted -> standard
            is ChordTuning.Kind.Dropped -> (tableShapesOf(target, seedTable(tuning)) + derived(standard, target, tuning)).distinctBy { it.frets }
        }
    }

    private fun tableShapesOf(target: Chord, shapes: List<TableShape>): List<ChordVoicing.Fretted> {
        val open = shapes.filter { !it.isMovable && it.chord == target }.map { it.voicing }
        val movable = shapes.filter { it.isMovable && it.chord.intervals == target.intervals && it.chord.bassInterval == target.bassInterval }
            .flatMap { shape -> shiftsOf(shape.voicing.frets, (target.root - shape.chord.root).mod(12)).map { shape.voicing.shiftedBy(it) } }
            .sortedWith(compareBy({ position(it.frets) }, { -it.frets.count { fret -> fret != null } }))
        return (open + movable).distinctBy { it.frets }
    }

    private val Chord.bassInterval get() = bass?.let { (it - root).mod(12) }

    /** See [ChordInstrument.playsChordOver]. */
    private fun playedOn(chord: Chord, instrument: ChordInstrument) = if (instrument.playsChordOver) chord.copy(bass = null) else chord

    /**
     * The chord whose shapes [tuning] draws [chord] with, in the terms of its unshifted strings ([lookupStrings]): the
     * chord moved back by the shift, so that the shape sounds [chord] on the strings the player has. A half step down an
     * A is the standard B♭ shape, and a baritone ukulele's G the soprano's C shape.
     */
    private fun lookupChord(chord: Chord, tuning: ChordTuning) = if (tuning.shift == 0) {
        chord
    } else {
        chord.copy(root = (chord.root - tuning.shift).mod(12), bass = chord.bass?.let { (it - tuning.shift).mod(12) })
    }

    /**
     * The strings [lookupChord]'s shapes are on: the standard ones for a shifted tuning, whose shapes are the standard
     * ones, and a dropped tuning's own moved back by its shift (drop C♯'s are drop D's), whose dropped strings are not.
     */
    private fun lookupStrings(tuning: ChordTuning) = when (tuning.kind) {
        ChordTuning.Kind.Standard, is ChordTuning.Kind.Shifted -> tuning.instrument.tuning
        is ChordTuning.Kind.Dropped -> tuning.strings.map { it - tuning.shift }
    }

    /**
     * The moves by [interval] and its octaves that keep a shape with no open string between the first and the ninth
     * fret. Higher up a shape is still found by the search, but it is no longer one a chart would show for the chord.
     */
    private fun shiftsOf(frets: List<Int?>, interval: Int): List<Int> {
        val lowest = frets.filterNotNull().min()
        return (-1..1).map { interval + it * 12 }.filter { shift -> lowest + shift in 1..MAX_TABLE_POSITION }
    }

    private fun ChordVoicing.Fretted.shiftedBy(frets: Int) = if (frets == 0) this else copy(frets = this.frets.map { it?.plus(frets) })

    /**
     * The standard table [shapes] of [target] as shapes of a dropped [tuning]: every lowered string stopped as many frets
     * higher as it was lowered, kept where the shape still keeps the search's rules and can be held, and otherwise tried
     * again with the lowered strings that sound muted. Drop D's G `3 2 0 0 0 3` is `5 2 0 0 0 3`; its G7 `3 2 0 0 0 1`
     * spans five frets re-fretted and sounds no G muted, so the search has to find one.
     *
     * The fingering is kept only where no finger had to move: the lowered strings are muted, so a finger that stopped one
     * is lifted and every other one stays. A finger on a re-fretted string cannot keep its number, since the barre an F
     * shape lays across the sixth string cannot be at the first and the third fret at once.
     */
    private fun derived(shapes: List<ChordVoicing.Fretted>, target: Chord, tuning: ChordTuning): List<ChordVoicing.Fretted> {
        val strings = lookupStrings(tuning)
        val lowered = strings.indices.associateWith { tuning.instrument.tuning[it] - strings[it] }.filterValues { it > 0 }
        val rules = Rules(target, strings, tuning.instrument.playsChordOver)
        return shapes.mapNotNull { shape ->
            val refretted = shape.frets.mapIndexed { string, fret -> fret?.plus(lowered[string] ?: 0) }
            val muted = shape.frets.mapIndexed { string, fret -> if (string in lowered) null else fret }
            listOf(refretted, muted).distinct().firstOrNull { frets ->
                // The tables mute a string a finger rests on between two that sound, which holds only while that finger stays.
                rules.allows(frets, checksGaps = frets != shape.frets) && ChordShapeGeometry.isHoldable(frets)
            }?.let { frets ->
                val fingers = shape.fingers?.takeIf { lowered.keys.all { frets[it] == null } }?.mapIndexed { string, finger -> if (frets[string] == null) 0 else finger }
                ChordVoicing.Fretted(frets, fingers)
            }
        }
    }

    /** Every shape the search finds for [chord] in a fretted [tuning], the lowest position first. */
    fun search(chord: Chord, tuning: ChordTuning): List<ChordVoicing.Fretted> {
        val played = playedOn(lookupChord(chord, tuning), tuning.instrument)
        val strings = lookupStrings(tuning)
        val rules = Rules(played, strings, tuning.instrument.playsChordOver)
        // Every string sounds one note, so a chord that needs more notes than there are strings has no shape at all.
        if (rules.required.size > strings.size) return emptyList()
        val found = mutableSetOf<List<Int?>>()
        for (position in 1..MAX_POSITION) {
            val options = strings.map { open ->
                listOf<Int?>(null) + (listOf(0) + (position until position + DIAGRAM_FRETS)).filter { (open + it) % 12 in rules.pitchClasses }
            }
            enumerate(options) { frets ->
                if (frets !in found && rules.allows(frets, checksGaps = true)) found += frets
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

    /** The search's rules for a shape of [played] on [strings], see [ChordVoicings]. */
    private class Rules(played: Chord, private val strings: List<Int>, playsChordOver: Boolean) {
        val pitchClasses = played.pitchClasses
        val required = pitchClasses - omissions(played, strings.size)
        private val lowest = if (playsChordOver) null else played.bass ?: played.root.takeIf { it in required }
        private val minimumStrings = if (playsChordOver) strings.size else if (pitchClasses.size <= 2) 3 else 4

        /** Whether [frets] keeps the rules, the one against a muted string between two that sound only with [checksGaps]. */
        fun allows(frets: List<Int?>, checksGaps: Boolean): Boolean {
            val sounding = frets.indices.filter { frets[it] != null }
            if (sounding.size < minimumStrings) return false
            if (checksGaps && sounding.last() - sounding.first() + 1 != sounding.size) return false
            val stopped = frets.filterNotNull().filter { it > 0 }
            if (stopped.isNotEmpty() && stopped.max() - stopped.min() >= DIAGRAM_FRETS) return false
            // An open string rings a long way from a hand high up the neck, which is not a shape anybody reads off a chart.
            if (0 in frets && stopped.isNotEmpty() && stopped.max() > OPEN_POSITION_FRETS) return false
            val notes = sounding.map { strings[it] + frets[it]!! }
            if (!notes.map { it % 12 }.toSet().let { sounded -> sounded.containsAll(required) && pitchClasses.containsAll(sounded) }) return false
            if (lowest != null && notes.min() % 12 != lowest) return false
            return fingerCount(frets) <= MAX_FINGERS
        }
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
