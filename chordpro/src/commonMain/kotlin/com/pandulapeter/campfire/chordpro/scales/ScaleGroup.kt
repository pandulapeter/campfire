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
 * Sections of a song that one set of scales fits, see [SongScaleGroups].
 *
 * @property blocks The sections, in the order the song plays them, each a [ChordProBlock.Section] or a
 *   [ChordProBlock.ChorusRecall], the very instances of the song's blocks, so that a viewer heads them the way it heads
 *   them on the page.
 * @property rootName The root every scale is played on, a note in the standard notation.
 * @property scales The scales on [rootName] to play over the sections' chords, the home scale that holds most of them
 *   first (a rock riff's minor pentatonic and blues before it), then what [PlayableScales.over] suggests for the chords
 *   it holds, in its order.
 * @property exceptions The chords of [blocks] the home scale does not hold and that are played often enough to change
 *   scale for, each with what to play over it instead, the most played first; empty where it holds them all, or the
 *   sections are a rock riff, whose chords the minor pentatonic is played over by design.
 */
public data class ScaleGroup(
    val blocks: List<ChordProBlock>,
    val rootName: String,
    val scales: List<Scale>,
    val exceptions: List<ScaleException> = emptyList(),
)
