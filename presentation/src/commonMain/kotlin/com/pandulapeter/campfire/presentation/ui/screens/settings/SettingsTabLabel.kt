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

import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.settings_about
import com.pandulapeter.campfire.presentation.resources.settings_features
import com.pandulapeter.campfire.presentation.resources.settings_general
import com.pandulapeter.campfire.presentation.resources.settings_library
import com.pandulapeter.campfire.presentation.resources.settings_songs

/** What a tab of the settings screen is called. */
@Composable
internal fun SettingsTab.label() = stringResource(
    when (this) {
        SettingsTab.GENERAL -> Res.string.settings_general
        SettingsTab.FEATURES -> Res.string.settings_features
        SettingsTab.SONGS -> Res.string.settings_songs
        SettingsTab.LIBRARY -> Res.string.settings_library
        SettingsTab.ABOUT -> Res.string.settings_about
    }
)
