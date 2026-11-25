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

import com.pandulapeter.campfire.data.model.preferences.UserPreferences

/**
 * Which of the tools song details has: its click ([isMetronomeEnabled]), the dock's way to the tuner ([isTunerEnabled]),
 * and whether the menu holds the tuner instead ([isTunerInMenu]).
 */
internal data class SongDetailsTools(
    val isMetronomeEnabled: Boolean,
    val isTunerEnabled: Boolean,
    val isTunerInMenu: Boolean,
)

/**
 * The tools of song details under [userPreferences]: the Metronome and Tuner features, less what the Song page's Dock
 * section leaves out of this screen alone (the tabs keep theirs). The dock is there wherever the chords
 * ([shouldShowChords]) or the click are, and where it holds no tuner the menu does, so the tuner is never out of reach.
 */
internal fun songDetailsToolsOf(userPreferences: UserPreferences?, shouldShowChords: Boolean): SongDetailsTools {
    val isMetronomeEnabled = userPreferences?.isMetronomeEnabled != false && userPreferences?.isDockMetronomeEnabled != false
    val isTunerFeatureEnabled = userPreferences?.isTunerEnabled != false
    val isTunerEnabled = isTunerFeatureEnabled && userPreferences?.isDockTunerEnabled != false
    return SongDetailsTools(
        isMetronomeEnabled = isMetronomeEnabled,
        isTunerEnabled = isTunerEnabled,
        isTunerInMenu = isTunerFeatureEnabled && !(isTunerEnabled && (shouldShowChords || isMetronomeEnabled)),
    )
}
