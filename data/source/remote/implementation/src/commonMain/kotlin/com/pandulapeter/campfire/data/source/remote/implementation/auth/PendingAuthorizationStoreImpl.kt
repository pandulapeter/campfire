/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.auth

import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.source.remote.api.PendingAuthorization
import com.pandulapeter.campfire.data.source.remote.api.PendingAuthorizationStore
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteAuthorizationRequest

/**
 * Kept in the credentials document rather than one of its own: it is the same kind of secret, it has the same
 * lifetime, and one file means one place where an interrupted authorization can be left behind.
 */
internal class PendingAuthorizationStoreImpl(
    private val credentialsStore: SyncCredentialsStore
) : PendingAuthorizationStore {

    override suspend fun savePendingAuthorization(providerId: SyncProviderId, request: RemoteAuthorizationRequest) =
        credentialsStore.update { credentials ->
            (credentials ?: SyncCredentialsDocument()).copy(
                pending = SyncCredentialsDocument.Pending(
                    providerId = providerId.id,
                    state = request.state,
                    verifier = request.verifier,
                    redirectUri = request.redirectUri.orEmpty()
                )
            ) to Unit
        }

    override suspend fun loadPendingAuthorization() = credentialsStore.load()?.pending?.let { pending ->
        SyncProviderId.fromId(pending.providerId)?.let { providerId ->
            PendingAuthorization(
                providerId = providerId,
                state = pending.state,
                verifier = pending.verifier,
                redirectUri = pending.redirectUri.takeIf { it.isNotEmpty() }
            )
        }
    }

    /**
     * An authorization that was abandoned before any token arrived leaves a document with nothing in it, so the
     * document goes rather than sitting there empty.
     */
    override suspend fun clearPendingAuthorization() = credentialsStore.update { credentials ->
        credentials?.copy(pending = null)?.takeIf { it.refreshToken.isNotEmpty() } to Unit
    }
}
