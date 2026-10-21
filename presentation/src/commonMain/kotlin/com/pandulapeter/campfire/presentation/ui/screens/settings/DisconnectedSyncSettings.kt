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

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.data.model.sync.SyncProviderId
import com.pandulapeter.campfire.data.model.sync.SyncState
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_cloud
import com.pandulapeter.campfire.presentation.resources.settings_sync_connect_dropbox
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.ActionListItem
import com.pandulapeter.campfire.presentation.ui.settingsPage.AnimatedSettingsRow
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsMessage
import com.pandulapeter.campfire.presentation.ui.sync.connectionFailedMessage
import com.pandulapeter.campfire.presentation.ui.sync.rememberAuthorizationCompletionPage
import org.jetbrains.compose.resources.painterResource

/** The invitation to connect, with the reason the last attempt failed under it: the Sync section's, and the Sharing tab's. */
@Composable
internal fun ColumnScope.DisconnectedSyncSettings(
    viewModel: CampfireViewModel,
    syncState: SyncState,
) {
    val completionPage = rememberAuthorizationCompletionPage()
    ActionListItem(
        title = stringResource(Res.string.settings_sync_connect_dropbox),
        icon = painterResource(Res.drawable.ic_cloud),
        isEnabled = viewModel.syncController.syncProviders.contains(SyncProviderId.DROPBOX),
        onClick = { viewModel.connectSyncProvider(SyncProviderId.DROPBOX, completionPage) },
    )
    // Under the Connect row rather than above it, so that Connect is the first row of its stage with or without a
    // reason: the first row of the connecting stage is text that takes no taps, where it would otherwise be the Cancel
    // that the second click of a double click lands on.
    AnimatedSettingsRow(value = (syncState as? SyncState.ConnectionFailed)?.reason) { reason ->
        SettingsMessage(
            text = stringResource(reason.connectionFailedMessage),
            isAnnounced = true,
        )
    }
}
