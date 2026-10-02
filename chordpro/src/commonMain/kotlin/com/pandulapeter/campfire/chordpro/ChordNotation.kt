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
 * is no note of the standard notation and a file written before the library had one notation may still hold it. A text
 * said to be in any other notation is read as that notation, whatever it looks like, which is what makes the
 * conversion of what somebody typed unambiguous where guessing could not be: a German chart in a flat key never needs
 * an `H`, and its `B` is a B flat all the same.
 */
enum class ChordNotation {

    /** `C D E F G A B`, with `#` and `b`: the notation of the ChordPro format, and of every file the app writes. */
    STANDARD,

    /** The note [STANDARD] writes `B` is written `H`, and its `Bb` is written `B`; nothing else changes. */
    GERMAN,
}
