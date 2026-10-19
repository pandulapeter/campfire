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
 * ([ScaleChords.scalesContaining]), and two kinds a player reaches for first that hold fewer notes or other ones.
 *
 * - **The pentatonic and hexatonic scales inside the home scale**: where a seven-note scale holds the chords (C major
 *   over C, F and G), the major and minor pentatonics and hexatonics whose notes are all in it are offered too. They
 *   leave out its tense notes, so they are the safe choice, even though a five-note scale can never hold the seven
 *   notes a key's chords play.
 * - **The blues scales**: the minor blues scale wherever the home scale holds the minor pentatonic (its flat fifth is a
 *   passing tone), and over a dominant blues, every chord a major one on the first, fourth or fifth step and the first a
 *   dominant seventh (C7 F7 G7, or a lone C7), the minor pentatonic, the blues scale and the major ones too: the flat
 *   third against the chords' major third is the sound of the style, which no scale holding the chords' notes plays.
 *
 * The common scales that hold the notes come first, then these, then the rest that hold them, so that a suggestion
 * cut short still has them.
 */
public object PlayableScales {

    /** The scales to play over [chord], on its root; the bass of a slash chord is left out, as a voicing. */
    public fun over(chord: Chord): List<Scale> = over(chord.root, listOf(chord))

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
        val inside = home?.let { scale -> insideHome.filter { scale.intervals.containsAll(it.intervals) } }.orEmpty()
        val minorBlues = if (inside.any { it.id == MINOR_PENTATONIC_ID }) listOfNotNull(Scales.fromId(BLUES_ID)) else emptyList()
        val dominantBlues = if (isDominantBlues(root, chords)) dominantBluesScales else emptyList()
        if (holding.isEmpty() && dominantBlues.isEmpty()) return null
        val added = (inside + minorBlues + dominantBlues).filter { it !in holding }
        val scales = (holding.filter { it in Scales.common } + added + holding.filter { it !in Scales.common }).distinct()
        return Suggestion(scales = scales, isFit = dominantBlues.isNotEmpty() || holding.any { it.intervals.size <= HOME_SCALE_SIZE })
    }

    /**
     * Whether [chords] are a dominant blues on [root]: each a chord with a major third on its first, fourth or fifth
     * step, and the first step's a dominant seventh, its third and flat seventh both there.
     */
    internal fun isDominantBlues(root: Int, chords: List<Chord>): Boolean {
        if (chords.isEmpty()) return false
        val isMajorOnBluesStep = chords.all { (it.root - root).mod(12) in bluesSteps && MAJOR_THIRD in it.intervals }
        return isMajorOnBluesStep && chords.any { (it.root - root).mod(12) == 0 && MINOR_SEVENTH in it.intervals }
    }

    /** What [suggestionFor] answers. */
    internal class Suggestion(val scales: List<Scale>, val isFit: Boolean)

    private const val HOME_SCALE_SIZE = 7
    private const val MAJOR_THIRD = 4
    private const val MINOR_SEVENTH = 10
    private const val MINOR_PENTATONIC_ID = "minor_pentatonic"
    private const val BLUES_ID = "blues"
    private val bluesSteps = setOf(0, 5, 7)
    private val insideHome = listOf("major_pentatonic", "minor_pentatonic", "major_hexatonic", "minor_hexatonic").map(Scales::fromId).requireNoNulls()
    private val dominantBluesScales = listOf(MINOR_PENTATONIC_ID, BLUES_ID, "major_pentatonic", "major_blues", "mixolydian").map(Scales::fromId).requireNoNulls()
}
