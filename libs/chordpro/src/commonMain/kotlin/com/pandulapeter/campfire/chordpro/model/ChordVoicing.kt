/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.model

/** One way of playing a chord. */
public sealed interface ChordVoicing {

    /**
     * A shape on a fretted instrument.
     *
     * @property frets One per string, in [ChordInstrument.tuning]'s order: the fret it is stopped at, counted from the
     *   nut, 0 for an open string and null for a muted one.
     * @property fingers The finger on each string where the shape says (1 the index finger to 4 the little one, 0 for
     *   a string no finger stops), or null for a shape that does not say. A finger named on several strings is a barre.
     */
    public data class Fretted(
        val frets: List<Int?>,
        val fingers: List<Int>? = null,
    ) : ChordVoicing

    /**
     * Keys pressed on a keyboard.
     *
     * @property notes The keys, in semitones above the C the diagram starts at, sorted.
     * @property bass The bass of a slash chord, below [notes] and drawn apart from them, or null.
     */
    public data class Keys(
        val notes: List<Int>,
        val bass: Int? = null,
    ) : ChordVoicing
}
