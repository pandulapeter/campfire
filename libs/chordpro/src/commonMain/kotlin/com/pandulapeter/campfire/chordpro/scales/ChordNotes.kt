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

/** The pitch classes the chord plays, from 0 for C to 11 for B. */
internal val Chord.notes: Set<Int> get() = intervals.mapTo(mutableSetOf()) { (root + it).mod(12) }

/** Whether the chord has a major third and a minor seventh, the sound of a dominant seventh and its extensions. */
internal val Chord.isDominantSeventh get() = intervals.map { it.mod(12) }.let { MAJOR_THIRD in it && MINOR_SEVENTH in it }

/** Whether the chord is a major triad or a dominant seventh, nothing added: the chord a key's V and IV are played as. */
internal val Chord.isMajorOrDominant get() = intervals.map { it.mod(12) }.let { MAJOR_THIRD in it && it.all { interval -> interval in dominantSeventh } }

/** Whether the chord has a fifth and no third, a power or a suspended chord, which says nothing of major or minor. */
internal val Chord.hasNoThird get() = intervals.map { it.mod(12) }.let { MINOR_THIRD !in it && MAJOR_THIRD !in it && FIFTH in it }

/** Whether [scale] on [root] plays every note of [chord]. */
internal fun holdsChord(scale: Scale, root: Int, chord: Chord) = Scales.pitchClasses(scale, root).containsAll(chord.notes)

private const val MINOR_THIRD = 3
private const val MAJOR_THIRD = 4
private const val FIFTH = 7
private const val MINOR_SEVENTH = 10
private val dominantSeventh = setOf(0, MAJOR_THIRD, FIFTH, MINOR_SEVENTH)
