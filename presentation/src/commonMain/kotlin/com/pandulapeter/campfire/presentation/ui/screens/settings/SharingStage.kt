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

import com.pandulapeter.campfire.data.model.share.Share
import com.pandulapeter.campfire.data.model.share.SharingFailure
import com.pandulapeter.campfire.data.model.sync.SyncState

/** What the Sharing tab of the settings screen shows, see [sharingStageOf]. */
internal enum class SharingStage {

    /** No account, or one whose connection failed: the invitation to connect, which says the library syncs too. */
    NOT_CONNECTED,

    CONNECTING,

    /** Connected, but without the permission to make links, or with a connection the service no longer accepts. */
    CONNECT_AGAIN,

    NOTHING_SHARED,

    SHARED,
}

/**
 * The stage of the Sharing tab: the connection decides first, then what the last read of the account's list answered
 * ([refreshFailure]), then whether anything is shared. A list that could not be read for the network is still the one
 * this device last saw, and is shown.
 */
internal fun sharingStageOf(
    syncState: SyncState,
    refreshFailure: SharingFailure?,
    shares: List<Share>,
) = when (syncState) {
    SyncState.Disconnected, is SyncState.ConnectionFailed -> SharingStage.NOT_CONNECTED
    is SyncState.Connecting -> SharingStage.CONNECTING
    is SyncState.Connected -> when {
        refreshFailure == SharingFailure.SCOPE_MISSING || refreshFailure == SharingFailure.AUTHORIZATION -> SharingStage.CONNECT_AGAIN
        shares.isEmpty() -> SharingStage.NOTHING_SHARED
        else -> SharingStage.SHARED
    }
}

/**
 * Whether [refreshFailure] leaves the Sharing tab showing the list this device last saw, which it then says: a failure
 * that asks to connect again shows no list, and a newer version's list has a line of its own.
 */
internal fun isStaleSharingList(refreshFailure: SharingFailure?) = when (refreshFailure) {
    null, SharingFailure.NOT_CONNECTED, SharingFailure.SCOPE_MISSING, SharingFailure.AUTHORIZATION, SharingFailure.NEWER_VERSION -> false
    SharingFailure.NOT_PUBLIC, SharingFailure.REFUSED, SharingFailure.REMOTE_STORAGE_FULL, SharingFailure.NETWORK, SharingFailure.UNKNOWN -> true
}
