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

import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.cancel
import com.pandulapeter.campfire.presentation.resources.ic_clear
import com.pandulapeter.campfire.presentation.resources.settings_sync_connecting
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.ActionListItem
import org.jetbrains.compose.resources.painterResource

/** A connection waiting for its consent page, and the way out of it: the Sync section's, and the Sharing tab's. */
@Composable
internal fun ConnectingSyncSettings(viewModel: CampfireViewModel) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(stringResource(Res.string.settings_sync_connecting)) },
    )
    // The way out. Each platform does try to notice that the browser was closed, but a consent page is somewhere
    // the app cannot see, so this state must never be one the user has to restart the app to leave.
    ActionListItem(
        title = stringResource(Res.string.cancel),
        icon = painterResource(Res.drawable.ic_clear),
        isEmphasized = false,
        onClick = viewModel.syncController::cancelSyncConnection,
    )
}
