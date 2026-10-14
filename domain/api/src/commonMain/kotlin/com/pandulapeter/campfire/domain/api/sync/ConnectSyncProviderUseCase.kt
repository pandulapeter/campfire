/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.sync

import com.pandulapeter.campfire.data.model.sync.SyncProviderId
import com.pandulapeter.campfire.data.model.sync.AuthorizationCompletionPage

interface ConnectSyncProviderUseCase {

    /**
     * Takes the user through the service's consent page and, if they agree, runs a first sync.
     *
     * @return Whether the app is now connected. False on the web even when all is well: consent happens on a page
     *   of the service's own, so the app is on its way there rather than finished, see [RestoreSyncUseCase].
     */
    suspend operator fun invoke(providerId: SyncProviderId, completionPage: AuthorizationCompletionPage): Boolean
}
