/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.useCases

import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.source.remote.api.model.AuthorizationCompletionPage

interface ConnectSyncProviderUseCase {

    /**
     * Takes the user through the service's consent page and, if they agree, runs a first sync.
     *
     * @return Whether the app is now connected. False on the web even when all is well: consent happens on a page
     *   of the service's own, so the app is on its way there rather than finished, see [RestoreSyncUseCase].
     */
    suspend operator fun invoke(providerId: SyncProviderId, completionPage: AuthorizationCompletionPage): Boolean
}

interface DisconnectSyncProviderUseCase {

    /** Forgets the account. Nothing in the library and nothing in the remote folder is touched. */
    suspend operator fun invoke()
}

interface RestoreSyncUseCase {

    /**
     * Picks sync back up at start up: reads the stored account, and finishes an authorization the app was closed in
     * the middle of. Runs a first sync if that leaves it connected.
     *
     * @return Whether this start up was the answer to a consent page the app had been sent away to, which only
     *   happens on the web. True either way the service answered: it says the user is coming back from connecting
     *   an account, and so should be shown the screen they started that from rather than the one the app opens on.
     */
    suspend operator fun invoke(): Boolean
}
