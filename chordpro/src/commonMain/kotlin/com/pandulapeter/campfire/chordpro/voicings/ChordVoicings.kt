/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.voicings

import com.pandulapeter.campfire.chordpro.voicings.ChordVoicingSearch.hasTableShapes
import com.pandulapeter.campfire.chordpro.voicings.ChordVoicingSearch.keyboard
import com.pandulapeter.campfire.chordpro.voicings.ChordVoicingSearch.search
import com.pandulapeter.campfire.chordpro.voicings.ChordVoicingSearch.tableShape
import com.pandulapeter.campfire.chordpro.voicings.ChordVoicingSearch.tableShapes
import com.pandulapeter.campfire.chordpro.model.Chord
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.chordpro.model.ChordVoicing
import kotlin.concurrent.Volatile

/**
 * How a chord is played: the shapes of a [Chord] on an instrument in one of its tunings ([ChordTuning]).
 *
 * The shapes come from two places, each for what it is good at. The tables ([ChordVoicingTables]) hold the shapes
 * players use, most with their fingerings, in the order they are offered in, and always come first, since a search
 * cannot know which of a dozen C chords a guitarist expects to see. A search finds the rest: every way to stop the strings within four frets, at each
 * position up to the twelfth, that sounds only notes of the chord and all of the ones that make it that chord (the
 * fifth may go first, then the root, where there are more notes than strings), with the bass, or else the root, as the
 * lowest note that sounds (not on an instrument that plays the chord over its strings, [ChordInstrument.playsChordOver]:
 * the re-entrant ukulele, the mandolin, and the banjo), no muted string between two that sound, and no more than four
 * fingers, a barre counting as one. That is complete for its own rules,
 * which is all that "every shape" can honestly mean: what a hand can do is a matter of hands, and the shape a song
 * wants is often no voicing of its chord's name at all, which is what a `{define}` is for.
 *
 * The keyboard needs neither: the chord's notes from the root up, the fifth and then the root left out of a chord of
 * more than five notes, and the inversions as the variations, a slash chord's bass an octave below.
 *
 * In a tuning other than the standard one every shape is a shape on the strings the player has, and sounds the chord it
 * is drawn for: a tuning with a table of its own (drop D, double drop D, and the open and modal tunings) reads it, and
 * one that is such a tuning with every string moved by one interval reads that table for the chord moved back by it (a
 * half step down an A is the standard B♭ shape, on a baritone ukulele a G is the soprano's C shape). The search runs on
 * the tuning's own strings after the table; in an open or modal tuning it lets the chord's fifth ring lowest from an
 * open string, as the tables there do.
 */
public object ChordVoicings {

    /** The defaults worked out so far, replaced whole rather than changed, so threads reading it never see it half written. */
    @Volatile
    private var defaults: Map<Pair<Chord, ChordTuning>, ChordVoicing?> = emptyMap()

    /**
     * The shape [chord] is shown with in [tuning], or null where there is none. A table lookup wherever the tables
     * have the chord, and the search otherwise, whose answer is remembered for the rest of the session: the first page
     * that names an unusual chord pays for it once.
     */
    public fun default(chord: Chord, tuning: ChordTuning): ChordVoicing? {
        val key = chord to tuning
        val cached = defaults
        if (key in cached) return cached[key]
        val shape = when (tuning.instrument) {
            ChordInstrument.KEYBOARD -> keyboard(chord).firstOrNull()
            else -> tableShapes(chord, tuning).firstOrNull() ?: search(chord, tuning).firstOrNull()
        }
        // A song plays a few dozen chords; starting over when the cache is full keeps it bounded without bookkeeping.
        defaults = (if (cached.size >= MAX_CACHED_DEFAULTS) emptyMap() else cached) + (key to shape)
        return shape
    }

    /**
     * Reads and indexes the table [tuning] uses, so that its first lookup costs nothing: reading one is thousands of
     * shapes, more than a frame on a slow device, and the first page of a song is built in one. Safe to call from any
     * thread and as often as wanted; a lookup made while it runs waits for it rather than reading the table a second
     * time, and nothing any function here answers changes.
     */
    public fun prepare(tuning: ChordTuning) {
        ChordVoicingSearch.prepare(tuning)
    }

    /** Forgets every default worked out so far, so that a test asking [needsSearch] does not depend on the ones before it. */
    internal fun resetCache() {
        defaults = emptyMap()
    }

    /**
     * Whether [default] would have to run the search for [chord] in [tuning]: a fretted chord the tables do not hold,
     * not even as a shape moved from another root, and nothing has looked for yet this session. What builds a page in a
     * frame asks this first.
     */
    public fun needsSearch(chord: Chord, tuning: ChordTuning): Boolean =
        tuning.instrument != ChordInstrument.KEYBOARD && (chord to tuning) !in defaults && !hasTableShapes(chord, tuning)

    /** Every shape of [chord] in [tuning], [default] first: the tables', then the search's. */
    public fun all(chord: Chord, tuning: ChordTuning): List<ChordVoicing> = when (tuning.instrument) {
        ChordInstrument.KEYBOARD -> keyboard(chord)
        else -> {
            val table = tableShapes(chord, tuning)
            val known = table.map { it.frets }.toSet()
            table + search(chord, tuning).filter { it.frets !in known }
        }
    }

    /**
     * A shape [write] wrote, read back for [tuning], or null where it is no shape of its instrument. With [chord], a
     * fretted shape the tables know for it in that tuning gets their fingering back, which a stored shape does not keep.
     */
    public fun read(shape: String, tuning: ChordTuning, chord: Chord? = null): ChordVoicing? {
        val words = shape.trim().split(' ').filter { it.isNotEmpty() }
        if (tuning.instrument == ChordInstrument.KEYBOARD) {
            val separator = words.indexOf(BASS_SEPARATOR)
            val notes = (if (separator < 0) words else words.subList(0, separator)).map { it.toIntOrNull()?.takeIf { note -> note in 0..MAX_KEY } ?: return null }
            val bass = if (separator < 0) null else words.getOrNull(separator + 1)?.toIntOrNull()?.takeIf { it in 0 until 12 } ?: return null
            if (notes.isEmpty() || separator >= 0 && words.size != separator + 2) return null
            return ChordVoicing.Keys(notes.distinct().sorted(), bass)
        }
        if (words.size != tuning.strings.size) return null
        val frets = words.map { word -> if (word == MUTED) null else word.toIntOrNull()?.takeIf { it in 0..MAX_FRET } ?: return null }
        if ((tuning.instrument.droneString?.let { frets[it] } ?: 0) > 0) return null
        return chord?.let { tableShape(it, tuning, frets) } ?: ChordVoicing.Fretted(frets)
    }

    /** [voicing] as one string, which is how a player's choice of shape is stored: `x 3 2 0 1 0`, `4 7 12 / 0`. */
    public fun write(voicing: ChordVoicing): String = when (voicing) {
        is ChordVoicing.Fretted -> voicing.frets.joinToString(" ") { it?.toString() ?: MUTED }
        is ChordVoicing.Keys -> voicing.notes.joinToString(" ") + (voicing.bass?.let { " $BASS_SEPARATOR $it" } ?: "")
    }

    /**
     * The fret a diagram of [frets] starts at: the nut where the shape fits into the first four frets, and otherwise
     * its lowest stopped fret, see [ChordShapeGeometry.baseFret].
     */
    public fun baseFret(frets: List<Int?>): Int = ChordShapeGeometry.baseFret(frets)

    /** Whether a hand can hold [frets], see [ChordShapeGeometry.isHoldable]. */
    public fun isHoldable(frets: List<Int?>): Boolean = ChordShapeGeometry.isHoldable(frets)

    private const val MUTED = "x"
    private const val BASS_SEPARATOR = "/"

    /** The last fret of the neck, on every fretted instrument and in a tab. */
    internal const val MAX_FRET = 24

    /** The highest key a keyboard shape can press: four octaves above the diagram's C, past anything [ChordVoicingSearch.keyboard] writes. */
    internal const val MAX_KEY = 47
    private const val MAX_CACHED_DEFAULTS = 256
}
