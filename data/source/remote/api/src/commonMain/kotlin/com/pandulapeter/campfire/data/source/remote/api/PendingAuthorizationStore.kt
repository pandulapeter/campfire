package com.pandulapeter.campfire.data.source.remote.api

import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteAuthorizationRequest

/**
 * The authorization that has been started and not finished.
 *
 * It exists because one platform cannot hold anything in memory across the flow: the web navigates away from the
 * running app to ask for consent, and the PKCE verifier still has to be there when the app starts again. Every
 * other platform would be happy with a field, and uses this anyway, so that all four take one code path.
 */
interface PendingAuthorizationStore {

    suspend fun savePendingAuthorization(providerId: SyncProviderId, request: RemoteAuthorizationRequest)

    suspend fun loadPendingAuthorization(): PendingAuthorization?

    suspend fun clearPendingAuthorization()
}

data class PendingAuthorization(
    val providerId: SyncProviderId,
    val state: String,
    val verifier: String,
    val redirectUri: String?
)
