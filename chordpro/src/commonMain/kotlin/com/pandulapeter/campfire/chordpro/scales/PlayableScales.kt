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

import com.pandulapeter.campfire.chordpro.model.Chord

/**
 * The scales to suggest for playing over chords, on a root: the ones holding every note of them
 * ([ScaleChords.scalesContaining]), and the kinds a player reaches for first that hold fewer notes or other ones.
 *
 * - **The pentatonics inside the home scale**, the first seven-note scale holding the chords: the one sharing its third
 *   first, then the blues scale where the minor pentatonic is among them (its flat fifth is a passing tone). They leave
 *   out its tense notes, so they are the safe choice, though five notes never hold a key's chords. A harmonic or melodic
 *   minor home offers natural minor's, the safe set over a minor song's i and iv.
 * - **The minor pentatonic and the blues over a major sound that has a flat third or seventh**: after a Mixolydian
 *   home's major pentatonic; over a dominant blues, every chord a major one on the first, fourth, or fifth step, the
 *   first or the fourth a dominant seventh (C7 F G, C F7 G, but not the plain cadence C F G7); and over a rock riff, every
 *   chord a power chord, major triad, or dominant seventh on I, ♭III, IV, V, ♭VI, or ♭VII, the flat ones a quarter of
 *   them at least (E G A, C B♭ F, A5 C5 D5).
 *   The flat third against the chords' major third is the sound of the style, which no scale holding the notes plays, so
 *   a dominant blues is a fit with no scale holding it. A rock riff is not: its ♭III and ♭VII are the roots of every
 *   key a step or a fourth away, and as a fit it would hold a verse and a chorus in two keys together.
 *
 * After these, the other common scales holding the notes, the other seven-note ones, the hexatonics and the rest of six
 * notes or fewer, and the eight-note scales last, so that a suggestion cut short keeps what a player reaches for.
 */
public object PlayableScales {

    /**
     * The scales to play over [chord], on its root, its quality's textbook scales first ([ChordScaleChoices]) where they
     * hold it; the bass of a slash chord is left out, as a voicing.
     */
    public fun over(chord: Chord): List<Scale> {
        val scales = over(chord.root, listOf(chord))
        val firsts = ChordScaleChoices.firstChoices(chord)
        return firsts.mapNotNull { id -> scales.firstOrNull { it.id == id } } + scales.filter { it.id !in firsts }
    }

    /** The scales on [root] to play over all of [chords], each read without its bass. */
    public fun over(root: Int, chords: List<Chord>): List<Scale> = suggestionFor(root, chords)?.scales.orEmpty()

    /**
     * The scales on [root] for [chords], and whether they are a fit to group a song by ([Suggestion.isFit]): a scale of
     * at most seven notes holds them, or they are a dominant blues. Null where nothing is suggested.
     */
    internal fun suggestionFor(root: Int, chords: List<Chord>): Suggestion? {
        val notes = chords.flatMapTo(mutableSetOf()) { chord -> chord.intervals.map { (chord.root + it).mod(12) } }
        val holding = ScaleChords.scalesContaining(root, notes)
        val home = holding.firstOrNull { it.intervals.size == HOME_SCALE_SIZE }
        val isDominantBlues = isDominantBlues(root, chords)
        val isRockRiff = isRockRiff(root, chords)
        if (holding.isEmpty() && !isDominantBlues && !isRockRiff) return null
        val source = if (home?.id in raisedMinorIds) naturalMinor else home
        val inside = source?.let { scale -> pentatonics.filter { scale.intervals.containsAll(it.intervals) } }.orEmpty()
            .sortedBy { MAJOR_THIRD !in (source?.intervals.orEmpty()) == (it.id == MAJOR_PENTATONIC_ID) }
        val bluesSound = when {
            home == null && isDominantBlues -> dominantBluesScales
            home == null && isRockRiff -> rockRiffScales
            else -> {
                val minorBlues = if (inside.any { it.id == MINOR_PENTATONIC_ID } || home?.id == MIXOLYDIAN_ID || isRockRiff) rockRiffScales else emptyList()
                inside + minorBlues + if (isDominantBlues) dominantBluesScales else emptyList()
            }
        }
        val hexatonics = (source?.let { scale -> insideHexatonics.filter { scale.intervals.containsAll(it.intervals) } }.orEmpty() + holding)
            .filter { it.id in hexatonicIds }
        val scales = (
            listOfNotNull(home) +
                bluesSound +
                holding.filter { it in Scales.common && it.intervals.size <= HOME_SCALE_SIZE } +
                holding.filter { it.intervals.size == HOME_SCALE_SIZE } +
                hexatonics +
                holding.filter { it.intervals.size < HOME_SCALE_SIZE } +
                holding.filter { it.intervals.size > HOME_SCALE_SIZE }
            ).distinct()
        return Suggestion(scales = scales, isFit = isDominantBlues || holding.any { it.intervals.size <= HOME_SCALE_SIZE })
    }

    /**
     * Whether [chords] are a dominant blues on [root]: each a chord with a major third on its first, fourth or fifth
     * step, and the first or the fourth step's a dominant seventh, its third and flat seventh both there. A seventh on
     * the fifth alone is the plain cadence of every hymn and folk song, no blues. A first step with a flat ninth, a flat
     * fifth, or a sharp fifth is an altered dominant, whose alteration the blues scales' natural fifth and ninth clash
     * with; an altered turnaround elsewhere leaves the other bars a blues. A sharp ninth is the blues third, so it stays.
     */
    internal fun isDominantBlues(root: Int, chords: List<Chord>): Boolean {
        if (chords.isEmpty()) return false
        val isMajorOnBluesStep = chords.all { (it.root - root).mod(12) in bluesSteps && MAJOR_THIRD in it.intervals }
        val isAltered = chords.any { chord -> (chord.root - root).mod(12) == 0 && chord.intervals.any { it.mod(12) in alterations } }
        return isMajorOnBluesStep && !isAltered && chords.any { (it.root - root).mod(12) in seventhSteps && MINOR_SEVENTH in it.intervals }
    }

    /**
     * Whether [chords] are a rock riff on [root]: each a chord with no third (a power or a suspended chord), a major
     * triad, or a dominant seventh on its first,
     * flat third, fourth, fifth, flat sixth, or flat seventh step (the minor pentatonic's roots and rock's ♭VI), with a
     * ♭III or ♭VI, the minor key's roots Mixolydian lacks, played as at least one chord in [MIN_MINOR_SHARE], or any flat
     * step where no chord has a third. A power chord has no third to clash with the minor pentatonic's, so A5 C5 D5 is
     * the plainest riff of all, a suspended one passing in it none either. A ♭VII is Mixolydian's own: I IV ♭VII is a
     * Mixolydian part, and its V or a ♭VII closing a long I IV V is an exception to it or to the major key.
     */
    internal fun isRockRiff(root: Int, chords: List<Chord>): Boolean {
        if (chords.isEmpty()) return false
        val isOnRiffStep = chords.all { chord -> (chord.root - root).mod(12) in riffSteps && (chord.isMajorOrDominant || chord.hasNoThird) }
        val minorRoots = chords.count { (it.root - root).mod(12) in minorRiffSteps }
        // With no third anywhere, nothing says major, so any flat step is the riff's.
        val isThirdless = chords.all { it.hasNoThird } && chords.any { (it.root - root).mod(12) in flatRiffSteps }
        return isOnRiffStep && (minorRoots * MIN_MINOR_SHARE >= chords.size || isThirdless)
    }

    /** What [suggestionFor] answers. */
    internal class Suggestion(val scales: List<Scale>, val isFit: Boolean)

    private const val HOME_SCALE_SIZE = 7
    private const val MIN_MINOR_SHARE = 8
    private const val MAJOR_THIRD = 4
    private const val MINOR_SEVENTH = 10
    private const val MAJOR_PENTATONIC_ID = "major_pentatonic"
    private const val MINOR_PENTATONIC_ID = "minor_pentatonic"
    private const val MIXOLYDIAN_ID = "mixolydian"
    private val bluesSteps = setOf(0, 5, 7)
    private val alterations = setOf(1, 6, 8)
    private val seventhSteps = setOf(0, 5)
    private val riffSteps = setOf(0, 3, 5, 7, 8, 10)
    private val flatRiffSteps = setOf(3, 8, 10)
    private val minorRiffSteps = setOf(3, 8)
    private val raisedMinorIds = setOf("harmonic_minor", "melodic_minor")
    private val hexatonicIds = setOf("major_hexatonic", "minor_hexatonic")
    private val naturalMinor = scalesOf("natural_minor").single()
    private val pentatonics = scalesOf(MAJOR_PENTATONIC_ID, MINOR_PENTATONIC_ID)
    private val insideHexatonics = scalesOf("major_hexatonic", "minor_hexatonic")
    /** What a rock riff ([isRockRiff]) is played over besides the scales holding it, on its root. */
    internal val rockRiffScales = scalesOf(MINOR_PENTATONIC_ID, "blues")
    private val dominantBluesScales = scalesOf(MINOR_PENTATONIC_ID, "blues", MAJOR_PENTATONIC_ID, "major_blues", MIXOLYDIAN_ID)

    private fun scalesOf(vararg ids: String) = ids.map(Scales::fromId).requireNoNulls()
}
