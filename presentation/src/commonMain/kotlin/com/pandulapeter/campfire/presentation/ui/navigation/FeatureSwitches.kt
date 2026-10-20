/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.navigation

import com.pandulapeter.campfire.data.model.preferences.UserPreferences

/**
 * The Features switches that take a screen out of the app, see [CampfireDestination.isEnabled].
 *
 * @property areSongsEnabled Whether the Songs tab is there, and with it every screen a song is read or edited on. Off, the
 *   app opens on the first tool switched on rather than on the songs, see [CampfireDestination.TopLevel.home].
 * @property areSetlistsEnabled Whether the Setlists tab is there, which needs the songs as well: a setlist is a list of
 *   them.
 * @property isChordLibraryEnabled Whether the Chords tab is there, which follows the Chord diagrams switch: the tab is
 *   those diagrams, every one of them, and with the chords switched off there is nothing to finger either.
 * @property areScalesEnabled Whether the Scales tab is there, and with it the scales a chord sheet suggests: a switch of
 *   its own, since a scale is played on any instrument, chords or not.
 * @property areScaleSuggestionsEnabled Whether songs and chord sheets suggest scales, which follows the Scale suggestions
 *   switch, disabled by the Scales switch, as the chord diagrams follow theirs.
 */
internal data class FeatureSwitches(
    val areSongsEnabled: Boolean,
    val areSetlistsEnabled: Boolean,
    val isMetronomeEnabled: Boolean,
    val isTunerEnabled: Boolean,
    val isChordLibraryEnabled: Boolean,
    val areScalesEnabled: Boolean,
    val areScaleSuggestionsEnabled: Boolean,
) {

    companion object {

        /** The switches as [preferences] have them, every one on where nothing has been read. */
        fun of(preferences: UserPreferences?) = FeatureSwitches(
            areSongsEnabled = preferences?.areSongsEnabled != false,
            areSetlistsEnabled = preferences?.areSongsEnabled != false && preferences?.areSetlistsEnabled != false,
            isMetronomeEnabled = preferences?.isMetronomeEnabled != false,
            isTunerEnabled = preferences?.isTunerEnabled != false,
            isChordLibraryEnabled = preferences?.areChordsEnabled != false && preferences?.areChordDiagramsEnabled != false,
            areScalesEnabled = preferences?.areScalesEnabled != false,
            areScaleSuggestionsEnabled = preferences?.areScalesEnabled != false && preferences?.areScaleSuggestionsEnabled != false,
        )
    }
}
