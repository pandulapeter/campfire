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
 * The notes a chord name stands for, as pitch classes: what is played, whatever the name's spelling. `C#m7`, `Dbm7`
 * and `C#min7` are one [Chord].
 *
 * @property root The root's pitch class, 0 for C up to 11 for B.
 * @property intervals The notes in semitones above the root, folded into one octave, sorted and each once; the root's
 *   `0` is among them unless the name leaves it out (`no1`).
 * @property bass The pitch class of the note after the slash of a slash chord, or null where there is none or it is
 *   the root.
 */
data class Chord(
    val root: Int,
    val intervals: List<Int>,
    val bass: Int? = null,
) {

    /**
     * The chord as one string, the same for every spelling of it: the root as a sharp, the intervals and the bass
     * (`C#:0.3.7.10`, `D:0.4.7/F#`). What a player's choice of shape is stored under, so it has to stay stable.
     */
    val id get() = sharpNames[root] + ":" + intervals.joinToString(".") + (bass?.let { "/" + sharpNames[it] } ?: "")

    /** Every pitch class the chord sounds, the bass included. */
    val pitchClasses get() = (intervals.map { (root + it) % 12 } + listOfNotNull(bass)).toSet()

    private companion object {
        val sharpNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    }
}
