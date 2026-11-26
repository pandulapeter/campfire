/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.preferences

import com.pandulapeter.campfire.data.model.preferences.UserPreferences

/**
 * Whether the Dock section's Tuner switch changes anything under [userPreferences]: it needs the Tuner feature, and a
 * dock to put the tuner in, which song details has only with the chords or the song's click on. Without either the
 * song's menu holds the tuner whatever the switch says (`songDetailsToolsOf`), so the switch is disabled.
 */
internal fun isDockTunerSwitchEnabled(userPreferences: UserPreferences?): Boolean {
    val isSongClickShown = userPreferences?.isMetronomeEnabled != false && userPreferences?.isDockMetronomeEnabled != false
    return userPreferences?.isTunerEnabled != false && (userPreferences?.areChordsEnabled != false || isSongClickShown)
}
