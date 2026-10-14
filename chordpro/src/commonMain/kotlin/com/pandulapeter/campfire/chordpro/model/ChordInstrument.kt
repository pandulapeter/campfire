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
 *   on the guitar, the fourth string first on the ukulele, which is re-entrant and so not the lowest, and one per pair
 *   of strings on the mandolin, whose pairs are each tuned in unison, and the short fifth string first on the banjo, where
 *   it is strung); empty for an instrument with no strings.
 * @property playsChordOver Whether a chord is played on every string with whichever of its notes falls lowest, rather
 *   than over its bass or root, as on the ukulele, whose lowest string is not its fourth, and on the mandolin, whose
 *   four strings tuned in fifths put a chord's fifth or third under its root in the shapes everybody plays, and on the
 *   banjo, whose short fifth string is its highest. A slash chord is played as the chord over it there.
 * @property droneString The string, as an index into [tuning], that is only ever played open or muted: the banjo's short
 *   fifth string, which starts at the fifth fret and rings its open note under whatever the hand plays. It sounds in a
 *   shape only where its note is one of the chord's, and is never moved along the neck with the rest; null for an
 *   instrument whose strings all run the length of the neck.
 */
public enum class ChordInstrument(
    public val id: String,
    public val tuning: List<Int>,
    public val playsChordOver: Boolean = false,
    public val droneString: Int? = null,
) {
    GUITAR("guitar", listOf(40, 45, 50, 55, 59, 64)),
    UKULELE("ukulele", listOf(67, 60, 64, 69), playsChordOver = true),
    KEYBOARD("keyboard", emptyList()),
    MANDOLIN("mandolin", listOf(55, 62, 69, 76), playsChordOver = true),
    BANJO("banjo", listOf(67, 50, 55, 59, 62), playsChordOver = true, droneString = 0),
    ;

    public val isFretted: Boolean get() = tuning.isNotEmpty()

    internal companion object {

        /** The instrument [id] names, or null for one this version does not know. */
        internal fun fromId(id: String) = entries.firstOrNull { it.id == id }
    }
}
