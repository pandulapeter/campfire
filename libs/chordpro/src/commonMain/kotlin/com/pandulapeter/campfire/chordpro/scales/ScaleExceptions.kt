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
     * ♭II, the Andalusian and metal chord, its lowered second, never the ♭II's own major scale. With a Phrygian-dominant
     * home, its root's Phrygian where that holds the chord (the ♭III of a flamenco falseta). Otherwise the closest to the
     * home's notes, by the first scale of each, of: the harmonic minor a secondary dominant leads into (the E7 of a song
     * in C is A harmonic minor, where E Mixolydian is three notes away), where that root is a note of the key (a tritone
     * substitution's is not) and, on a tie with the chord's own root, where the part plays that minor chord (an A7 going
     * to a D7 is A Mixolydian); the harmonic minor a diminished chord leads into a semitone up (above any note of a
     * diminished seventh), else its own diminished scale, the one answer of eight notes; the scales of seven notes on the
     * chord's own root, the common ones first, each kind the closest first (the D7 of a song in C is D Mixolydian), and a common
     * scale of seven notes on the home's root other than a harmonic or melodic minor, which no player calls in a major
     * or modal song (the ♭VII of a major song is its key's Mixolydian, one note away where the ♭VII's own major scale
     * is two); a tie goes in that order, so a jazz blues's F7 stays F Mixolydian rather than its
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
                answerOver(chord, rootNames[first], Answer(rootName, root, emptyList()), home, chords, rootNames)
                    ?.let { ScaleException(chordName = names[first], rootName = it.rootName, scales = it.scales) }
            }
    }

    /**
     * What to play over [chord], on the root spelled [chordRootName], in a song at [home] on [homeRoot], the group
     * playing [chords] on the roots spelled [rootNames]; see [of].
     */
    private fun answerOver(chord: Chord, chordRootName: String, homeRoot: Answer, home: Scale, chords: List<Chord>, rootNames: List<String>): Answer? {
        if (home.id == NATURAL_MINOR_ID) {
            minorScales.filter { holdsChord(it, homeRoot.root, chord) }.takeIf { it.isNotEmpty() }?.let { return homeRoot.copy(scales = it) }
        }
        if (home.id == PHRYGIAN_DOMINANT_ID) {
            phrygianDominantScales.filter { holdsChord(it, homeRoot.root, chord) }.takeIf { it.isNotEmpty() }?.let { return homeRoot.copy(scales = it) }
        }
        val homeNotes = Scales.pitchClasses(home, homeRoot.root)
        fun distance(scale: Scale, root: Int) = Scales.pitchClasses(scale, root).count { it !in homeNotes }
        val target = targetOf(chord, chords, rootNames, chordRootName)?.takeIf { it.root in homeNotes }
        val firsts = ChordScaleChoices.firstChoices(chord)
        val onChordRoot = PlayableScales.over(chord)
            .filter { holdsChord(it, chord.root, chord) && (it.intervals.size == HOME_SCALE_SIZE || chord.isDiminished && it.id in firsts) }
            .sortedWith(
                compareBy(
                    { if (chord.isDiminished) firsts.indexOf(it.id).let { index -> if (index < 0) firsts.size else index } else 0 },
                    { it !in Scales.common },
                    { distance(it, chord.root) },
                ),
            )
        // An A7 in C is as close as D harmonic minor as A Mixolydian; it leads into the minor only where the part plays
        // the Dm it would resolve to, else it is the ragtime VI7 of `C A7 D7 G7`. A diminished chord's target is already
        // confirmed by the chord following it, so its tie stays with the target.
        val isUnconfirmedTie = target != null && !chord.isDiminished && onChordRoot.isNotEmpty() &&
            distance(harmonicMinor, target.root) == distance(onChordRoot.first(), chord.root) &&
            chords.none { it.root == target.root && MINOR_THIRD in it.intervals.map { interval -> interval.mod(12) } }
        // A borrowed chord of a major song comes from its parallel natural minor and the major modes, never from a
        // harmonic or melodic minor on the key's root, which a player would not call in a major song even where it is a
        // note closer: the A♭ of a song in C is C natural minor's, not C harmonic minor's.
        val onHomeRoot = Scales.common
            .filter { it != home && it.id !in raisedMinorIds && it.intervals.size == HOME_SCALE_SIZE && holdsChord(it, homeRoot.root, chord) }
            .sortedBy { distance(it, homeRoot.root) }
        return listOfNotNull(target.takeUnless { isUnconfirmedTie }, Answer(chordRootName, chord.root, onChordRoot), homeRoot.copy(scales = onHomeRoot))
            .filter { it.scales.isNotEmpty() }
            .minByOrNull { distance(it.scales.first(), it.root) }
    }

    /**
     * The harmonic minor [chord] (on the root spelled [chordRootName]) leads into: for a secondary dominant (a major
     * triad or a dominant seventh, with the flat ninth that is that harmonic minor's own, `A7b9` into Dm; a ninth or a
     * thirteenth is not in it) the chord a fourth up; for a diminished triad or seventh, the leading-tone chord, the
     * chord a semitone up, where a play of it in [chords] (spelled [rootNames]) is followed by a chord on that root,
     * spelled as that chord is (a `C#dim` before a `Dm`; an `Ebdim` falling to a `Dm7` is a passing chord, played over its
     * own diminished scale). [chords] are the group's parts one after another, read as a loop, so the last part's last
     * chord is followed by the first part's first. Null where the harmonic minor there does not hold the chord.
     */
    private fun targetOf(chord: Chord, chords: List<Chord>, rootNames: List<String>, chordRootName: String): Answer? {
        val target = when {
            chord.isMajorOrDominant || chord.isDominantSeventh -> (chord.root + FOURTH).mod(12).let {
                Answer(ScaleSpelling.spell(chordRootName, letterStep = FOURTH_LETTER_STEP, pitchClass = it), it, listOf(harmonicMinor))
            }
            chord.isDiminished -> chords.indices.filter { chords[it] == chord }
                .map { (it + 1) % chords.size }
                .firstOrNull { next -> (chords[next].root - 1).mod(12) in chord.leadingTones }
                ?.let { next -> Answer(rootNames[next], chords[next].root, listOf(harmonicMinor)) }
            else -> null
        }
        return target?.takeIf { holdsChord(harmonicMinor, it.root, chord) }
    }

    /** Whether the chord is a diminished triad or a diminished seventh, the leading-tone chord of the key a semitone up. */
    private val Chord.isDiminished
        get() = intervals.map { it.mod(12) }.toSet().let { MINOR_THIRD in it && FLAT_FIFTH in it && it.all { interval -> interval in diminishedSeventh } }

    /**
     * The notes a diminished chord is the leading-tone chord from: its root for a triad, every note of a diminished
     * seventh, which a chart names by whichever of them it likes (`C#dim7`, `Edim7`, `Gdim7`, and `Bbdim7` are one chord).
     */
    private val Chord.leadingTones get() = if (DIMINISHED_SEVENTH in intervals.map { it.mod(12) }) notes else setOf(root)

    /** Scales on one root, spelled [rootName], to play over an exception. */
    private data class Answer(val rootName: String, val root: Int, val scales: List<Scale>)

    private const val HOME_SCALE_SIZE = 7
    private const val FOURTH = 5
    private const val FOURTH_LETTER_STEP = 3
    private const val MINOR_THIRD = 3
    private const val FLAT_FIFTH = 6
    private const val DIMINISHED_SEVENTH = 9
    private const val NATURAL_MINOR_ID = "natural_minor"
    private const val PHRYGIAN_DOMINANT_ID = "phrygian_dominant"
    private val diminishedSeventh = setOf(0, MINOR_THIRD, FLAT_FIFTH, DIMINISHED_SEVENTH)
    private val raisedMinorIds = setOf("harmonic_minor", "melodic_minor")
    private val harmonicMinor = Scales.fromId("harmonic_minor")!!
    private val minorScales = listOf("harmonic_minor", "dorian", "phrygian", "melodic_minor").map(Scales::fromId).requireNoNulls()
    private val phrygianDominantScales = listOf("phrygian", "double_harmonic").map(Scales::fromId).requireNoNulls()
}
