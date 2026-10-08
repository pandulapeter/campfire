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

import com.pandulapeter.campfire.chordpro.model.Chord
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordVoicing
import kotlin.concurrent.Volatile

/**
 * How a chord is played: the shapes of a [Chord] on an instrument.
 *
 * The shapes come from two places, each for what it is good at. The tables ([ChordVoicingTables]) hold the shapes
 * everybody knows, with their fingerings, and always come first, since a search cannot know which of a dozen C chords
 * a guitarist expects to see. A search finds the rest: every way to stop the strings within four frets, at each
 * position up to the twelfth, that sounds only notes of the chord and all of the ones that make it that chord (the
 * fifth may go first, then the root, where there are more notes than strings), with the bass, or else the root, as the
 * lowest note that sounds (not on the re-entrant ukulele, whose lowest string is in the middle), no muted string
 * between two that sound, and no more than four fingers, a barre counting as one. That is complete for its own rules,
 * which is all that "every shape" can honestly mean: what a hand can do is a matter of hands, and the shape a song
 * wants is often no voicing of its chord's name at all, which is what a `{define}` is for.
 *
 * The keyboard needs neither: the chord's notes from the root up, the fifth and then the root left out of a chord of
 * more than five notes, and the inversions as the variations, a slash chord's bass an octave below.
 */
object ChordVoicings {

    /** The defaults worked out so far, replaced whole rather than changed, so threads reading it never see it half written. */
    @Volatile
    private var defaults: Map<Pair<Chord, ChordInstrument>, ChordVoicing?> = emptyMap()

    /**
     * The shape [chord] is shown with on [instrument], or null where there is none. A table lookup wherever the tables
     * have the chord, and the search otherwise, whose answer is remembered for the rest of the session: the first page
     * that names an unusual chord pays for it once.
     */
    fun default(chord: Chord, instrument: ChordInstrument): ChordVoicing? {
        val key = chord to instrument
        val cached = defaults
        if (key in cached) return cached[key]
        val shape = when (instrument) {
            ChordInstrument.KEYBOARD -> keyboard(chord).firstOrNull()
            else -> tableShapes(chord, instrument).firstOrNull() ?: search(chord, instrument).firstOrNull()
        }
        // A song plays a few dozen chords; starting over when the cache is full keeps it bounded without bookkeeping.
        defaults = (if (cached.size >= MAX_CACHED_DEFAULTS) emptyMap() else cached) + (key to shape)
        return shape
    }

    /**
     * Whether [default] would have to run the search for [chord] on [instrument]: a fretted chord the tables do not
     * hold and nothing has looked for yet this session. What builds a page in a frame asks this first.
     */
    fun needsSearch(chord: Chord, instrument: ChordInstrument): Boolean =
        instrument != ChordInstrument.KEYBOARD && (chord to instrument) !in defaults && tableShapes(chord, instrument).isEmpty()

    /** Every shape of [chord] on [instrument], [default] first: the tables', then the search's. */
    fun all(chord: Chord, instrument: ChordInstrument): List<ChordVoicing> = when (instrument) {
        ChordInstrument.KEYBOARD -> keyboard(chord)
        else -> {
            val table = tableShapes(chord, instrument)
            val known = table.map { it.frets }.toSet()
            table + search(chord, instrument).filter { it.frets !in known }
        }
    }

    /**
     * A shape [write] wrote, read back for [instrument], or null where it is no shape of that instrument. With
     * [chord], a fretted shape the tables know for it gets their fingering back, which a stored shape does not keep.
     */
    fun read(shape: String, instrument: ChordInstrument, chord: Chord? = null): ChordVoicing? {
        val words = shape.trim().split(' ').filter { it.isNotEmpty() }
        if (instrument == ChordInstrument.KEYBOARD) {
            val separator = words.indexOf(BASS_SEPARATOR)
            val notes = (if (separator < 0) words else words.subList(0, separator)).map { it.toIntOrNull()?.takeIf { note -> note in 0..MAX_KEY } ?: return null }
            val bass = if (separator < 0) null else words.getOrNull(separator + 1)?.toIntOrNull()?.takeIf { it in 0 until 12 } ?: return null
            if (notes.isEmpty() || separator >= 0 && words.size != separator + 2) return null
            return ChordVoicing.Keys(notes.distinct().sorted(), bass)
        }
        if (words.size != instrument.tuning.size) return null
        val frets = words.map { word -> if (word == MUTED) null else word.toIntOrNull()?.takeIf { it in 0..MAX_FRET } ?: return null }
        return chord?.let { tableShapes(it, instrument).firstOrNull { known -> known.frets == frets } } ?: ChordVoicing.Fretted(frets)
    }

    /** [voicing] as one string, which is how a player's choice of shape is stored: `x 3 2 0 1 0`, `4 7 12 / 0`. */
    fun write(voicing: ChordVoicing) = when (voicing) {
        is ChordVoicing.Fretted -> voicing.frets.joinToString(" ") { it?.toString() ?: MUTED }
        is ChordVoicing.Keys -> voicing.notes.joinToString(" ") + (voicing.bass?.let { " $BASS_SEPARATOR $it" } ?: "")
    }

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
    internal fun fingerCount(frets: List<Int?>): Int {
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
    internal fun pitchClasses(voicing: ChordVoicing, instrument: ChordInstrument): Set<Int> = when (voicing) {
        is ChordVoicing.Fretted -> voicing.frets.mapIndexedNotNull { string, fret -> fret?.let { (instrument.tuning[string] + it) % 12 } }.toSet()
        is ChordVoicing.Keys -> (voicing.notes + listOfNotNull(voicing.bass)).map { it % 12 }.toSet()
    }

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
    private fun tableShapes(chord: Chord, instrument: ChordInstrument): List<ChordVoicing.Fretted> {
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

    private fun position(frets: List<Int?>) = frets.filterNotNull().filter { it > 0 }.minOrNull() ?: 0

    private fun search(chord: Chord, instrument: ChordInstrument): List<ChordVoicing.Fretted> {
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

    private fun stretch(frets: List<Int?>) = frets.filterNotNull().filter { it > 0 }.let { if (it.isEmpty()) 0 else it.max() - it.min() }

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

    private fun keyboard(chord: Chord): List<ChordVoicing.Keys> {
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

    private const val MUTED = "x"
    private const val BASS_SEPARATOR = "/"
    private const val DIAGRAM_FRETS = 4
    private const val OPEN_POSITION_FRETS = 5

    /**
     * The fingers a fretting hand holds a shape with, the thumb not counted: the most a shape may need, and the highest
     * finger number a moved definition keeps.
     */
    internal const val MAX_FINGERS = 4

    private const val MAX_POSITION = 12
    private const val MAX_TABLE_POSITION = 9
    private const val MAX_HOLDABLE_FRET = 15

    /** The last fret of the neck, on every fretted instrument and in a tab. */
    internal const val MAX_FRET = 24

    /** How many notes a keyboard shape aims at: the fifth and the root are left out above it, never a note the name asks for. */
    private const val MAX_KEYS = 5

    /** The highest key a keyboard shape can press: four octaves above the diagram's C, past anything [keyboard] writes. */
    internal const val MAX_KEY = 47
    private const val MAX_CACHED_DEFAULTS = 256
}
