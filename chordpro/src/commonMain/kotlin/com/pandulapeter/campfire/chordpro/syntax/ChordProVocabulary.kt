/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.syntax

/** The ChordPro words and characters several objects of this module read, spelled once. */
internal object ChordProVocabulary {

    /** What a source comment line starts with, outside an environment handed to another program. */
    const val SOURCE_COMMENT = "#"

    /** What a bracket's content starts with where it is an annotation rather than a chord (`[*softly]`). */
    const val ANNOTATION_MARKER = "*"

    const val BRACKET_OPEN = '['
    const val BRACKET_CLOSE = ']'

    /** The prefix of a custom directive's name (`{x_source: …}`). */
    const val CUSTOM_PREFIX = "x_"

    const val META = "meta"
    const val KEY = "key"
    const val TRANSPOSE = "transpose"
    const val TEMPO = "tempo"
    const val TIME = "time"

    /** The environments whose lines are tablature and a chord grid, by the name they are opened with. */
    const val TAB = "tab"
    const val GRID = "grid"

    /** The signs that may follow a note letter: the ASCII ones a file is written with and the typographic ones it may hold. */
    const val ACCIDENTALS = "#b♯♭"

    /** The semitones of an octave. */
    const val NOTE_COUNT = 12
}
