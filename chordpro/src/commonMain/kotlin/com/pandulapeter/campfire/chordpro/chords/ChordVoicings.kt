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

import com.pandulapeter.campfire.chordpro.chords.ChordVoicingSearch.keyboard
import com.pandulapeter.campfire.chordpro.chords.ChordVoicingSearch.search
import com.pandulapeter.campfire.chordpro.chords.ChordVoicingSearch.tableShapes
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
public object ChordVoicings {

    /** The defaults worked out so far, replaced whole rather than changed, so threads reading it never see it half written. */
    @Volatile
    private var defaults: Map<Pair<Chord, ChordInstrument>, ChordVoicing?> = emptyMap()

    /**
     * The shape [chord] is shown with on [instrument], or null where there is none. A table lookup wherever the tables
     * have the chord, and the search otherwise, whose answer is remembered for the rest of the session: the first page
     * that names an unusual chord pays for it once.
     */
    public fun default(chord: Chord, instrument: ChordInstrument): ChordVoicing? {
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

    /** Forgets every default worked out so far, so that a test asking [needsSearch] does not depend on the ones before it. */
    internal fun resetCache() {
        defaults = emptyMap()
    }

    /**
     * Whether [default] would have to run the search for [chord] on [instrument]: a fretted chord the tables do not
     * hold and nothing has looked for yet this session. What builds a page in a frame asks this first.
     */
    public fun needsSearch(chord: Chord, instrument: ChordInstrument): Boolean =
        instrument != ChordInstrument.KEYBOARD && (chord to instrument) !in defaults && tableShapes(chord, instrument).isEmpty()

    /** Every shape of [chord] on [instrument], [default] first: the tables', then the search's. */
    public fun all(chord: Chord, instrument: ChordInstrument): List<ChordVoicing> = when (instrument) {
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
    public fun read(shape: String, instrument: ChordInstrument, chord: Chord? = null): ChordVoicing? {
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
