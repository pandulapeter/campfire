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

/**
 * Every scale the app knows, in twelve-tone equal temperament. The maqamat and ragas that need quarter tones are left
 * out, since no fret or key plays them; their tempered relatives are in under the names their players use for them
 * (Hijaz is the Phrygian dominant, Hijaz Kar the double harmonic).
 *
 * No two scales are the same set of notes: a second name for one is an alias, never a second entry, so a scale is found
 * by its notes exactly once. Every list is built once and never changes.
 */
public object Scales {

    /** Every scale, the families in [ScaleFamily] order and each family in the order it is browsed in. */
    public val all: List<Scale> = listOf(
        scale("major", ScaleFamily.MAJOR_MODES, "0 2 4 5 7 9 11", "1 2 3 4 5 6 7", aliases = listOf("Ionian")),
        scale("dorian", ScaleFamily.MAJOR_MODES, "0 2 3 5 7 9 10", "1 2 b3 4 5 6 b7", "major" to 2),
        scale("phrygian", ScaleFamily.MAJOR_MODES, "0 1 3 5 7 8 10", "1 b2 b3 4 5 b6 b7", "major" to 3),
        scale("lydian", ScaleFamily.MAJOR_MODES, "0 2 4 6 7 9 11", "1 2 3 #4 5 6 7", "major" to 4),
        scale("mixolydian", ScaleFamily.MAJOR_MODES, "0 2 4 5 7 9 10", "1 2 3 4 5 6 b7", "major" to 5, listOf("Dominant scale")),
        scale("natural_minor", ScaleFamily.MAJOR_MODES, "0 2 3 5 7 8 10", "1 2 b3 4 5 b6 b7", "major" to 6, listOf("Aeolian")),
        scale("locrian", ScaleFamily.MAJOR_MODES, "0 1 3 5 6 8 10", "1 b2 b3 4 b5 b6 b7", "major" to 7),
        scale("harmonic_minor", ScaleFamily.HARMONIC_MINOR_MODES, "0 2 3 5 7 8 11", "1 2 b3 4 5 b6 7"),
        scale("locrian_natural_6", ScaleFamily.HARMONIC_MINOR_MODES, "0 1 3 5 6 9 10", "1 b2 b3 4 b5 6 b7", "harmonic_minor" to 2),
        scale(
            "ionian_sharp_5", ScaleFamily.HARMONIC_MINOR_MODES, "0 2 4 5 8 9 11", "1 2 3 4 #5 6 7", "harmonic_minor" to 3,
            listOf("Augmented major"),
        ),
        scale(
            "dorian_sharp_4", ScaleFamily.HARMONIC_MINOR_MODES, "0 2 3 6 7 9 10", "1 2 b3 #4 5 6 b7", "harmonic_minor" to 4,
            listOf("Romanian minor", "Ukrainian Dorian"),
        ),
        scale(
            "phrygian_dominant", ScaleFamily.HARMONIC_MINOR_MODES, "0 1 4 5 7 8 10", "1 b2 3 4 5 b6 b7", "harmonic_minor" to 5,
            listOf("Spanish Phrygian", "Phrygian major", "Hijaz", "Freygish", "Ahava Rabbah"),
        ),
        scale("lydian_sharp_2", ScaleFamily.HARMONIC_MINOR_MODES, "0 3 4 6 7 9 11", "1 #2 3 #4 5 6 7", "harmonic_minor" to 6),
        scale(
            "ultralocrian", ScaleFamily.HARMONIC_MINOR_MODES, "0 1 3 4 6 8 9", "1 b2 b3 b4 b5 b6 bb7", "harmonic_minor" to 7,
            listOf("Super Locrian ♭♭7"),
        ),
        scale("melodic_minor", ScaleFamily.MELODIC_MINOR_MODES, "0 2 3 5 7 9 11", "1 2 b3 4 5 6 7", aliases = listOf("Jazz minor")),
        scale(
            "dorian_flat_2", ScaleFamily.MELODIC_MINOR_MODES, "0 1 3 5 7 9 10", "1 b2 b3 4 5 6 b7", "melodic_minor" to 2,
            listOf("Phrygian ♮6"),
        ),
        scale("lydian_augmented", ScaleFamily.MELODIC_MINOR_MODES, "0 2 4 6 8 9 11", "1 2 3 #4 #5 6 7", "melodic_minor" to 3),
        scale(
            "lydian_dominant", ScaleFamily.MELODIC_MINOR_MODES, "0 2 4 6 7 9 10", "1 2 3 #4 5 6 b7", "melodic_minor" to 4,
            listOf("Acoustic", "Overtone", "Lydian ♭7", "Mixolydian ♯4"),
        ),
        scale(
            "mixolydian_flat_6", ScaleFamily.MELODIC_MINOR_MODES, "0 2 4 5 7 8 10", "1 2 3 4 5 b6 b7", "melodic_minor" to 5,
            listOf("Aeolian dominant", "Hindu"),
        ),
        scale(
            "locrian_natural_2", ScaleFamily.MELODIC_MINOR_MODES, "0 2 3 5 6 8 10", "1 2 b3 4 b5 b6 b7", "melodic_minor" to 6,
            listOf("Half-diminished"),
        ),
        scale(
            "altered", ScaleFamily.MELODIC_MINOR_MODES, "0 1 3 4 6 8 10", "1 b2 b3 b4 b5 b6 b7", "melodic_minor" to 7,
            listOf("Super Locrian", "Diminished whole tone"),
        ),
        scale("harmonic_major", ScaleFamily.MINOR_AND_HARMONIC, "0 2 4 5 7 8 11", "1 2 3 4 5 b6 7"),
        scale(
            "double_harmonic", ScaleFamily.MINOR_AND_HARMONIC, "0 1 4 5 7 8 11", "1 b2 3 4 5 b6 7",
            aliases = listOf("Byzantine", "Hijaz Kar", "Arabic", "Gypsy major"),
        ),
        scale(
            "hungarian_minor", ScaleFamily.MINOR_AND_HARMONIC, "0 2 3 6 7 8 11", "1 2 b3 #4 5 b6 7", "double_harmonic" to 4,
            listOf("Gypsy minor", "Double harmonic minor"),
        ),
        scale("oriental", ScaleFamily.MINOR_AND_HARMONIC, "0 1 4 5 6 9 10", "1 b2 3 4 b5 6 b7", "double_harmonic" to 5),
        scale("hungarian_major", ScaleFamily.MINOR_AND_HARMONIC, "0 3 4 6 7 9 10", "1 #2 3 #4 5 6 b7"),
        scale("neapolitan_major", ScaleFamily.MINOR_AND_HARMONIC, "0 1 3 5 7 9 11", "1 b2 b3 4 5 6 7"),
        scale("leading_whole_tone", ScaleFamily.MINOR_AND_HARMONIC, "0 2 4 6 8 10 11", "1 2 3 #4 #5 #6 7", "neapolitan_major" to 2),
        scale("lydian_minor", ScaleFamily.MINOR_AND_HARMONIC, "0 2 4 6 7 8 10", "1 2 3 #4 5 b6 b7", "neapolitan_major" to 4),
        scale("major_locrian", ScaleFamily.MINOR_AND_HARMONIC, "0 2 4 5 6 8 10", "1 2 3 4 b5 b6 b7", "neapolitan_major" to 5),
        scale("neapolitan_minor", ScaleFamily.MINOR_AND_HARMONIC, "0 1 3 5 7 8 11", "1 b2 b3 4 5 b6 7"),
        scale("major_pentatonic", ScaleFamily.PENTATONIC, "0 2 4 7 9", "1 2 3 5 6"),
        scale("minor_pentatonic", ScaleFamily.PENTATONIC, "0 3 5 7 10", "1 b3 4 5 b7", "major_pentatonic" to 5),
        scale("egyptian", ScaleFamily.PENTATONIC, "0 2 5 7 10", "1 2 4 5 b7", "major_pentatonic" to 2, listOf("Suspended pentatonic")),
        scale("man_gong", ScaleFamily.PENTATONIC, "0 3 5 8 10", "1 b3 4 b6 b7", "major_pentatonic" to 3, listOf("Blues minor pentatonic")),
        scale("ritusen", ScaleFamily.PENTATONIC, "0 2 5 7 9", "1 2 4 5 6", "major_pentatonic" to 4, listOf("Yo", "Blues major pentatonic")),
        scale("hirajoshi", ScaleFamily.PENTATONIC, "0 2 3 7 8", "1 2 b3 5 b6"),
        scale("in", ScaleFamily.PENTATONIC, "0 1 5 7 8", "1 b2 4 5 b6", aliases = listOf("Sakura")),
        scale("insen", ScaleFamily.PENTATONIC, "0 1 5 7 10", "1 b2 4 5 b7"),
        scale("iwato", ScaleFamily.PENTATONIC, "0 1 5 6 10", "1 b2 4 b5 b7"),
        scale("kumoi", ScaleFamily.PENTATONIC, "0 2 3 7 9", "1 2 b3 5 6"),
        scale("chinese", ScaleFamily.PENTATONIC, "0 4 6 7 11", "1 3 #4 5 7"),
        scale("blues", ScaleFamily.BLUES_AND_HEXATONIC, "0 3 5 6 7 10", "1 b3 4 b5 5 b7", aliases = listOf("Minor blues")),
        scale("major_blues", ScaleFamily.BLUES_AND_HEXATONIC, "0 2 3 4 7 9", "1 2 b3 3 5 6", "blues" to 2),
        scale("major_hexatonic", ScaleFamily.BLUES_AND_HEXATONIC, "0 2 4 5 7 9", "1 2 3 4 5 6"),
        scale("minor_hexatonic", ScaleFamily.BLUES_AND_HEXATONIC, "0 2 3 5 7 10", "1 2 b3 4 5 b7", "major_hexatonic" to 2),
        scale("prometheus", ScaleFamily.BLUES_AND_HEXATONIC, "0 2 4 6 9 10", "1 2 3 #4 6 b7", aliases = listOf("Mystic")),
        scale("tritone", ScaleFamily.BLUES_AND_HEXATONIC, "0 1 4 6 7 10", "1 b2 3 #4 5 b7", aliases = listOf("Petrushka")),
        scale("whole_tone", ScaleFamily.SYMMETRIC, "0 2 4 6 8 10", "1 2 3 #4 #5 #6"),
        scale("augmented", ScaleFamily.SYMMETRIC, "0 3 4 7 8 11", "1 #2 3 5 #5 7", aliases = listOf("Hexatonic")),
        scale("diminished", ScaleFamily.SYMMETRIC, "0 2 3 5 6 8 9 11", "1 2 b3 4 b5 b6 6 7", aliases = listOf("Octatonic")),
        scale("dominant_diminished", ScaleFamily.SYMMETRIC, "0 1 3 4 6 7 9 10", "1 b2 b3 3 b5 5 6 b7", "diminished" to 2),
        scale(CHROMATIC_ID, ScaleFamily.SYMMETRIC, "0 1 2 3 4 5 6 7 8 9 10 11", "1 b2 2 b3 3 4 b5 5 b6 6 b7 7"),
        scale("bebop_dominant", ScaleFamily.BEBOP, "0 2 4 5 7 9 10 11", "1 2 3 4 5 6 b7 7"),
        scale("bebop_major", ScaleFamily.BEBOP, "0 2 4 5 7 8 9 11", "1 2 3 4 5 b6 6 7"),
        scale("bebop_dorian", ScaleFamily.BEBOP, "0 2 3 4 5 7 9 10", "1 2 b3 3 4 5 6 b7", "bebop_dominant" to 5),
        scale("bebop_melodic_minor", ScaleFamily.BEBOP, "0 2 3 5 7 8 9 11", "1 2 b3 4 5 b6 6 7"),
        scale("bebop_harmonic_minor", ScaleFamily.BEBOP, "0 2 3 5 7 8 10 11", "1 2 b3 4 5 b6 b7 7"),
        scale("persian", ScaleFamily.WORLD, "0 1 4 5 6 8 11", "1 b2 3 4 b5 b6 7"),
        scale("enigmatic", ScaleFamily.WORLD, "0 1 4 6 8 10 11", "1 b2 3 #4 #5 #6 7"),
        scale("flamenco", ScaleFamily.WORLD, "0 1 3 4 5 7 8 10", "1 b2 b3 3 4 5 b6 b7"),
        scale("spanish_eight_tone", ScaleFamily.WORLD, "0 1 3 4 5 6 8 10", "1 b2 b3 3 4 b5 b6 b7"),
    )

    private val byId = all.associateBy { it.id }

    /**
     * The shortlist offered before the families, in the order a player reaches for them: the major and minor keys, the
     * pentatonics and the blues a solo starts from, the two modes rock and folk sit in, then the minor scales of jazz and
     * classical harmony. Each an entry of [all].
     */
    public val common: List<Scale> = listOf(
        "major", "natural_minor", "minor_pentatonic", "major_pentatonic", "blues", "dorian", "mixolydian", "harmonic_minor",
        "melodic_minor", "major_blues", CHROMATIC_ID,
    ).map(byId::getValue)

    private val byFamily = all.groupBy { it.family }

    /** The scales of [family], in the order they are browsed in. */
    public fun of(family: ScaleFamily): List<Scale> = byFamily[family].orEmpty()

    /** The scale with [id], or null for an id no scale has (one saved by another version of the app). */
    public fun fromId(id: String): Scale? = byId[id]

    /** The pitch classes [scale] plays on [root], a pitch class from 0 for C to 11 for B. */
    public fun pitchClasses(scale: Scale, root: Int): Set<Int> = scale.intervals.map { (root + it).mod(12) }.toSet()

    /** The semitones between each note of [scale] and the next, the last one closing the octave: `2 2 1 2 2 2 1`. */
    public fun steps(scale: Scale): List<Int> = (scale.intervals.drop(1) + 12).zip(scale.intervals) { next, note -> next - note }

    /**
     * The pitch class of the root of the scale [scale] is a mode of, where it is played on [root]: C for D Dorian. Null
     * where it is a mode of none.
     */
    public fun parentRoot(scale: Scale, root: Int): Int? {
        val (parentId, degree) = scale.modeOf ?: return null
        val parent = fromId(parentId) ?: return null
        return (root - parent.intervals[degree - 1]).mod(12)
    }

    /**
     * The id of the chromatic scale, which holds every note and so every chord: it is browsed like any other, but never
     * suggested for a chord, since it says nothing about one.
     */
    internal const val CHROMATIC_ID = "chromatic"

    private fun scale(
        id: String,
        family: ScaleFamily,
        intervals: String,
        degrees: String,
        modeOf: Pair<String, Int>? = null,
        aliases: List<String> = emptyList(),
    ) = Scale(
        id = id,
        family = family,
        intervals = intervals.split(' ').map(String::toInt),
        degrees = degrees.split(' '),
        modeOf = modeOf,
        aliases = aliases,
    )
}
