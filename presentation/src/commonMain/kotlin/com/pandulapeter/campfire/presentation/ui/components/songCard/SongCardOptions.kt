/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.songCard

import com.pandulapeter.campfire.data.model.preferences.CardDetails
import com.pandulapeter.campfire.data.model.preferences.UserPreferences

/**
 * What the song cards of a list say after the artist: what the reader picked for the list ([of]'s `details`,
 * `UserPreferences.songCardDetails` or `setlistCardDetails`), within the features switched on. The chords switched off
 * take them out of the viewer, and the key is the shortest way of writing them down; the tempo is what the click plays,
 * so unless the reader picked it, it goes with the metronome.
 */
internal data class SongCardOptions(
    val shouldShowChords: Boolean,
    val isKeyShown: Boolean,
    val isTempoShown: Boolean,
    val isDurationShown: Boolean,
    val isCoverArtEnabled: Boolean,
    val chordSpelling: UserPreferences.ChordSpelling,
) {
    companion object {
        fun of(userPreferences: UserPreferences?, details: CardDetails) = SongCardOptions(
            shouldShowChords = userPreferences?.areChordsEnabled != false,
            isKeyShown = details.isKeyShown,
            isTempoShown = details.isTempoShown(isMetronomeEnabled = userPreferences?.isMetronomeEnabled != false),
            isDurationShown = details.isDurationShown,
            isCoverArtEnabled = userPreferences?.isCoverArtEnabled == true,
            chordSpelling = userPreferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default,
        )
    }
}
