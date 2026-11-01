/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.setlists

import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.data.model.setlist.Setlist

/**
 * The entry a song gets when it is put into a setlist: played the way the library plays it, so its transposition, tempo,
 * and capo from [preferences] are copied into the entry, since they are how the band plays the song and a song moved
 * into a set should not change key or speed on the way. A copy taken once: from then on the entry and the library are
 * changed apart, as every entry override is. Where the library has none, the entry has none either rather than the
 * song's own `{tempo}` or `{capo}` written down, so that a later edit of the file still reaches the setlist.
 */
internal fun newSetlistEntry(songFileName: String, preferences: UserPreferences?) = Setlist.Entry(
    songFileName = songFileName,
    transposition = preferences?.transpositions?.get(songFileName) ?: 0,
    tempo = preferences?.tempos?.get(songFileName),
    capo = preferences?.capos?.get(songFileName),
)
