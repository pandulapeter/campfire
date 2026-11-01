/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.preferences

/**
 * How a song's chords are named under a capo ([UserPreferences.chordNames]): [SHAPES] as they are fingered above it
 * (`Em` at capo 2), the way a capo chart is written, or [SOUNDING] as they are heard (`F#m`), the way a player reading
 * along without a capo needs them. Only a song's chords: its key, the lists' keys, and the scales always name what
 * sounds, and the keyboard, which is fingered as it sounds, always reads [SOUNDING].
 */
enum class ChordNames(val id: String) {
    SHAPES("shapes"),
    SOUNDING("sounding"),
}
