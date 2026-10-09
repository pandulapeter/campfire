/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.tuner

import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProNotation

private val SHARP_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

/**
 * The name of a MIDI note in the reader's chord [notation], with sharps, as the chords on a page write them (`F#`, and
 * `H` for B in German); a numbering names it in letters, since a note on its own counts from no key.
 */
internal fun noteName(note: Int, notation: ChordNotation): String = ChordProNotation.shownName(SHARP_NAMES[note.mod(SEMITONES)], notation)

/** The octave of a MIDI note in scientific numbering, C4 being middle C. */
internal fun noteOctave(note: Int): Int = note.floorDiv(SEMITONES) - 1

/** The note with its octave, `E2`, the way the strings are named. */
internal fun noteNameWithOctave(note: Int, notation: ChordNotation) = "${noteName(note, notation)}${noteOctave(note)}"

private const val SEMITONES = 12
