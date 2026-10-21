/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.screenshots

import com.pandulapeter.campfire.data.model.catalogue.CoverArtQuery
import com.pandulapeter.campfire.data.model.catalogue.CoverArtResult
import com.pandulapeter.campfire.data.model.catalogue.CoverArtSearchResults
import com.pandulapeter.campfire.data.model.sync.AuthorizationCompletionPage
import com.pandulapeter.campfire.data.model.sync.SyncDeletionPolicy
import com.pandulapeter.campfire.data.model.sync.SyncProgress
import com.pandulapeter.campfire.data.model.sync.SyncProviderId
import com.pandulapeter.campfire.data.model.sync.SyncState
import com.pandulapeter.campfire.data.repository.api.CoverArtRepository
import com.pandulapeter.campfire.data.repository.api.SyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.koin.dsl.module

/** What the UI tests start the app with on top of `Fakes.kt`'s, in place of what would reach outside. */
internal val testFakes = module {
    single<CoverArtRepository> { OfflineCoverArtRepository() }
}

/**
 * Sync as a device that never connected sees it, for the tests of what is offered before connecting, in place of
 * `Fakes.kt`'s connected account. Connecting opens no browser and connects nothing, so the invitation stays.
 */
internal val disconnectedSyncFakes = module {
    single<SyncRepository> { DisconnectedSyncRepository() }
}

/**
 * Covers as a device without a network sees them: no song's image can be had, and every catalogue a search asks has
 * already answered it with nothing, so the search's first frame is its last.
 */
private class OfflineCoverArtRepository : CoverArtRepository {
    override suspend fun getCoverArt(url: String): CoverArtResult = CoverArtResult.Missing

    override fun searchCoverArt(query: CoverArtQuery) = flowOf(
        CoverArtSearchResults(candidates = emptyList(), pending = emptySet(), busy = emptySet(), failed = emptySet()),
    )

    override val coverArtCacheSize = flowOf<Long?>(0L)

    override suspend fun clearCoverArtCache() = Unit
}

private class DisconnectedSyncRepository : SyncRepository {

    override val syncState = MutableStateFlow<SyncState>(SyncState.Disconnected)

    override val availableProviders = listOf(SyncProviderId.DROPBOX)

    override suspend fun restore(onReturnedFromAuthorization: () -> Unit) =
        SyncRepository.RestoreResult(isConnected = false, wasInterrupted = false)

    override suspend fun connect(providerId: SyncProviderId, completionPage: AuthorizationCompletionPage) = false

    override suspend fun cancelConnection() = Unit

    override suspend fun disconnect() = Unit

    override suspend fun forgetStoredConnection() = Unit

    override fun synchronize(deletionPolicy: SyncDeletionPolicy) = false

    override fun scheduleSynchronization() = Unit

    override fun synchronizeOnReturn() = Unit

    override suspend fun watchRemoteChanges() = Unit

    override fun startScheduledSynchronization(): SyncProgress? = null

    override fun cancelSynchronization() = Unit
}
