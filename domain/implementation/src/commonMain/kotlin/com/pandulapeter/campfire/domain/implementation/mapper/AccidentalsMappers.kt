/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.mapper

import com.pandulapeter.campfire.data.model.domain.UserPreferences

/**
 * The preference as `:chordpro` takes it: null for the spelling the song itself asks for, which is what
 * `ChordProTransposer` works out from the key. `:chordpro` depends on nothing, so it knows no preferences.
 */
internal fun UserPreferences.Accidentals.toPreferFlats(): Boolean? = when (this) {
    UserPreferences.Accidentals.ORIGINAL -> null
    UserPreferences.Accidentals.FLATS -> true
    UserPreferences.Accidentals.SHARPS -> false
}
