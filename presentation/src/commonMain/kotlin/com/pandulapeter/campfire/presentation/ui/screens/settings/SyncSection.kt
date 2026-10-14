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
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.settings_group_sync
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsSection

/**
 * Collects the sync state itself, so that a run reporting every file it moves recomposes this section and no other.
 * It is the first section of the library's tab, since it decides where the library lives beyond this device.
 */
@Composable
internal fun SyncSection(
    viewModel: CampfireViewModel,
) = SettingsSection(title = stringResource(Res.string.settings_group_sync)) {
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    SyncSettings(viewModel = viewModel, syncState = syncState)
}
