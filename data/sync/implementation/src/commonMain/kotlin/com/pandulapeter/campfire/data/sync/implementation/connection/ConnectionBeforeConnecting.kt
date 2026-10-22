/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.connection

import com.pandulapeter.campfire.data.model.sync.SyncState
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncProvider
import com.pandulapeter.campfire.data.sync.implementation.SyncStateHolder
import com.pandulapeter.campfire.data.sync.implementation.scheduling.SyncRunScheduler
import kotlinx.coroutines.CancellationException

/**
 * The connection an authorization started from, which backing out of it (Cancel, a closed consent page) returns to
 * while its credentials are still stored, since the next launch would restore it from them:
 * - a failed connection stays failed, rather than showing as disconnected what the next launch restores as connected;
 * - a working one stays connected, for an authorization asked again for a permission it was made without (making
 *   links), without the progress of a run whose reports went nowhere while Connecting was shown.
 *
 * Anything else, a failure with no credentials stored included (a first connection that never got that far), backs out
 * to disconnected.
 *
 * @param scheduler Stopped by `SyncConnectionManager.connect` for an authorization started from a stored connection,
 *   and asked for a run again on backing out, so that the stopped run's work goes on.
 */
internal class ConnectionBeforeConnecting(
    private val stateHolder: SyncStateHolder,
    private val scheduler: SyncRunScheduler,
) {

    private var state: SyncState? = null
    private var provider: SyncProvider? = null

    /** Whether the authorization started from a stored connection, which backing out returns to. */
    val hasStoredConnection get() = state != null

    /** Remembers [current] as the connection [provider]'s authorization starts from. */
    suspend fun remember(current: SyncState, provider: SyncProvider) {
        this.provider = provider
        state = when (current) {
            is SyncState.ConnectionFailed -> current
            is SyncState.Connected -> current.copy(progress = null)
            else -> null
        }?.takeIf { provider.hasStoredCredentials() }
    }

    fun forget() {
        state = null
        provider = null
    }

    /**
     * Puts back what the authorization started from, if it is still showing Connecting. The credentials are asked about
     * again here, because a token exchange given up after it stored new tokens forgets whatever the provider holds, the
     * old tokens with it; one that failed before storing anything leaves the old ones alone. The scheduler schedules
     * nothing for a state that is not Connected.
     */
    suspend fun backOut() {
        val after = state?.takeIf { provider?.hasStoredCredentials() == true } ?: SyncState.Disconnected
        stateHolder.update { if (it is SyncState.Connecting) after else it }
        forget()
        scheduler.schedule(reason = SyncRunScheduler.RunReason.CONNECTION)
    }
}

/** Whether the provider still holds credentials, which is what the next launch restores a connection from. */
private suspend fun SyncProvider.hasStoredCredentials() = try {
    isConnected()
} catch (exception: CancellationException) {
    throw exception
} catch (exception: Exception) {
    false
}
