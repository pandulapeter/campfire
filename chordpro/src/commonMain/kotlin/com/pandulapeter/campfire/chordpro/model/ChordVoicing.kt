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

/**
 * An instrument a chord can be shown for.
 *
 * @property id What a stored choice and a preference name the instrument by, so it has to stay stable.
 * @property tuning The open strings as MIDI note numbers, in the order a chord chart draws them (the lowest string first
 *   on the guitar, the fourth string first on the ukulele, which is re-entrant and so not the lowest); empty for an
 *   instrument with no strings.
 */
enum class ChordInstrument(val id: String, val tuning: List<Int>) {
    GUITAR("guitar", listOf(40, 45, 50, 55, 59, 64)),
    UKULELE("ukulele", listOf(67, 60, 64, 69)),
    KEYBOARD("keyboard", emptyList());

    val isFretted get() = tuning.isNotEmpty()

    companion object {

        /** The instrument [id] names, or null for one this version does not know. */
        fun fromId(id: String) = entries.firstOrNull { it.id == id }
    }
}

/** One way of playing a chord. */
sealed interface ChordVoicing {

    /**
     * A shape on a fretted instrument.
     *
     * @property frets One per string, in [ChordInstrument.tuning]'s order: the fret it is stopped at, counted from the
     *   nut, 0 for an open string and null for a muted one.
     * @property fingers The finger on each string where the shape says (1 the index finger to 4 the little one, 0 for
     *   a string no finger stops), or null for a shape that does not say. A finger named on several strings is a barre.
     */
    data class Fretted(
        val frets: List<Int?>,
        val fingers: List<Int>? = null,
    ) : ChordVoicing

    /**
     * Keys pressed on a keyboard.
     *
     * @property notes The keys, in semitones above the C the diagram starts at, sorted.
     * @property bass The bass of a slash chord, below [notes] and drawn apart from them, or null.
     */
    data class Keys(
        val notes: List<Int>,
        val bass: Int? = null,
    ) : ChordVoicing
}
