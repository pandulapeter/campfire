/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import com.pandulapeter.campfire.presentation.ui.songView.model.ChordDiagrams
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.mapper.toChordNotation
import com.pandulapeter.campfire.presentation.ui.instrument.chordTuning
import com.pandulapeter.campfire.presentation.ui.instrument.instrumentTuning
import com.pandulapeter.campfire.presentation.ui.instrument.storedChordShapes
import com.pandulapeter.campfire.presentation.ui.preferences.SongSections

/**
 * What the dock's diagrams of every page are drawn with, null where the diagrams are off. One for the whole pager, since
 * nothing in it belongs to one song: the instrument, the tuning, and the player's shapes are the same wherever a song is
 * read. An instrument without shapes still gets one, with no tuning to draw in, so that the dock keeps the instrument
 * pill that changes it; so do the diagrams switched off while the chords are on and the dock's card holds the tuner
 * ([isTunerInCard]), the switch taking the diagrams rather than the tuner and the instrument it listens for.
 */
@Composable
internal fun rememberChordDiagrams(
    userPreferences: UserPreferences?,
    isTunerInCard: Boolean,
): ChordDiagrams? {
    val preferences = userPreferences?.takeIf { it.areChordsEnabled } ?: return null
    val areDiagramsShown = SongSections.of(preferences).areDiagramsShown
    if (!areDiagramsShown && !isTunerInCard) return null
    val chordTuning = preferences.chordTuning()?.takeIf { areDiagramsShown }
    val storedChordShapes = chordTuning?.let { preferences.storedChordShapes(it) }.orEmpty()
    val instrument = preferences.instrument
    val instrumentTuning = preferences.instrumentTuning
    val notation = preferences.chordSpelling.notation.toChordNotation()
    return remember(chordTuning, storedChordShapes, instrument, instrumentTuning, notation) {
        ChordDiagrams(
            tuning = chordTuning,
            instrument = instrument,
            instrumentTuning = instrumentTuning,
            storedShapes = storedChordShapes,
            notation = notation,
        )
    }
}
