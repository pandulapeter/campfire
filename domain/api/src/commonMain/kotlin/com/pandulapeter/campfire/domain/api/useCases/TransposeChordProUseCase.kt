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

import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.data.model.domain.UserPreferences

interface TransposeChordProUseCase {

    /**
     * @param accidentals Which spelling the chords are written with, from the user's preferences. Anything but
     *   [UserPreferences.Accidentals.ORIGINAL] is worth applying even for no semitones at all: it is a preference
     *   about how a song reads, not about what transposing does to it.
     */
    operator fun invoke(song: ChordProSong, semitones: Int, accidentals: UserPreferences.Accidentals): ChordProSong
}
