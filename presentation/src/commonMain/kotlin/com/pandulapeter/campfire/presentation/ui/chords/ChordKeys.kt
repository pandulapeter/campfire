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

/**
 * The keys a song's chords are in, in the standard notation: [home], the key the Chords section's cells are labelled
 * with their steps of ([withDegrees]) and headed by nothing, and [moved], the key each chord first played after a key
 * change is in, by its name as written (`SongKeys.keysOfChords`), which its cells are counted from and grouped under
 * ([groupedByKey]). A chord missing from [moved] is at [home].
 */
internal class ChordKeys(val home: String, val moved: Map<String, String>) {

    /** The key the chord written as [name] is counted from. */
    fun of(name: String) = moved[name] ?: home
}
