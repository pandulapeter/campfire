/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.mapper

import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.data.model.preferences.UserPreferences

/** The preference as `:chordpro` takes it, which depends on nothing and so knows no preferences. */
internal fun UserPreferences.Notation.toChordNotation() = when (this) {
    UserPreferences.Notation.STANDARD -> ChordNotation.STANDARD
    UserPreferences.Notation.GERMAN -> ChordNotation.GERMAN
    UserPreferences.Notation.LATIN -> ChordNotation.LATIN
    UserPreferences.Notation.NASHVILLE -> ChordNotation.NASHVILLE
    UserPreferences.Notation.ROMAN -> ChordNotation.ROMAN
}

/**
 * The preference as `:chordpro` takes it: null for the spelling the song itself asks for, which is what
 * `ChordProTransposer` works out from the key.
 */
internal fun UserPreferences.Accidentals.toPreferFlats(): Boolean? = when (this) {
    UserPreferences.Accidentals.ORIGINAL -> null
    UserPreferences.Accidentals.FLATS -> true
    UserPreferences.Accidentals.SHARPS -> false
}
