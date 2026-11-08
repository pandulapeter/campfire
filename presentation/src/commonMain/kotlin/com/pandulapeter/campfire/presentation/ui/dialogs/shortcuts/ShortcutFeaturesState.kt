/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.shortcuts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.ShortcutFeatures
import com.pandulapeter.campfire.presentation.ui.state.PreferencesController

/** The features the shortcuts listed depend on, as [preferencesController] has them, for the sheet and the shells' lists. */
@Composable
internal fun shortcutFeatures(preferencesController: PreferencesController): ShortcutFeatures {
    val userPreferences by preferencesController.userPreferences.collectAsStateWithLifecycle()
    val isReadOnly by preferencesController.isPerformanceModeEnabled.collectAsStateWithLifecycle()
    return ShortcutFeatures(
        areSongsEnabled = userPreferences?.areSongsEnabled != false,
        isMetronomeEnabled = userPreferences?.isMetronomeEnabled != false,
        isReadOnly = isReadOnly,
    )
}
