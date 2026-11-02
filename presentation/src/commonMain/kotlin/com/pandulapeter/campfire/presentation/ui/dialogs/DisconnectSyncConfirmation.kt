/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.localization.textResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.settings_sync_disconnect
import com.pandulapeter.campfire.presentation.resources.settings_sync_disconnect_confirmation
import com.pandulapeter.campfire.presentation.resources.settings_sync_disconnect_pending_stops
import com.pandulapeter.campfire.presentation.resources.settings_sync_disconnect_shared_links
import com.pandulapeter.campfire.presentation.ui.share.ShareLinkController

/** Disconnecting the account sync runs against, which says what becomes of the links shared from it. */
@Composable
internal fun DisconnectSyncConfirmation(
    shareLinkController: ShareLinkController,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
    dialog: DialogType.DisconnectSync,
) = ConfirmationDialog(
    title = stringResource(Res.string.settings_sync_disconnect),
    // The shares stay in the cloud folder and keep serving their last copies, which nothing updates any more.
    text = textResource(Res.string.settings_sync_disconnect_confirmation, dialog.accountName) +
        if (shareLinkController.shares.collectAsStateWithLifecycle().value.isEmpty()) {
            ""
        } else {
            " " + stringResource(Res.string.settings_sync_disconnect_shared_links)
        } +
        // Deletions not yet told to the account's list are forgotten with it, leaving any of their links working.
        if (shareLinkController.hasPendingStops.collectAsStateWithLifecycle().value) {
            " " + stringResource(Res.string.settings_sync_disconnect_pending_stops)
        } else {
            ""
        },
    confirmLabel = stringResource(Res.string.settings_sync_disconnect),
    onDismiss = onDismiss,
    onConfirm = {
        onDisconnect()
        onDismiss()
    },
)
