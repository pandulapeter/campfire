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

import com.pandulapeter.campfire.chordpro.model.ChordProBlock

/**
 * What [fitOf] answers: the [scales] on [root], the [home] scale that decided them, and the chords it leaves out.
 */
internal class Fit(
    val root: SpelledNote,
    val scales: List<Scale>,
    val level: Level,
    val home: Scale,
    val exceptions: List<ScaleException> = emptyList(),
) {
    /** Whether this is a rock riff's fit, led by the minor pentatonic rather than its home. */
    val isRockRiff get() = home != scales.first()

    fun playedGroupOf(parts: List<SongPart>) = PlayedGroup(
        group = ScaleGroup(parts.flatMap { it.blocks }, root.name, scales, exceptions),
        rootPitchClass = root.pitchClass,
        names = parts.flatMap { it.names },
        rootNames = parts.flatMap { part -> part.roots.map { it.name } },
        chords = parts.flatMap { it.chords },
        parts = parts.map { part ->
            val centre = rankedRoots(listOf(part)).first()
            PlayedPart(
                blocks = part.blocks,
                // The rest of a section a `{transpose}` cut is a part of its own, played once like any other.
                plays = part.blocks.withIndex().count { (index, block) -> index == 0 || block !is ChordProBlock.Section || !block.isContinuation },
                names = part.names.toSet(),
                chords = part.chords.toList(),
                centre = centre.pitchClass,
                centreName = centre.name,
            )
        },
    )
}

/** How sure a fit is of a part's key, see [fitOf]. */
internal enum class Level { TONAL, LOOSE, THIN, PASSING }
