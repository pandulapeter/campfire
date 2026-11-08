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

import com.pandulapeter.campfire.data.model.preferences.UserPreferences

/**
 * What the song cards of a list say after the artist, within the features switched on: the key, which goes with the
 * chords ([shouldShowChords]), since the chords switched off take them out of the viewer and the key is the shortest way
 * of writing them down; the tempo, which is what the click plays, so it goes with the metronome; and, on a setlist's
 * cards alone, where a set is timed, the duration.
 */
internal data class SongCardOptions(
    val shouldShowChords: Boolean,
    val isTempoShown: Boolean,
    val isDurationShown: Boolean,
    val isCoverArtEnabled: Boolean,
    val chordSpelling: UserPreferences.ChordSpelling,
) {
    companion object {
        /** The cards of the Songs screen, where a song is found. */
        fun forSongs(userPreferences: UserPreferences?) = of(userPreferences = userPreferences, isDurationShown = false)

        /** The cards of the Setlists screen, where a set is timed. */
        fun forSetlists(userPreferences: UserPreferences?) = of(userPreferences = userPreferences, isDurationShown = true)

        private fun of(userPreferences: UserPreferences?, isDurationShown: Boolean) = SongCardOptions(
            shouldShowChords = userPreferences?.areChordsEnabled != false,
            isTempoShown = userPreferences?.isMetronomeEnabled != false,
            isDurationShown = isDurationShown,
            isCoverArtEnabled = userPreferences?.isCoverArtEnabled == true,
            chordSpelling = userPreferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default,
        )
    }
}
