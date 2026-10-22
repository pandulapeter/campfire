/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.sync.SyncFailureReason
import com.pandulapeter.campfire.data.model.sync.SyncProviderId
import com.pandulapeter.campfire.data.model.sync.SyncState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import org.koin.core.annotation.Single

/** The one [SyncState] every part of sync reports into, and the screens read through `SyncRepository.syncState`. */
@Single
internal class SyncStateHolder(
    private val logger: Logger,
) {

    private val _state = MutableStateFlow<SyncState>(SyncState.Disconnected)
    val state = _state.asStateFlow()
    val value get() = _state.value

    fun update(transform: (SyncState) -> SyncState) = logged(_state.getAndUpdate(transform))

    /**
     * Changes the state only while it is still [SyncState.Connected]. A run reports how it went when it ends, and if
     * the account was disconnected in the meantime that report has nothing to attach itself to: rebuilding the
     * connected state from what the run remembers would put the account back on screen.
     */
    fun updateConnected(transform: (SyncState.Connected) -> SyncState) = logged(
        _state.getAndUpdate {
            if (it is SyncState.Connected) transform(it) else it
        },
    )

    fun fail(providerId: SyncProviderId, reason: SyncFailureReason, message: String): Boolean {
        logger.log(message)
        logged(_state.getAndUpdate { SyncState.ConnectionFailed(providerId, reason) })
        return false
    }

    /**
     * Logs where the connection went from [before]: only a change of its kind, or of a failure's reason, since a run
     * updates the connected state many times a second with its progress.
     */
    private fun logged(before: SyncState) {
        val after = _state.value
        if (before::class == after::class && (after !is SyncState.ConnectionFailed || before == after)) return
        logger.log(
            "Sync: " + when (after) {
                SyncState.Disconnected -> "disconnected"
                is SyncState.Connecting -> "connecting"
                is SyncState.Connected -> "connected (${after.account.providerId.id})"
                is SyncState.ConnectionFailed -> "connection failed (${after.reason})"
            },
        )
    }
}
