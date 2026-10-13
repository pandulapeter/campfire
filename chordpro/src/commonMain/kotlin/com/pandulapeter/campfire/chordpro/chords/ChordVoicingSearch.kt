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
 * Every tuning reads one table: its own, or that of the tuning it is with every string moved by one interval
 * ([ChordTuning.tableTuning]), for the chord moved back by that interval, so that the shape sounds the chord asked for
 * on the strings the player has: a half step down an A is the standard B♭ shape. The search runs on those strings.
 */
internal object ChordVoicingSearch {

    /** The table of [tuning]'s [ChordTuning.tableTuning], read once. */
    private fun table(tuning: ChordTuning) = when (tuning.tableTuning) {
        ChordTuning.GUITAR -> guitarTable
        ChordTuning.GUITAR_DROP_D -> dropDTable
        ChordTuning.GUITAR_DOUBLE_DROP_D -> doubleDropDTable
        ChordTuning.GUITAR_OPEN_G -> openGTable
        ChordTuning.GUITAR_OPEN_D -> openDTable
        ChordTuning.GUITAR_OPEN_C -> openCTable
        ChordTuning.GUITAR_DADGAD -> dadgadTable
        ChordTuning.UKULELE -> ukuleleTable
        ChordTuning.MANDOLIN -> mandolinTable
        ChordTuning.MANDOLIN_GDGD -> mandolinGdgdTable
        ChordTuning.BANJO -> banjoTable
        ChordTuning.BANJO_DOUBLE_C -> banjoDoubleCTable
        ChordTuning.BANJO_C -> banjoCTable
        ChordTuning.BANJO_OPEN_D -> banjoOpenDTable
        ChordTuning.BANJO_G_MODAL -> banjoGModalTable
        else -> Table.EMPTY
    }

    private class TableShape(val chord: Chord, val voicing: ChordVoicing.Fretted)

    /**
     * A table read and indexed once: thousands of shapes are looked up for every chord of a page while it is built in a
     * frame, so neither lookup walks the whole of it.
     *
     * @property filed The shapes filed under each chord, in the table's order.
     * @property movable The shapes with no open string, by their quality (the intervals and the bass's interval), which
     *   stand for that quality on every root.
     */
    private class Table(
        val filed: Map<Chord, List<ChordVoicing.Fretted>>,
        val movable: Map<Pair<List<Int>, Int?>, List<TableShape>>,
    ) {
        companion object {
            val EMPTY = Table(emptyMap(), emptyMap())
        }
    }

    private val guitarTable by lazy { readTable(ChordVoicingTables.guitar, ChordTuning.GUITAR) }
    private val dropDTable by lazy { readTable(ChordVoicingTables.dropD, ChordTuning.GUITAR_DROP_D) }
    private val doubleDropDTable by lazy { readTable(ChordVoicingTables.doubleDropD, ChordTuning.GUITAR_DOUBLE_DROP_D) }
    private val openGTable by lazy { readTable(ChordVoicingTables.openG, ChordTuning.GUITAR_OPEN_G) }
    private val openDTable by lazy { readTable(ChordVoicingTables.openD, ChordTuning.GUITAR_OPEN_D) }
    private val openCTable by lazy { readTable(ChordVoicingTables.openC, ChordTuning.GUITAR_OPEN_C) }
    private val dadgadTable by lazy { readTable(ChordVoicingTables.dadgad, ChordTuning.GUITAR_DADGAD) }
    private val ukuleleTable by lazy { readTable(ChordVoicingTables.ukulele, ChordTuning.UKULELE) }
    private val mandolinTable by lazy { readTable(ChordVoicingTables.mandolin, ChordTuning.MANDOLIN) }
    private val mandolinGdgdTable by lazy { readTable(ChordVoicingTables.mandolinGdgd, ChordTuning.MANDOLIN_GDGD) }
    private val banjoTable by lazy { readTable(ChordVoicingTables.banjo, ChordTuning.BANJO) }
    private val banjoDoubleCTable by lazy { readTable(ChordVoicingTables.banjoDoubleC, ChordTuning.BANJO_DOUBLE_C) }
    private val banjoCTable by lazy { readTable(ChordVoicingTables.banjoC, ChordTuning.BANJO_C) }
    private val banjoOpenDTable by lazy { readTable(ChordVoicingTables.banjoOpenD, ChordTuning.BANJO_OPEN_D) }
    private val banjoGModalTable by lazy { readTable(ChordVoicingTables.banjoGModal, ChordTuning.BANJO_G_MODAL) }

    private fun readTable(lines: List<String>, tuning: ChordTuning): Table {
        val instrument = tuning.instrument
        val shapes = lines.map { line ->
            val reading = ChordProDefinitions.read(line, instrument.id) as ChordProDefinitions.Reading.Shape
            check(reading.instrument == instrument) { line }
            TableShape(checkNotNull(ChordProChords.parse(reading.name)) { line }, reading.voicing as ChordVoicing.Fretted)
        }
        return Table(
            filed = shapes.groupBy({ it.chord }, { it.voicing }),
            movable = shapes.filter { isMovable(it.voicing, tuning) }.groupBy { it.chord.quality },
        )
    }

    /**
     * Whether [voicing] stands for its quality on every root: it has no open string, a drone string aside, and where the
     * drone sounds, the strings that move with the shape sound its note too, since the drone stays where it is.
     */
    private fun isMovable(voicing: ChordVoicing.Fretted, tuning: ChordTuning): Boolean {
        val drone = tuning.instrument.droneString
        if (voicing.frets.withIndex().any { it.value == 0 && it.index != drone }) return false
        if (drone == null || voicing.frets[drone] == null) return true
        val withoutDrone = voicing.copy(frets = voicing.frets.mapIndexed { string, fret -> fret.takeIf { string != drone } })
        return ChordShapeGeometry.pitchClasses(withoutDrone, tuning.strings) == ChordShapeGeometry.pitchClasses(voicing, tuning.strings)
    }

    /**
     * The table's shapes of [chord] in [tuning]: every shape filed under it, in the table's order, and then every shape
     * with no open string filed under another root of its quality, moved to it, from the lowest position up. A drone
     * string stays where it is on a moved shape, open where its note is one of the chord's and muted otherwise.
     */
    fun tableShapes(chord: Chord, tuning: ChordTuning): List<ChordVoicing.Fretted> {
        val target = playedOn(chord.movedBy(-tuning.shift), tuning.instrument)
        val table = table(tuning)
        val filed = table.filed[target].orEmpty()
        val drone = tuning.instrument.droneString
        val droneFret = drone?.let { if (tuning.tableTuning.strings[it] % 12 in target.pitchClasses) 0 else null }
        val moved = table.movable[target.quality].orEmpty().filter { it.chord != target }
            .flatMap { shape ->
                shiftsOf(shape.voicing.frets, (target.root - shape.chord.root).mod(12), drone).map { shape.voicing.shiftedBy(it, drone, droneFret) }
            }
            .sortedWith(compareBy({ position(it.frets) }, { -it.frets.count { fret -> fret != null } }))
        return (filed + moved).distinctBy { it.frets }
    }

    /** Whether [tableShapes] has any shape of [chord] in [tuning], without working all of them out. */
    fun hasTableShapes(chord: Chord, tuning: ChordTuning): Boolean {
        val target = playedOn(chord.movedBy(-tuning.shift), tuning.instrument)
        val table = table(tuning)
        return target in table.filed ||
            table.movable[target.quality].orEmpty().any {
                shiftsOf(it.voicing.frets, (target.root - it.chord.root).mod(12), tuning.instrument.droneString).isNotEmpty()
            }
    }

    private val Chord.quality get() = intervals to bassInterval

    private val Chord.bassInterval get() = bass?.let { (it - root).mod(12) }

    private fun Chord.movedBy(semitones: Int) = if (semitones.mod(12) == 0) {
        this
    } else {
        copy(root = (root + semitones).mod(12), bass = bass?.let { (it + semitones).mod(12) })
    }

    /** See [ChordInstrument.playsChordOver]. */
    private fun playedOn(chord: Chord, instrument: ChordInstrument) = if (instrument.playsChordOver) chord.copy(bass = null) else chord

    /**
     * The moves by [interval] and its octaves that keep a shape with no open string between the first and the ninth
     * fret. Higher up a shape is still found by the search, but it is no longer one a chart would show for the chord.
     * The [drone] string does not move, so it counts for nothing.
     */
    private fun shiftsOf(frets: List<Int?>, interval: Int, drone: Int?): List<Int> {
        val lowest = frets.filterIndexed { string, _ -> string != drone }.filterNotNull().min()
        return (-1..1).map { interval + it * 12 }.filter { shift -> lowest + shift in 1..MAX_TABLE_POSITION }
    }

    private fun ChordVoicing.Fretted.shiftedBy(frets: Int, drone: Int?, droneFret: Int?) = if (frets == 0) {
        this
    } else {
        copy(
            frets = this.frets.mapIndexed { string, fret -> if (string == drone) droneFret else fret?.plus(frets) },
            fingers = fingers?.mapIndexed { string, finger -> if (string == drone) 0 else finger },
        )
    }

    /** Every shape the search finds for [chord] on a fretted [tuning]'s strings, the lowest position first. */
    fun search(chord: Chord, tuning: ChordTuning): List<ChordVoicing.Fretted> {
        val played = playedOn(chord, tuning.instrument)
        val strings = tuning.strings
        val pitchClasses = played.pitchClasses
        val required = pitchClasses - omissions(played, strings.size)
        // Every string sounds one note, so a chord that needs more notes than there are strings has no shape at all.
        if (required.size > strings.size) return emptyList()
        val lowest = if (tuning.instrument.playsChordOver) null else played.bass ?: played.root.takeIf { it in required }
        // In an open tuning the open strings drone under the chord, so its fifth may ring lowest from one of them.
        val openFifth = ((played.root + 7) % 12).takeIf { tuning.isOpen && played.bass == null && 7 in played.intervals }
        // A drone string only ever rings open, so it sounds where its note is the chord's and is muted otherwise.
        val drone = tuning.instrument.droneString
        val minimumStrings = when {
            tuning.instrument.playsChordOver -> strings.size - if (drone == null) 0 else 1
            pitchClasses.size <= 2 -> 3
            else -> 4
        }
        val found = mutableSetOf<List<Int?>>()
        for (position in 1..MAX_POSITION) {
            val options = strings.mapIndexed { string, open ->
                val frets = if (string == drone) listOf(0) else listOf(0) + (position until position + DIAGRAM_FRETS)
                listOf<Int?>(null) + frets.filter { (open + it) % 12 in pitchClasses }
            }
            enumerate(options) { frets ->
                if (frets in found) return@enumerate
                val sounding = frets.indices.filter { frets[it] != null }
                if (sounding.size < minimumStrings) return@enumerate
                if (sounding.last() - sounding.first() + 1 != sounding.size) return@enumerate
                val stopped = frets.filterNotNull().filter { it > 0 }
                if (stopped.isNotEmpty() && stopped.max() - stopped.min() >= DIAGRAM_FRETS) return@enumerate
                // An open string rings a long way from a hand high up the neck, which is not a shape anybody reads off a chart;
                // a drone string is the exception, since it starts at the fifth fret and rings under every position.
                val hasOpenString = frets.withIndex().any { it.value == 0 && it.index != drone }
                if (hasOpenString && stopped.isNotEmpty() && stopped.max() > OPEN_POSITION_FRETS) return@enumerate
                val notes = sounding.map { strings[it] + frets[it]!! }
                if (!notes.map { it % 12 }.toSet().containsAll(required)) return@enumerate
                if (lowest != null && notes.min() % 12 != lowest) {
                    val bottom = sounding[notes.indexOf(notes.min())]
                    if (notes.min() % 12 != openFifth || frets[bottom] != 0) return@enumerate
                }
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
