/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.useCases

import com.pandulapeter.campfire.data.model.domain.UserPreferences

interface ConvertChordProTextNotationUseCase {

    /**
     * Rewrites the chords of a ChordPro document written in [from] in [to], leaving the rest of the text exactly as it
     * was. It is how the editor shows a file in the reader's notation and how what is typed there reaches the file,
     * which is always in [UserPreferences.Notation.STANDARD].
     *
     * A text in the standard notation is still read as German where it uses an `H` chord, which no file the app writes
     * does but one written before every file was in one notation may; a text in German is read as German, since that
     * is what its writer typed it in; and a Latin chord is read as the chord it names in any text, since no Latin name
     * is a standard one. Converted to the standard notation, chords are written with ASCII accidentals, so converting a
     * file from the standard notation to itself is what brings such a file into it, and leaves any other one unchanged.
     *
     * A numbering ([UserPreferences.Notation.isNumbering]) is never written into a text, nor read out of one: either
     * side being one is the standard notation.
     */
    operator fun invoke(text: String, from: UserPreferences.Notation, to: UserPreferences.Notation): String
}
