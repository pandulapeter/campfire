/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.export

import com.pandulapeter.campfire.data.model.share.CreateShareResult
import com.pandulapeter.campfire.data.model.share.Share
import com.pandulapeter.campfire.data.model.share.ShareState
import com.pandulapeter.campfire.data.model.share.SharingFailure
import com.pandulapeter.campfire.data.model.sync.SyncFailureReason
import com.pandulapeter.campfire.data.model.sync.SyncState
import com.pandulapeter.campfire.presentation.ui.share.ShareCreation
import com.pandulapeter.campfire.presentation.ui.share.ShareTarget
import com.pandulapeter.campfire.presentation.ui.share.shareOf

/** What the Link format shows for one song or setlist, see [linkExportStateOf]. */
internal sealed interface LinkExportState {

    /** No account is connected: connecting it is offered, and said to sync the whole library. */
    data object NotConnected : LinkExportState

    data object Connecting : LinkExportState

    /** The last attempt to connect failed, for [reason], as the Settings screen says it. */
    data class ConnectionFailed(val reason: SyncFailureReason) : LinkExportState

    /** Connected, and the item is not shared yet. */
    data object NotShared : LinkExportState

    /** The share is being made. */
    data object Creating : LinkExportState

    /** The share is being stopped. */
    data object Stopping : LinkExportState

    /** The item is shared as [share], whose copy may have stopped following it for its size ([isTooLarge]). */
    data class Shared(val share: Share) : LinkExportState {
        val isTooLarge get() = share.state == ShareState.TOO_LARGE
    }

    /** The last attempt to share the item failed, for [problem]. */
    data class Failed(val problem: LinkProblem) : LinkExportState
}

/** Why a share could not be made, as the screen tells the reasons apart: by what can be done about each. */
internal enum class LinkProblem {

    /** The connection predates the permission to make links: connecting again grants it. */
    SCOPE_MISSING,

    /** The service no longer accepts the connection: connecting again is the way out. */
    AUTHORIZATION,

    /** Over what the person opening the link could import. */
    TOO_LARGE,

    /** The account makes no public links. */
    NOT_PUBLIC,

    /** The service could not be reached. */
    NETWORK,

    /** The cloud folder is full. */
    REMOTE_STORAGE_FULL,

    /** The account's list of shares was written by a newer version of Campfire. */
    NEWER_VERSION,

    /** The item is no longer in the library. */
    MISSING,

    /** Anything else: the service refused the link, or something unforeseen went wrong. */
    OTHER,
}

/** The floating action button of the Link format, see [linkActionOf]. */
internal enum class LinkAction { CONNECT, CONNECT_AGAIN, CANCEL, CREATE, TRY_AGAIN, COPY }

/**
 * What the Link format shows for [target]: the connection decides first, since a share needs one, then a share the
 * account already lists (made here or on another device), then how the last attempt to make one here went ([creation]).
 */
internal fun linkExportStateOf(
    syncState: SyncState,
    target: ShareTarget,
    shares: List<Share>,
    creation: ShareCreation?,
    stoppingShareIds: Set<String>,
): LinkExportState {
    when (syncState) {
        SyncState.Disconnected -> return LinkExportState.NotConnected
        is SyncState.Connecting -> return LinkExportState.Connecting
        is SyncState.ConnectionFailed -> return LinkExportState.ConnectionFailed(syncState.reason)
        is SyncState.Connected -> Unit
    }
    val ownCreation = creation?.takeIf { it.target == target }
    val share = shareOf(shares, target)
    return when {
        share != null && share.shareId in stoppingShareIds -> LinkExportState.Stopping
        share != null -> LinkExportState.Shared(share)
        ownCreation == null -> LinkExportState.NotShared
        else -> when (val result = ownCreation.result) {
            null -> LinkExportState.Creating
            // The account's list is updated as the share is made, but the two arrive separately: this is the moment
            // between, and stopping the share forgets it.
            is CreateShareResult.Shared -> if (result.share.shareId in stoppingShareIds) LinkExportState.Stopping else LinkExportState.Shared(result.share)
            CreateShareResult.TooLarge -> LinkExportState.Failed(LinkProblem.TOO_LARGE)
            CreateShareResult.Missing -> LinkExportState.Failed(LinkProblem.MISSING)
            is CreateShareResult.Failed -> LinkExportState.Failed(result.reason.toLinkProblem())
        }
    }
}

private fun SharingFailure.toLinkProblem() = when (this) {
    SharingFailure.SCOPE_MISSING -> LinkProblem.SCOPE_MISSING
    SharingFailure.AUTHORIZATION -> LinkProblem.AUTHORIZATION
    SharingFailure.NOT_PUBLIC -> LinkProblem.NOT_PUBLIC
    SharingFailure.NETWORK -> LinkProblem.NETWORK
    SharingFailure.REMOTE_STORAGE_FULL -> LinkProblem.REMOTE_STORAGE_FULL
    SharingFailure.NEWER_VERSION -> LinkProblem.NEWER_VERSION
    SharingFailure.NOT_CONNECTED,
    SharingFailure.REFUSED,
    SharingFailure.UNKNOWN -> LinkProblem.OTHER
}

/**
 * The floating action button's action in [state], null where there is none: nothing the screen could do would change
 * the answer (a size, an account's policy, a full folder, a newer version), or nothing is to be done while it works.
 */
internal fun linkActionOf(state: LinkExportState) = when (state) {
    LinkExportState.NotConnected -> LinkAction.CONNECT
    LinkExportState.Connecting -> LinkAction.CANCEL
    is LinkExportState.ConnectionFailed -> LinkAction.CONNECT_AGAIN
    LinkExportState.NotShared -> LinkAction.CREATE
    LinkExportState.Creating, LinkExportState.Stopping -> null
    is LinkExportState.Shared -> LinkAction.COPY
    is LinkExportState.Failed -> when (state.problem) {
        LinkProblem.SCOPE_MISSING, LinkProblem.AUTHORIZATION -> LinkAction.CONNECT_AGAIN
        LinkProblem.NETWORK, LinkProblem.OTHER -> LinkAction.TRY_AGAIN
        LinkProblem.TOO_LARGE, LinkProblem.NOT_PUBLIC, LinkProblem.REMOTE_STORAGE_FULL, LinkProblem.NEWER_VERSION, LinkProblem.MISSING -> null
    }
}
