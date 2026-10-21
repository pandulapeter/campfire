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

/** The parts [SongScaleGroups] has put together so far, with the fit they share. */
internal class PartGroup(val parts: MutableList<SongPart>, var fit: Fit) {
    /** A home leaving out one of at most three chords, see [SongScaleGroups.of]; a rock riff, which has no exceptions, is no guess. */
    val isGuess get() = isGuessOf(fit, parts.flatMap { it.chords })

    fun isTonicVamp(root: SpelledNote) = parts.all { it.isTonicVamp(root) }
}

/** Whether every chord of the part is on [root], a vamp that says nothing of the mode. */
internal fun SongPart.isTonicVamp(root: SpelledNote) = chords.all { it.root == root.pitchClass }

/** Whether a part playing [chords] fit as [fit] is a guess, see [PartGroup.isGuess]. */
internal fun isGuessOf(fit: Fit, chords: List<Chord>) =
    (fit.level == Level.LOOSE || fit.level == Level.THIN) && fit.exceptions.isNotEmpty() && chords.distinct().size <= MAX_GUESSED_CHORDS

/**
 * These groups, in play order, each joining the first before it on its root that has a part of the same chords, every
 * chord of one being the other's, the fit of the one playing more chords answering both: a part's own plays may tip it to
 * another mode (an intro holding the ♭VII four bars reads as Mixolydian, the chorus of the same chords as major with the
 * ♭VII its exception), but the same chords are one answer, and the intro would otherwise be a row no moment ever shows.
 */
internal fun List<PartGroup>.sameChordsJoined(order: List<SongPart>) = fold(mutableListOf<PartGroup>()) { joined, group ->
    val chords = group.parts.map { it.chords.toSet() }
    val match = joined.firstOrNull { other ->
        other.fit.root.pitchClass == group.fit.root.pitchClass && other.fit.isRockRiff == group.fit.isRockRiff &&
            listOf(other, group).none { it.fit.level == Level.PASSING } && other.parts.any { it.chords.toSet() in chords } &&
            listOf(other, group).sortedBy { it.chordCount }.let { (smaller, larger) -> larger.distinctChords.containsAll(smaller.distinctChords) }
    }
    if (match == null) {
        joined += group
    } else {
        if (group.chordCount > match.chordCount) match.fit = group.fit
        match.parts += group.parts
        match.parts.sortBy(order::indexOf)
    }
    joined
}

private val PartGroup.chordCount get() = parts.sumOf { it.chords.size }

private val PartGroup.distinctChords get() = parts.flatMapTo(mutableSetOf()) { it.chords }

private const val MAX_GUESSED_CHORDS = 3
