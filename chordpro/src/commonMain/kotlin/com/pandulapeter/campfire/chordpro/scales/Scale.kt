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

/**
 * One scale of [Scales.all], in no key: the notes it is made of, counted from whatever root it is played on. Twelve-tone
 * equal temperament only, since nothing else can be drawn on a fret or a key.
 *
 * @property id Stable and lowercase (`dorian`): what a string resource and a dialog are keyed by, so never changed.
 * @property family The group it is browsed under.
 * @property intervals The notes in semitones above the root, sorted, starting at 0, each within the octave and once.
 * @property degrees One label per interval (`1`, `b3`, `#4`, `bb7`): what the formula shows and what spells each note's
 *   letter ([ScaleSpelling]). Every label names its interval.
 * @property modeOf The id of the scale this is a rotation of and the degree it starts on (Dorian is the major scale from
 *   its second degree), or null.
 * @property aliases Other names it is known by, in English.
 */
public data class Scale(
    val id: String,
    val family: ScaleFamily,
    val intervals: List<Int>,
    val degrees: List<String>,
    val modeOf: Pair<String, Int>? = null,
    val aliases: List<String> = emptyList(),
)
