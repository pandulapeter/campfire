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
}

/**
 * Every root [parts] play, the one they are most likely heard in first. A root scores one for each chord on it but a
 * dominant seventh (a chord that leaves, rarely a tonic), one for the first chord and one for the last, and two for
 * every cadence into it, a chord with a major third a fifth above it followed by a chord on it, each part read as a
 * loop too, since a section is repeated. Ties go to more chords that are not a dominant seventh, then the first
 * chord's root, then the last one's.
 */
internal fun rankedRoots(parts: List<SongPart>): List<SpelledNote> {
    val played = parts.flatMap { it.roots }
    val first = played.first().pitchClass
    val last = played.last().pitchClass
    val scores = mutableMapOf<Int, Int>()
    val counts = mutableMapOf<Int, Int>()
    parts.forEach { part ->
        part.chords.forEachIndexed { index, chord ->
            if (!chord.isDominantSeventh) {
                scores[chord.root] = (scores[chord.root] ?: 0) + 1
                counts[chord.root] = (counts[chord.root] ?: 0) + 1
            }
            val next = part.chords[(index + 1) % part.chords.size]
            if (MAJOR_THIRD in chord.intervals && (chord.root - next.root).mod(12) == FIFTH) {
                scores[next.root] = (scores[next.root] ?: 0) + CADENCE_SCORE
            }
        }
    }
    scores[first] = (scores[first] ?: 0) + 1
    scores[last] = (scores[last] ?: 0) + 1
    return played.distinctBy { it.pitchClass }.sortedWith(
        compareByDescending<SpelledNote> { scores[it.pitchClass] ?: 0 }
            .thenByDescending { counts[it.pitchClass] ?: 0 }
            .thenByDescending { it.pitchClass == first }
            .thenByDescending { it.pitchClass == last },
    )
}

private const val MAJOR_THIRD = 4
private const val FIFTH = 7
private const val CADENCE_SCORE = 2
