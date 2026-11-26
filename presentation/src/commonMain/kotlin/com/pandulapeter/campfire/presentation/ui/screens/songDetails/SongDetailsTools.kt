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

/** Which of the tools song details has: its click in the dock ([isMetronomeEnabled]) and the tuner in its menu ([isTunerInMenu]). */
internal data class SongDetailsTools(
    val isMetronomeEnabled: Boolean,
    val isTunerInMenu: Boolean,
)

/**
 * The tools of song details under [userPreferences]: the Metronome feature less what the Song page's Dock section leaves
 * out of this screen alone (the tab keeps it), and the Tuner feature, whose way in is always the song's menu (and the
 * Playing sheet's tuning row), never an import preview's, which has no tuner.
 */
internal fun songDetailsToolsOf(userPreferences: UserPreferences?, isImportPreview: Boolean) = SongDetailsTools(
    isMetronomeEnabled = userPreferences?.isMetronomeEnabled != false && userPreferences?.isDockMetronomeEnabled != false,
    isTunerInMenu = userPreferences?.isTunerEnabled != false && !isImportPreview,
)
