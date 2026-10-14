/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.sync

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.pandulapeter.campfire.data.model.sync.AuthorizationCompletionPage
import com.pandulapeter.campfire.data.model.sync.SyncFailureReason
import com.pandulapeter.campfire.data.model.sync.SyncState
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.settings_sync_connection_failed_authorization
import com.pandulapeter.campfire.presentation.resources.settings_sync_connection_failed_network
import com.pandulapeter.campfire.presentation.resources.settings_sync_connection_failed_storage
import com.pandulapeter.campfire.presentation.resources.settings_sync_connection_failed_unknown
import com.pandulapeter.campfire.presentation.resources.settings_sync_redirect_page_message
import com.pandulapeter.campfire.presentation.resources.settings_sync_redirect_page_title

/**
 * What the settings screen's sync section and the onboarding's sync page animate between. A failed connection is the
 * invitation to connect with the reason under it, so the two are one stage and the reason comes and goes within it.
 */
internal enum class SyncStage { DISCONNECTED, CONNECTING, CONNECTED }

internal val SyncState.stage
    get() = when (this) {
        SyncState.Disconnected, is SyncState.ConnectionFailed -> SyncStage.DISCONNECTED
        is SyncState.Connecting -> SyncStage.CONNECTING
        is SyncState.Connected -> SyncStage.CONNECTED
    }

/**
 * The page the browser shows once the consent has been answered. Resolved out here rather than in the data layer, which
 * can see neither the translations nor the language the user picked.
 */
@Composable
internal fun rememberAuthorizationCompletionPage(): AuthorizationCompletionPage {
    val title = stringResource(Res.string.settings_sync_redirect_page_title)
    val message = stringResource(Res.string.settings_sync_redirect_page_message)
    return remember(title, message) { AuthorizationCompletionPage(title = title, message = message) }
}

/** Why a connection failed, as the line under the Connect button says it. */
internal val SyncFailureReason.connectionFailedMessage
    get() = when (this) {
        SyncFailureReason.NETWORK -> Res.string.settings_sync_connection_failed_network
        SyncFailureReason.AUTHORIZATION -> Res.string.settings_sync_connection_failed_authorization
        SyncFailureReason.STORAGE -> Res.string.settings_sync_connection_failed_storage
        SyncFailureReason.REMOTE_STORAGE_FULL,
        SyncFailureReason.UNKNOWN -> Res.string.settings_sync_connection_failed_unknown
    }
