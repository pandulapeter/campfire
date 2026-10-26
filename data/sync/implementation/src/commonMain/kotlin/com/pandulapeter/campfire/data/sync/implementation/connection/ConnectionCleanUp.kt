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
import com.pandulapeter.campfire.data.model.sync.SyncState
import com.pandulapeter.campfire.data.source.remote.api.sync.PendingAuthorizationStore
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncProvider

/** What `SyncConnectionManager` leaves behind of an authorization that did not end connected, dropped quietly. */
internal class ConnectionCleanUp(
    private val pendingAuthorizationStore: PendingAuthorizationStore,
    private val logger: Logger,
) {

    /**
     * Forgets the authorization that was started, for the ways out that have already decided how they end. Clearing
     * writes the credentials document, and a storage that refuses that write must not replace the ending with an
     * exception of its own: `SyncConnectionManager.connect` runs in a launched coroutine with nobody to throw to, and
     * the state has to leave [SyncState.Connecting] whatever the storage says. What stays behind is a verifier nothing
     * asks for again, and the next authorization writes over it.
     */
    suspend fun discardPendingAuthorization() = logger.recovering(
        // Only the kind of failure: the message of one that came from the credentials document may quote it.
        describe = { "Could not clear the pending authorization: ${it::class.simpleName}" },
        fallback = {},
    ) { pendingAuthorizationStore.clearPendingAuthorization() }

    /**
     * Drops whatever [provider] stored for an authorization that did not end connected, without telling the service:
     * there is nothing to revoke on the user's behalf for a connection they never saw made. A failure is only logged,
     * since the outcome the caller is on its way to report is the one that matters.
     */
    suspend fun forgetCredentialsOf(provider: SyncProvider) = logger.recovering(
        describe = { "Could not forget the ${provider.id} credentials: ${it.message}" },
        fallback = {},
    ) { provider.forgetStoredCredentials() }
}
