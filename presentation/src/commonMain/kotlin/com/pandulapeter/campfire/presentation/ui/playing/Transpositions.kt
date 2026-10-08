/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.playing

/**
 * The transposition of every song the UI can currently show, from both places one can be stored. Looked up by
 * how the song was opened rather than by a composite key, so callers cannot accidentally mix the two up.
 */
data class Transpositions(
    private val library: Map<String, Int> = emptyMap(),
    private val bySetlist: Map<String, Map<String, Int>> = emptyMap(),
) {

    /** Wrapped on the way out too, since a file or a preferences document may hold any amount (see [wrapTransposition]). */
    operator fun get(songFileName: String, setlistFileName: String?): Int = wrapTransposition(
        if (setlistFileName == null) {
            library[songFileName] ?: 0
        } else {
            bySetlist[setlistFileName]?.get(songFileName) ?: 0
        }
    )
}

private const val SEMITONES_PER_OCTAVE = 12

/**
 * [semitones] as the one amount between -5 and +6 that moves the chords to the same names: twelve semitones up
 * or down is the same song, so +7 reads as -5 and -6 as +6, the stepper steps around the octave rather than into
 * an end, and a label never claims more than half an octave either way.
 */
fun wrapTransposition(semitones: Int) = (semitones + 5).mod(SEMITONES_PER_OCTAVE) - 5
