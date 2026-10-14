/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.sync

import com.pandulapeter.campfire.data.model.sync.SyncProviderId
import com.pandulapeter.campfire.data.model.sync.AuthorizationCompletionPage
import com.pandulapeter.campfire.data.repository.api.SyncRepository
import com.pandulapeter.campfire.domain.api.sync.ConnectSyncProviderUseCase
import com.pandulapeter.campfire.domain.api.sync.SynchronizeLibraryUseCase
import org.koin.core.annotation.Factory

@Factory
internal class ConnectSyncProviderUseCaseImpl(
    private val syncRepository: SyncRepository,
    private val synchronizeLibrary: SynchronizeLibraryUseCase,
) : ConnectSyncProviderUseCase {

    /**
     * A first run follows straight away. Connecting an account and then facing a library that is still empty would
     * leave the user to work out that something else is expected of them.
     */
    override suspend operator fun invoke(providerId: SyncProviderId, completionPage: AuthorizationCompletionPage): Boolean {
        if (!syncRepository.connect(providerId, completionPage)) return false
        synchronizeLibrary()
        return true
    }
}
