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

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.recovering
import com.pandulapeter.campfire.data.model.sync.SyncFailureReason
import com.pandulapeter.campfire.data.model.sync.SyncState
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncProvider
import com.pandulapeter.campfire.data.sync.implementation.SyncStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Asks the service who the account is behind a start up that has already shown what was stored. The answer only
 * ever changes a state that is still connected to the same provider: a new name replaces the stored one, and a
 * refusal - the grant was revoked elsewhere - takes the connection down the way a start up that waited would
 * have. Anything else, a dead network above all, leaves what is on screen alone.
 */
internal class SyncAccountRefresh(
    private val scope: CoroutineScope,
    private val stateHolder: SyncStateHolder,
    private val logger: Logger,
) {

    private var accountRefreshJob: Job? = null

    fun start(provider: SyncProvider) {
        if (accountRefreshJob?.isActive == true) return
        accountRefreshJob = scope.launch {
            val account = logger.recovering(
                describe = { "Could not refresh the ${provider.id} account: ${it.message}" },
                fallback = { return@launch },
            ) { provider.loadAccount() }
            stateHolder.updateConnected { connected ->
                when {
                    connected.account.providerId != provider.id -> connected
                    account == null -> SyncState.ConnectionFailed(provider.id, SyncFailureReason.AUTHORIZATION)
                    else -> connected.copy(account = account)
                }
            }
        }
    }

    fun cancel() {
        accountRefreshJob?.cancel()
    }
}
