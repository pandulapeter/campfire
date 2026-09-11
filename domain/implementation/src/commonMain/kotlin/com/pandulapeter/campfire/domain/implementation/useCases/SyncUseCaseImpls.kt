/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.source.remote.api.model.AuthorizationCompletionPage
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.data.repository.api.SyncRepository
import com.pandulapeter.campfire.domain.api.useCases.CancelSynchronizationUseCase
import com.pandulapeter.campfire.domain.api.useCases.ConnectSyncProviderUseCase
import com.pandulapeter.campfire.domain.api.useCases.DisconnectSyncProviderUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetSyncProvidersUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetSyncStateUseCase
import com.pandulapeter.campfire.domain.api.useCases.RestoreSyncUseCase
import com.pandulapeter.campfire.domain.api.useCases.SynchronizeLibraryUseCase

class GetSyncStateUseCaseImpl internal constructor(
    private val syncRepository: SyncRepository,
) : GetSyncStateUseCase {

    override operator fun invoke() = syncRepository.syncState
}

class GetSyncProvidersUseCaseImpl internal constructor(
    private val syncRepository: SyncRepository,
) : GetSyncProvidersUseCase {

    override operator fun invoke() = syncRepository.availableProviders
}

class ConnectSyncProviderUseCaseImpl internal constructor(
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

class DisconnectSyncProviderUseCaseImpl internal constructor(
    private val syncRepository: SyncRepository,
) : DisconnectSyncProviderUseCase {

    override suspend operator fun invoke() = syncRepository.disconnect()
}

class RestoreSyncUseCaseImpl internal constructor(
    private val syncRepository: SyncRepository,
    private val synchronizeLibrary: SynchronizeLibraryUseCase,
) : RestoreSyncUseCase {

    override suspend operator fun invoke(): Boolean {
        val result = syncRepository.restore()
        if (result.isConnected) {
            synchronizeLibrary()
        }
        return result.didReturnFromAuthorization
    }
}

class SynchronizeLibraryUseCaseImpl internal constructor(
    private val syncRepository: SyncRepository,
) : SynchronizeLibraryUseCase {

    override operator fun invoke() = syncRepository.synchronize()
}

class CancelSynchronizationUseCaseImpl internal constructor(
    private val syncRepository: SyncRepository,
) : CancelSynchronizationUseCase {

    override operator fun invoke() = syncRepository.cancelSynchronization()
}
