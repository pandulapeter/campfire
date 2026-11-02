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
import com.pandulapeter.campfire.chordpro.model.ChordProBlock

/**
 * A [ScaleGroup] with the chords its sections play, in order, each as written ([names]), by its root as spelled
 * ([rootNames]), and as notes without its bass ([chords]), the three lists of one length, and its [parts].
 */
internal class PlayedGroup(
    val group: ScaleGroup,
    val rootPitchClass: Int,
    val names: List<String>,
    val rootNames: List<String>,
    val chords: List<Chord>,
    val parts: List<PlayedPart>,
)

/**
 * Sections of a [PlayedGroup] played together, a section with its continuations or a chorus with its recalls.
 *
 * @property plays How many times the song plays them: once for the section and once for each recall.
 * @property names The chords they play, as written.
 * @property chords The chords they play, in order, as notes without their bass.
 * @property centre The root their own chords are heard in, best scored by `SongScaleGroups`, and [centreName] it spelled.
 */
internal class PlayedPart(
    val blocks: List<ChordProBlock>,
    val plays: Int,
    val names: Set<String>,
    val chords: List<Chord>,
    val centre: Int,
    val centreName: String,
)
