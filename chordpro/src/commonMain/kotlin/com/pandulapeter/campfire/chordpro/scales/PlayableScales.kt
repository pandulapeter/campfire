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
 *   home's major pentatonic; over a dominant blues ([isDominantBlues]: C7 F G, C F7 G, a jazz blues with its ii–Vs, a
 *   twelve-bar of triads turning around from its V7 to the IV, but not the plain cadence C F G7), first of all where
 *   the major scale holds it; and over a rock riff, every
 *   chord a power chord, major triad, or dominant seventh on I, ♭III, IV, V, ♭VI, or ♭VII, or a thirdless one on ♭V, the
 *   flat ones a quarter of them at least (E G A, C B♭ F, A5 C5 D5, G5 B♭5 D♭5).
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
        // A blues the major scale holds (triads and a V7 turning around to the IV) is still played as a blues.
        val lead = if (isDominantBlues && home?.id == MAJOR_ID) dominantBluesScales else emptyList()
        val scales = (
            lead +
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
     * Whether [chords] are a dominant blues on [root], of one of two shapes:
     *
     * - **Plain**: each a chord with a major third and no major seventh on the first, fourth, or fifth step.
     * - **Jazz**: every chord on the first step a dominant seventh, that step played more often than any other, no major
     *   seventh on the first, fourth, or fifth step, and the major chords there more than half the plays; the rest (a
     *   ii–V, a passing diminished chord, a VI7) are departures the home names as exceptions. Without the first two, a
     *   gospel chart or Here Comes the Sun would read as a blues on a lesser root.
     *
     * Either needs a blues mark: the first or the fourth step played as a dominant seventh at least as often as plain, or
     * the turnaround, a chord on the fifth step followed by one on the fourth (read as a loop, a section repeating), in
     * a part half on its first step that plays a dominant seventh somewhere. The share tells a twelve-bar from rock and
     * roll's I IV V IV loops, the seventh from Bad Moon Rising's triads, and a seventh on the fifth alone is the plain
     * cadence of every hymn and folk song, as a tonic seventh passing once among plain tonics is a folk verse's colour.
     * A major seventh on the tonic (a Cmaj7 beside an F7) is a bossa's, which the blues scales' flat seventh clashes with.
     * A first step with a flat ninth, a flat fifth, or a sharp fifth is an altered dominant, whose alteration the blues
     * scales' natural fifth and ninth clash with; an altered turnaround elsewhere leaves the other bars a blues. A sharp
     * ninth is the blues third, so it stays.
     */
    internal fun isDominantBlues(root: Int, chords: List<Chord>) = bluesShapeOf(root, chords) != null

    /** Whether [chords] are a dominant blues on [root] of the plain shape ([isDominantBlues]), every chord on I, IV, or V. */
    internal fun isPlainBlues(root: Int, chords: List<Chord>) = bluesShapeOf(root, chords) == BluesShape.PLAIN

    private fun bluesShapeOf(root: Int, chords: List<Chord>): BluesShape? {
        if (chords.isEmpty()) return null
        fun step(chord: Chord) = (chord.root - root).mod(12)
        fun intervalsOf(chord: Chord) = chord.intervals.map { it.mod(12) }
        val onBluesSteps = chords.filter { step(it) in bluesSteps && intervalsOf(it).let { intervals -> MAJOR_THIRD in intervals && MAJOR_SEVENTH !in intervals } }
        val onTonic = chords.filter { step(it) == 0 }
        val isTonicMostPlayed = chords.groupingBy { step(it) }.eachCount().all { (other, plays) -> other == 0 || plays < onTonic.size }
        val hasMajorSeventh = chords.any { step(it) in bluesSteps && MAJOR_SEVENTH in intervalsOf(it) }
        val shape = when {
            onBluesSteps.size == chords.size -> BluesShape.PLAIN
            onTonic.isNotEmpty() && onTonic.all { it.isDominantSeventh } && isTonicMostPlayed && !hasMajorSeventh && onBluesSteps.size * 2 > chords.size -> BluesShape.JAZZ
            else -> return null
        }
        if (onTonic.any { chord -> intervalsOf(chord).any { it in alterations } }) return null
        fun isMostlySeventh(step: Int) = chords.filter { step(it) == step }.let { on -> on.isNotEmpty() && on.count { MINOR_SEVENTH in it.intervals } * 2 >= on.size }
        val hasTurnaround = onTonic.size * 2 >= chords.size && chords.any { it.isDominantSeventh } &&
            chords.indices.any { step(chords[it]) == FIFTH && step(chords[(it + 1) % chords.size]) == FOURTH }
        return shape.takeIf { isMostlySeventh(0) || isMostlySeventh(FOURTH) || hasTurnaround }
    }

    private enum class BluesShape { PLAIN, JAZZ }

    /**
     * Whether [chords] are a rock riff on [root]: each a chord with no third (a power or a suspended chord), a major
     * triad, or a dominant seventh on its first,
     * flat third, fourth, fifth, flat sixth, or flat seventh step (the minor pentatonic's roots and rock's ♭VI), or a
     * chord with no third on the flat fifth, the blues scale's own note (the D♭5 of Smoke on the Water), with a
     * ♭III or ♭VI, the minor key's roots Mixolydian lacks, played as at least one chord in [MIN_MINOR_SHARE], or any flat
     * step where no chord has a third. A power chord has no third to clash with the minor pentatonic's, so A5 C5 D5 is
     * the plainest riff of all, a suspended one passing in it none either. A ♭VII is Mixolydian's own: I IV ♭VII is a
     * Mixolydian part, and its V or a ♭VII closing a long I IV V is an exception to it or to the major key.
     */
    internal fun isRockRiff(root: Int, chords: List<Chord>): Boolean {
        if (chords.isEmpty()) return false
        val isOnRiffStep = chords.all { chord ->
            val step = (chord.root - root).mod(12)
            step in riffSteps && (chord.isMajorOrDominant || chord.hasNoThird) || step == FLAT_FIFTH && chord.hasNoThird
        }
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
    private const val FOURTH = 5
    private const val FLAT_FIFTH = 6
    private const val FIFTH = 7
    private const val MINOR_SEVENTH = 10
    private const val MAJOR_SEVENTH = 11
    private const val MAJOR_PENTATONIC_ID = "major_pentatonic"
    private const val MINOR_PENTATONIC_ID = "minor_pentatonic"
    private const val MIXOLYDIAN_ID = "mixolydian"
    private const val MAJOR_ID = "major"
    private val bluesSteps = setOf(0, 5, 7)
    private val alterations = setOf(1, 6, 8)
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
    private val dominantBluesScales = scalesOf(MINOR_PENTATONIC_ID, "blues", MAJOR_PENTATONIC_ID, MIXOLYDIAN_ID, "major_blues")

    private fun scalesOf(vararg ids: String) = ids.map(Scales::fromId).requireNoNulls()
}
