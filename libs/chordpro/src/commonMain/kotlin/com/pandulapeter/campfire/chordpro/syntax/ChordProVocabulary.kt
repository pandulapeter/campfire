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
public object ChordProVocabulary {

    /** What a source comment line starts with, outside an environment handed to another program. */
    internal const val SOURCE_COMMENT = "#"

    /** What a bracket's content starts with where it is an annotation rather than a chord (`[*softly]`). */
    public const val ANNOTATION_MARKER: String = "*"

    internal const val BRACKET_OPEN = '['
    internal const val BRACKET_CLOSE = ']'

    /** The prefix of a custom directive's name (`{x_source: …}`). */
    internal const val CUSTOM_PREFIX = "x_"

    internal const val META = "meta"
    /** The name of the directive giving a song its key (`{key: Am}`). */
    public const val KEY: String = "key"
    internal const val TRANSPOSE = "transpose"
    internal const val TEMPO = "tempo"
    internal const val TIME = "time"

    /** The directive colouring the chords from where it stands on (`{chordcolour: red}`); empty, back to the reader's. */
    internal const val CHORD_COLOUR = "chordcolour"

    /** The environments whose lines are tablature and a chord grid, by the name they are opened with. */
    internal const val TAB = "tab"
    internal const val GRID = "grid"

    /** The environment of a tune in ABC notation, which the app draws as staff notation. */
    internal const val ABC = "abc"

    /** The signs that may follow a note letter: the ASCII ones a file is written with and the typographic ones it may hold. */
    internal const val ACCIDENTALS = "#b♯♭"

    /** The semitones of an octave. */
    internal const val NOTE_COUNT = 12
}
