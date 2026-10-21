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

/** The chords of a list of parts, read once for every fit tried on them. */
internal class Harmony(parts: List<SongPart>) {
    val key = parts.first().key
    val names = parts.flatMap { it.names }
    val chords = parts.flatMap { it.chords }
    val roots = parts.flatMap { it.roots }
    val ranked by lazy { rankedRoots(parts) }
    private val notes = chords.flatMapTo(mutableSetOf()) { it.notes }

    fun holds(scale: Scale, root: SpelledNote) = Scales.pitchClasses(scale, root.pitchClass).containsAll(notes)

    /** The chords [home] on [root] leaves out, each with what to play over it ([ScaleExceptions]). */
    fun exceptionsTo(home: Scale, root: SpelledNote) = ScaleExceptions.of(chords, names, roots.map { it.name }, root.pitchClass, root.name, home)

    /** The first of the scales [ids] names that holds every chord on [root]. */
    fun holding(root: SpelledNote, ids: List<String>) = ids.firstNotNullOfOrNull { id -> Scales.fromId(id)?.takeIf { holds(it, root) } }

    /**
     * Whether a home on [root] counts the chords leading into it ([leadsInto]): never for a rock riff, whose major chords
     * are the riff's, not secondary dominants; and on the key's root only while the part is still in the key ([leavesKey]).
     */
    fun countsLeadersOn(root: SpelledNote) = !PlayableScales.isRockRiff(root.pitchClass, chords) &&
        (key?.root?.pitchClass != root.pitchClass || !leavesKey(key))

    /**
     * Whether the part has left [key] for another: its best scored root is another, and the major or natural minor scale
     * there, not sharing the key's notes, holds more of its chords than the key's own scale does. A bridge in the key of
     * the V (`D A Bm F#m G D E A` under `{key: G}`) is then read in D: its A and E lead into notes of G major too, and
     * counted as the key's they would make it G with three exceptions.
     */
    fun leavesKey(key: SongKey): Boolean {
        val centre = ranked.first().takeIf { it.pitchClass != key.root.pitchClass } ?: return false
        val keyScale = if (key.isMinor) naturalMinor else major
        val keyNotes = Scales.pitchClasses(keyScale, key.root.pitchClass)
        val held = chords.count { holdsChord(keyScale, key.root.pitchClass, it) }
        return listOf(major, naturalMinor).any { scale ->
            Scales.pitchClasses(scale, centre.pitchClass) != keyNotes && chords.count { holdsChord(scale, centre.pitchClass, it) } > held
        }
    }

    /**
     * Whether the chord at [index] is the ii of a secondary ii–V into [home] on [root] (`Gm7 C7` into F in a song in C): a
     * minor chord followed by a chord leading into the home a fourth above it. A ii is never the last chord before its V,
     * so the chords are not read as a loop here.
     */
    fun isTwoOfLeaderInto(home: Scale, root: Int, index: Int): Boolean {
        val chord = chords[index]
        val next = chords.getOrNull(index + 1) ?: return false
        val intervals = chord.intervals.map { it.mod(12) }
        return MINOR_THIRD in intervals && MAJOR_THIRD !in intervals && leadsInto(home, root, next) && (next.root - chord.root).mod(12) == FOURTH
    }
}

/**
 * Whether [chord] leads into [home] on [root] as the home's own harmony would: a major triad or a dominant seventh
 * (altered or not) whose target, the root a fourth above it, is a note of the home (the E7 and A7 of a song in C), or a
 * diminished chord a semitone under one (its F#dim). Such a chord is the key's, not against it, though the home leaves
 * its notes out.
 */
internal fun leadsInto(home: Scale, root: Int, chord: Chord): Boolean {
    // A major chord or a seventh on a minor home's own root is its parallel major, never a secondary dominant: read as
    // leading into the fourth, the tonic C7 of a jazz blues would lift the minor family over Mixolydian.
    if (chord.root == root && MINOR_THIRD in home.intervals) return false
    val notes = Scales.pitchClasses(home, root)
    val intervals = chord.intervals.map { it.mod(12) }
    val isDominant = chord.isMajorOrDominant || chord.isDominantSeventh
    val isDiminished = MINOR_THIRD in intervals && FLAT_FIFTH in intervals && FIFTH !in intervals
    return isDominant && (chord.root + FOURTH).mod(12) in notes || isDiminished && (chord.root + 1).mod(12) in notes
}

/**
 * Every root [parts] play, the one they are most likely heard in first. A root scores one for each chord on it but a
 * dominant seventh (a chord that leaves, rarely a tonic), one for the first chord and one for the last, and two for
 * every cadence into it, a chord with a major third a fifth above it followed by a chord on it, each part read as a
 * loop too, since a section is repeated. A cadence into a dominant seventh is no cadence home (All of Me's `E7 A7`).
 *
 * A blues's I7 is the exception, a tonic seventh: a part's first chord where that is a dominant seventh, else its most
 * played dominant sevenths, where nothing on the root a fourth above them is played but as a seventh, so the seventh
 * leaves to nowhere or to the blues's IV7. It scores as any chord, and so does a cadence into it. Its first-chord rule
 * keeps the shuffle `E7 A7 B7 A7` on E, and the target rule the pickup `D7 G C D7` on G.
 *
 * Ties go to the first chord's root, then to more chords that are not a dominant seventh, then to the last one's.
 */
internal fun rankedRoots(parts: List<SongPart>): List<SpelledNote> {
    val played = parts.flatMap { it.roots }
    val first = played.first().pitchClass
    val last = played.last().pitchClass
    val scores = mutableMapOf<Int, Int>()
    val counts = mutableMapOf<Int, Int>()
    parts.forEach { part ->
        val plays = part.chords.groupingBy { it }.eachCount()
        val candidates = if (part.chords.first().isDominantSeventh) {
            setOf(part.chords.first())
        } else {
            plays.filter { (chord, count) -> chord.isDominantSeventh && count == plays.values.max() }.keys
        }
        val tonicSevenths = candidates.filter { seventh ->
            part.chords.none { it.root == (seventh.root + FOURTH).mod(12) && !it.isDominantSeventh }
        }.toSet()
        part.chords.forEachIndexed { index, chord ->
            if (!chord.isDominantSeventh || chord in tonicSevenths) {
                scores[chord.root] = (scores[chord.root] ?: 0) + 1
                counts[chord.root] = (counts[chord.root] ?: 0) + 1
            }
            val next = part.chords[(index + 1) % part.chords.size]
            if (MAJOR_THIRD in chord.intervals && (chord.root - next.root).mod(12) == FIFTH && (!next.isDominantSeventh || next in tonicSevenths)) {
                scores[next.root] = (scores[next.root] ?: 0) + CADENCE_SCORE
            }
        }
    }
    scores[first] = (scores[first] ?: 0) + 1
    scores[last] = (scores[last] ?: 0) + 1
    return played.distinctBy { it.pitchClass }.sortedWith(
        compareByDescending<SpelledNote> { scores[it.pitchClass] ?: 0 }
            .thenByDescending { it.pitchClass == first }
            .thenByDescending { counts[it.pitchClass] ?: 0 }
            .thenByDescending { it.pitchClass == last },
    )
}

private const val MINOR_THIRD = 3
private const val MAJOR_THIRD = 4
private const val FOURTH = 5
private const val FLAT_FIFTH = 6
private const val FIFTH = 7
private const val CADENCE_SCORE = 2
private val major = Scales.fromId("major")!!
private val naturalMinor = Scales.fromId("natural_minor")!!
