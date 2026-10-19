/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.chords

import com.pandulapeter.campfire.data.model.preferences.UserPreferences

/**
 * The twelve keys the Chords and the Scales tabs list their chords and scales in, from C, in the standard notation, as
 * [accidentals] spells them: as a chart spells each key where it keeps the song's own accidentals (`C#` but `Eb`, `F#`
 * but `Bb`), and every one of them sharp or flat where the reader asked for that.
 */
internal fun libraryKeys(accidentals: UserPreferences.Accidentals) = when (accidentals) {
    UserPreferences.Accidentals.ORIGINAL -> listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")
    UserPreferences.Accidentals.SHARPS -> listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    UserPreferences.Accidentals.FLATS -> listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
}
