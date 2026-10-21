/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.api.sync

import com.pandulapeter.campfire.data.model.sync.SyncAccount
import com.pandulapeter.campfire.data.model.sync.SyncProviderId
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteAuthorizationRequest
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteAuthorizationResponse

/**
 * The account half of a [SyncProvider]: who is connected, and the credentials that say so. Nothing a run does is here -
 * a run reads and writes a [SyncFolder] and never connects, disconnects or authorizes anything.
 *
 * Nothing here throws for an expired token: refreshing is the provider's own business, and only an authorization that
 * cannot be repaired surfaces, as [SyncAuthorizationException].
 */
interface SyncConnection {

    val id: SyncProviderId

    /** True once there are credentials to work with, whether or not they still work. */
    suspend fun isConnected(): Boolean

    /** Builds the URL to open in the browser. [redirectUri] is null on platforms that use the copy-a-code flow. */
    fun buildAuthorizationRequest(redirectUri: String?): RemoteAuthorizationRequest

    /**
     * Exchanges the code for tokens and stores them, then returns who the user turned out to be.
     *
     * Nothing is stored until the code has been exchanged: a [SyncNetworkException] or [SyncAuthorizationException]
     * thrown here comes from the exchange, before anything was stored, so credentials stored by an earlier connection
     * are still there and still work. Reconnecting a working connection relies on that to keep it.
     *
     * @param verifier The PKCE verifier from the matching [RemoteAuthorizationRequest], which may have been read
     *   back from storage after a page reload.
     */
    suspend fun completeAuthorization(
        response: RemoteAuthorizationResponse,
        verifier: String,
        redirectUri: String?,
    ): SyncAccount

    /**
     * Forgets the stored credentials at once, without a request, and answers the request that tells the service to drop
     * them too, for the caller to run in the background: the user's part of a disconnect is local, and only that
     * courtesy needs the network. The request may take as long as the network does, never throws but cancellation, and
     * stores nothing - what it needs was captured before the credentials were forgotten. Null when there is nothing to
     * revoke.
     */
    suspend fun disconnect(): (suspend () -> Unit)?

    /**
     * Forgets the stored credentials and tells the service nothing. [disconnect] is the user's disconnect, which
     * also revokes the token; this is an installation dropping what a previous one left in a store that outlived
     * it, where there is nobody to revoke on behalf of and no permission to make a request.
     */
    suspend fun forgetStoredCredentials()

    /** Who is connected, or null if the credentials are gone or no longer accepted. */
    suspend fun loadAccount(): SyncAccount?

    /**
     * Who is connected as far as this device remembers, answered from what is stored and without a request - so that
     * a start up on a bad network can show the account at once and leave [loadAccount] to catch up behind it. Null
     * when nothing is connected, or when nothing was ever stored that the account could go by.
     */
    suspend fun storedAccount(): SyncAccount?
}
