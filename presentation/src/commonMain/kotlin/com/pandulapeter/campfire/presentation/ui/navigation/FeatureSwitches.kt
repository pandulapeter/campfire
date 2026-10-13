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

import com.pandulapeter.campfire.data.model.domain.UserPreferences

/**
 * The Features switches that take a screen out of the app, see [CampfireDestination.isEnabled].
 *
 * @property isChordLibraryEnabled Whether the Chords tab is there, which follows the Chord diagrams switch: the tab is
 *   those diagrams, every one of them, and with the chords switched off there is nothing to finger either.
 */
internal data class FeatureSwitches(
    val areSetlistsEnabled: Boolean,
    val isMetronomeEnabled: Boolean,
    val isTunerEnabled: Boolean,
    val isChordLibraryEnabled: Boolean,
) {

    companion object {

        /** The switches as [preferences] have them, every one on where nothing has been read. */
        fun of(preferences: UserPreferences?) = FeatureSwitches(
            areSetlistsEnabled = preferences?.areSetlistsEnabled != false,
            isMetronomeEnabled = preferences?.isMetronomeEnabled != false,
            isTunerEnabled = preferences?.isTunerEnabled != false,
            isChordLibraryEnabled = preferences?.areChordsEnabled != false && preferences?.areChordDiagramsEnabled != false,
        )
    }
}
