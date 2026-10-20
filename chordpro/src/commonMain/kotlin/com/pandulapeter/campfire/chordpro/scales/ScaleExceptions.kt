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

/** The chords a home scale leaves out, each with what to play over it instead, for [SongScaleGroups]. */
internal object ScaleExceptions {

    /**
     * The chords of [chords] (written as [names], on the roots spelled [rootNames]) that [home] on [root] (spelled
     * [rootName]) does not hold, the most played first, then in play order, each with the scales to play over it.
     *
     * With a natural-minor home, the minor scales on its root that hold it (harmonic minor, Dorian, and Phrygian change
     * one note of it, melodic minor two): a minor song's major V and IV are its own raised seventh and sixth, and its
     * ♭II, the Andalusian and metal chord, its lowered second, never the ♭II's own major scale. Otherwise the closest
     * to the home's notes, by the first scale of each, of: the harmonic minor a secondary dominant leads into (the E7 of
     * a song in C is A harmonic minor, where E Mixolydian is three notes away), the scales of seven notes on the chord's
     * own root, the common ones first, each kind the closest first (the D7 of a song in C is D Mixolydian), and a common
     * scale of seven notes on the home's root (the ♭VII of a major song is its key's Mixolydian, one note away where the
     * ♭VII's own major scale is two); a tie goes in that order, so a jazz blues's F7 stays F Mixolydian rather than its
     * notes said as C Dorian. The pentatonics and the blues a lone chord is offered would clash with the key around it. A
     * chord nothing holds is not named; how often one is played decides whether a song shows it ([SongScaleSuggestions]).
     */
    fun of(chords: List<Chord>, names: List<String>, rootNames: List<String>, root: Int, rootName: String, home: Scale): List<ScaleException> {
        val homeNotes = Scales.pitchClasses(home, root)
        return chords.indices
            .filter { !homeNotes.containsAll(chords[it].notes) }
            .groupBy { chords[it] }
            .entries
            .sortedByDescending { it.value.size }
            .mapNotNull { (chord, indices) ->
                val first = indices.first()
                answerOver(chord, rootNames[first], Answer(rootName, root, emptyList()), home)
                    ?.let { ScaleException(chordName = names[first], rootName = it.rootName, scales = it.scales) }
            }
    }

    /** What to play over [chord], on the root spelled [chordRootName], in a song at [home] on [homeRoot]; see [of]. */
    private fun answerOver(chord: Chord, chordRootName: String, homeRoot: Answer, home: Scale): Answer? {
        if (home.id == NATURAL_MINOR_ID) {
            minorScales.filter { holdsChord(it, homeRoot.root, chord) }.takeIf { it.isNotEmpty() }?.let { return homeRoot.copy(scales = it) }
        }
        val homeNotes = Scales.pitchClasses(home, homeRoot.root)
        fun distance(scale: Scale, root: Int) = Scales.pitchClasses(scale, root).count { it !in homeNotes }
        val target = targetMinorOf(chord)?.let { Answer(ScaleSpelling.spell(chordRootName, letterStep = FOURTH_LETTER_STEP, pitchClass = it), it, listOf(harmonicMinor)) }
        val onChordRoot = PlayableScales.over(chord)
            .filter { it.intervals.size == HOME_SCALE_SIZE && holdsChord(it, chord.root, chord) }
            .sortedWith(compareBy({ it !in Scales.common }, { distance(it, chord.root) }))
        val onHomeRoot = Scales.common
            .filter { it != home && it.intervals.size == HOME_SCALE_SIZE && holdsChord(it, homeRoot.root, chord) }
            .sortedBy { distance(it, homeRoot.root) }
        return listOfNotNull(target, Answer(chordRootName, chord.root, onChordRoot), homeRoot.copy(scales = onHomeRoot))
            .filter { it.scales.isNotEmpty() }
            .minByOrNull { distance(it.scales.first(), it.root) }
    }

    /** The root of the harmonic minor [chord] leads into as a secondary dominant (a major triad or a dominant seventh). */
    private fun targetMinorOf(chord: Chord) =
        (chord.root + FOURTH).mod(12).takeIf { chord.isMajorOrDominant && holdsChord(harmonicMinor, it, chord) }

    /** Scales on one root, spelled [rootName], to play over an exception. */
    private data class Answer(val rootName: String, val root: Int, val scales: List<Scale>)

    private const val HOME_SCALE_SIZE = 7
    private const val FOURTH = 5
    private const val FOURTH_LETTER_STEP = 3
    private const val NATURAL_MINOR_ID = "natural_minor"
    private val harmonicMinor = Scales.fromId("harmonic_minor")!!
    private val minorScales = listOf("harmonic_minor", "dorian", "phrygian", "melodic_minor").map(Scales::fromId).requireNoNulls()
}
