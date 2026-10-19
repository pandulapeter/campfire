/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.scales

import com.pandulapeter.campfire.chordpro.chords.ChordProNashville
import com.pandulapeter.campfire.chordpro.chords.ChordProChords
import com.pandulapeter.campfire.chordpro.model.Chord

/**
 * The two ways between scales and chords: the chords a scale's notes stack into, and the scales a chord can be played
 * over.
 */
public object ScaleChords {

    /**
     * The triads of [scale] played on [rootName], one per degree whose stack of thirds within the scale is a chord with a
     * name ([triadSuffixes]), for a seven-note scale only: a scale of another size has no thirds to stack. Each name is
     * the degree's note as [ScaleSpelling] spells it and the chord's suffix, in the standard notation, so that
     * [ChordProChords.parse] reads it, and each numeral stands on the degree as the scale's formula writes it (`bV` on a
     * Locrian scale's `b5`), not on the step its semitones would make it.
     */
    public fun triads(scale: Scale, rootName: String): List<ScaleChord> = stacks(scale, rootName, depth = 3, triadSuffixes)

    /** The seventh chords of [scale] played on [rootName], as [triads] lists its triads. */
    public fun sevenths(scale: Scale, rootName: String): List<ScaleChord> = stacks(scale, rootName, depth = 4, seventhSuffixes)

    /**
     * The scales built on [chord]'s root that hold every one of its notes, [Scales.common] first in that order and then
     * the rest in catalogue order: what can be played over it. The bass of a slash chord is left out, since it is a
     * voicing rather than a tone of the chord, and so is the chromatic scale, which holds every chord. A scale on another
     * root that holds the notes too (a C triad is in F major) is no answer to a player asking what to play over a C.
     */
    public fun scalesContaining(chord: Chord): List<Scale> = scalesContaining(chord.root, chord.intervals.map { (chord.root + it) % 12 }.toSet())

    /**
     * The scales built on [root] that hold every one of [pitchClasses], ordered as [scalesContaining] orders a chord's:
     * what a whole song can be played over, [root] being its key and [pitchClasses] the notes of all of its chords.
     */
    public fun scalesContaining(root: Int, pitchClasses: Set<Int>): List<Scale> {
        val intervals = pitchClasses.map { (it - root).mod(12) }
        val fits = Scales.all.filter { scale -> scale.id != Scales.CHROMATIC_ID && scale.intervals.containsAll(intervals) }
        return Scales.common.filter { it in fits } + fits.filter { it !in Scales.common }
    }

    private fun stacks(scale: Scale, rootName: String, depth: Int, suffixes: Map<List<Int>, String>): List<ScaleChord> {
        if (scale.intervals.size != SEVEN) return emptyList()
        val root = ChordProChords.pitchClass(rootName) ?: return emptyList()
        return scale.intervals.indices.mapNotNull { degree ->
            val intervals = (0 until depth).map { (scale.intervals[(degree + 2 * it) % SEVEN] - scale.intervals[degree]).mod(12) }
            val suffix = suffixes[intervals.sorted()] ?: return@mapNotNull null
            val note = ScaleSpelling.spell(rootName, ScaleSpelling.letterStep(scale.degrees[degree]), (root + scale.intervals[degree]).mod(12))
            val name = note + suffix
            ScaleChord(numeral = ChordProNashville.numeral(name, scale.degrees[degree]), name = name)
        }
    }

    private const val SEVEN = 7

    /** The triads a stack of thirds can make, by their notes above the root, and how a chart writes each. */
    private val triadSuffixes = mapOf(
        listOf(0, 4, 7) to "",
        listOf(0, 3, 7) to "m",
        listOf(0, 3, 6) to "dim",
        listOf(0, 4, 8) to "aug",
        listOf(0, 2, 7) to "sus2",
        listOf(0, 5, 7) to "sus4",
    )

    /** The seventh chords a stack of thirds can make, as [triadSuffixes] lists the triads. */
    private val seventhSuffixes = mapOf(
        listOf(0, 4, 7, 11) to "maj7",
        listOf(0, 4, 7, 10) to "7",
        listOf(0, 3, 7, 10) to "m7",
        listOf(0, 3, 6, 10) to "m7b5",
        listOf(0, 3, 6, 9) to "dim7",
        listOf(0, 3, 7, 11) to "mmaj7",
        listOf(0, 4, 8, 11) to "maj7#5",
        listOf(0, 4, 8, 10) to "7#5",
        listOf(0, 4, 6, 10) to "7b5",
        listOf(0, 4, 6, 11) to "maj7b5",
    )
}
