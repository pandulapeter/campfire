package com.pandulapeter.campfire.domain.api.useCases

import com.pandulapeter.campfire.data.model.domain.SyncProviderId

interface ConnectSyncProviderUseCase {

    /**
     * Takes the user through the service's consent page and, if they agree, runs a first sync.
     *
     * @return Whether the app is now connected. False on the web even when all is well: consent happens on a page
     *   of the service's own, so the app is on its way there rather than finished, see [RestoreSyncUseCase].
     */
    suspend operator fun invoke(providerId: SyncProviderId): Boolean
}

interface DisconnectSyncProviderUseCase {

    /** Forgets the account. Nothing in the library and nothing in the remote folder is touched. */
    suspend operator fun invoke()
}

interface RestoreSyncUseCase {

    /**
     * Picks sync back up at start up: reads the stored account, and finishes an authorization the app was closed in
     * the middle of. Runs a first sync if that leaves it connected.
     */
    suspend operator fun invoke()
}
