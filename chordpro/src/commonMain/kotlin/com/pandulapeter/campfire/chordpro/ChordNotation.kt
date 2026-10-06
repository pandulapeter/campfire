/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro

/**
 * A way of writing the notes of a chord down. [STANDARD] is the one every file is stored in and the one the model
 * works in; every other notation is only ever a way of showing that, or of typing it, and [ChordProNotation] converts
 * between the two at exactly those two boundaries.
 *
 * A text said to be in [STANDARD] is still read tolerantly: an `H` anywhere in it marks it as German, since that letter
 * is no note of the standard notation and a file written before the library had one notation may still hold it, and a
 * Latin name is read as the chord it names wherever it stands, since no Latin name is also a standard one. A text said
 * to be in German is read as German, whatever it looks like, which is what makes the conversion of what somebody typed
 * unambiguous where guessing could not be: a German chart in a flat key never needs an `H`, and its `B` is a B flat all
 * the same.
 *
 * [NASHVILLE] and [ROMAN] are not namings of notes at all but of the steps of a key, so they are only ever shown: a text
 * said to be in either is read as [STANDARD], and nothing is ever converted into them but a parsed song.
 */
enum class ChordNotation {

    /** `C D E F G A B`, with `#` and `b`: the notation of the ChordPro format, and of every file the app writes. */
    STANDARD,

    /** The note [STANDARD] writes `B` is written `H`, and its `Bb` is written `B`; nothing else changes. */
    GERMAN,

    /** The notes are named `Do Re Mi Fa Sol La Si`, with `#` and `b`; the quality and the bass note follow as written. */
    LATIN,

    /**
     * Every chord is the number of the step of the song's key it stands on, `1` to `7` with a `b` or a `#` for the
     * steps between, a minor chord marked with a `-` and every other quality kept as written: `1 4 5 6-`.
     */
    NASHVILLE,

    /**
     * The steps of [NASHVILLE] as Roman numerals, the quality in the case of the numeral: `I IV V vi`, a diminished
     * chord `vii°`, a half-diminished one `viiø7`, an augmented one `I+`. A bass note is still an Arabic step (`V/7`).
     */
    ROMAN,
    ;

    /** Whether the chords are counted from the song's key rather than named, which only a parsed song can be shown in. */
    val isNumbering get() = this == NASHVILLE || this == ROMAN
}
