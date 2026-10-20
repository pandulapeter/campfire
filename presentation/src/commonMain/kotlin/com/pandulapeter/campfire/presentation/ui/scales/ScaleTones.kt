/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.scales

import com.pandulapeter.campfire.chordpro.scales.Scale

/**
 * The notes the scale sheet plays [scale] in from [root] (a pitch class, 0 for C), as MIDI note numbers: once up from
 * the root in the octave over middle C and on to the root an octave higher, where every instrument's notes are heard
 * clearly through a phone's speaker, whatever the instrument's own range.
 */
internal fun scaleToneNotes(scale: Scale, root: Int): List<Int> {
    val start = MIDDLE_C + root.mod(12)
    return scale.intervals.map { start + it } + (start + 12)
}

/**
 * How long the scale sheet's circle takes to reach a degree, and so how long each note waits for it after its degree is
 * shown: the note sounds as the circle arrives, as if the circle struck it, never while it is still on its way.
 */
internal const val SCALE_NOTE_LEAD_MILLIS = 90

private const val MIDDLE_C = 60
