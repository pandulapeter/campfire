/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.settings

import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.navigation.SettingsTab

/**
 * The settings screen's tabs: every one but those that could not work or change nothing. A build without sync has no
 * Sharing, and the Song and the Metronome tabs go with their features, as their pages leave the Preferences sheet.
 */
internal fun settingsTabs(
    hasSync: Boolean,
    userPreferences: UserPreferences?,
) = SettingsTab.entries.filter { tab ->
    when (tab) {
        SettingsTab.SHARING -> hasSync
        SettingsTab.SONG -> userPreferences?.areSongsEnabled != false
        SettingsTab.METRONOME -> userPreferences?.isMetronomeEnabled != false
        else -> true
    }
}
