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

import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.settings_group_performance
import com.pandulapeter.campfire.presentation.resources.settings_read_only_mode
import com.pandulapeter.campfire.presentation.resources.settings_read_only_mode_description
import com.pandulapeter.campfire.presentation.resources.settings_read_only_mode_description_without_setlists
import com.pandulapeter.campfire.presentation.ui.components.SwitchListItem
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsSection

/**
 * Read only mode, above the Preferences' pages wherever they are shown, since it decides what the rest of the app is
 * still allowed to do: the screens it locks open the sheet it is in, so a switch flipped by accident is undone where it
 * was noticed.
 */
@Composable
internal fun PerformanceSection(
    isPerformanceModeEnabled: Boolean,
    areSetlistsEnabled: Boolean,
    onPerformanceModeChanged: (Boolean) -> Unit,
) = SettingsSection(title = stringResource(Res.string.settings_group_performance)) {
    SwitchListItem(
        title = stringResource(Res.string.settings_read_only_mode),
        // Reordering is a setlist's, so with the setlists off there is none to lock.
        description = stringResource(
            if (areSetlistsEnabled) Res.string.settings_read_only_mode_description else Res.string.settings_read_only_mode_description_without_setlists,
        ),
        isChecked = isPerformanceModeEnabled,
        onCheckedChange = onPerformanceModeChanged,
    )
}
