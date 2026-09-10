package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.model.domain.SyncOutcome
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.data.repository.api.SyncRepository
import com.pandulapeter.campfire.domain.api.useCases.ConnectSyncProviderUseCase
import com.pandulapeter.campfire.domain.api.useCases.DisconnectSyncProviderUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetSyncProvidersUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetSyncStateUseCase
import com.pandulapeter.campfire.domain.api.useCases.RestoreSyncUseCase
import com.pandulapeter.campfire.domain.api.useCases.SynchronizeLibraryUseCase

class GetSyncStateUseCaseImpl internal constructor(
    private val syncRepository: SyncRepository
) : GetSyncStateUseCase {

    override operator fun invoke() = syncRepository.syncState
}

class GetSyncProvidersUseCaseImpl internal constructor(
    private val syncRepository: SyncRepository
) : GetSyncProvidersUseCase {

    override operator fun invoke() = syncRepository.availableProviders
}

class ConnectSyncProviderUseCaseImpl internal constructor(
    private val syncRepository: SyncRepository,
    private val synchronizeLibrary: SynchronizeLibraryUseCase
) : ConnectSyncProviderUseCase {

    /**
     * A first run follows straight away. Connecting an account and then facing a library that is still empty would
     * leave the user to work out that something else is expected of them.
     */
    override suspend operator fun invoke(providerId: SyncProviderId): Boolean {
        if (!syncRepository.connect(providerId)) return false
        synchronizeLibrary()
        return true
    }
}

class DisconnectSyncProviderUseCaseImpl internal constructor(
    private val syncRepository: SyncRepository
) : DisconnectSyncProviderUseCase {

    override suspend operator fun invoke() = syncRepository.disconnect()
}

class RestoreSyncUseCaseImpl internal constructor(
    private val syncRepository: SyncRepository,
    private val synchronizeLibrary: SynchronizeLibraryUseCase
) : RestoreSyncUseCase {

    override suspend operator fun invoke() {
        if (syncRepository.restore()) {
            synchronizeLibrary()
        }
    }
}

class SynchronizeLibraryUseCaseImpl internal constructor(
    private val syncRepository: SyncRepository,
    private val songRepository: SongRepository,
    private val setlistRepository: SetlistRepository
) : SynchronizeLibraryUseCase {

    /**
     * The rescan is the same one an import ends with, and for the same reason: sync writes files behind the cached
     * lists' back, so the lists have to be read again before the screens can be right.
     *
     * Only when something actually moved. Most runs find nothing to do, and re-reading the whole library each time
     * the user opens the app would cost more than the sync itself.
     */
    override suspend operator fun invoke(): SyncOutcome? {
        val outcome = syncRepository.synchronize()
        if (outcome is SyncOutcome.Success && outcome.summary.hasChanges) {
            songRepository.rescan()
            setlistRepository.rescan()
        }
        return outcome
    }
}
