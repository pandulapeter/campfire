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
 * @property sections The sections, in the order the song plays them, the very instances of the song's blocks, so that
 *   a viewer heads them the way it heads them on the page.
 * @property rootName The root every scale is played on, a note in the standard notation.
 * @property scales The scales on [rootName] to play over the sections' chords, the home scale that holds most of them
 *   first, then what [PlayableScales.over] suggests for the chords it holds, in its order.
 * @property exceptions The chords of [sections] the first of [scales] does not hold, each with what to play over it
 *   instead, the most played first; empty where it holds them all.
 */
public data class ScaleGroup(
    val sections: List<ChordProBlock.Section>,
    val rootName: String,
    val scales: List<Scale>,
    val exceptions: List<ScaleException> = emptyList(),
)
