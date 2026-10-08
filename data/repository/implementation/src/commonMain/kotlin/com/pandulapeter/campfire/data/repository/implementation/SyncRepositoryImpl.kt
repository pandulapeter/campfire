/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.domain.SyncDeletionPolicy
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.model.domain.SyncState
import com.pandulapeter.campfire.data.repository.api.SyncRepository
import com.pandulapeter.campfire.data.repository.implementation.sync.SyncConnectionManager
import com.pandulapeter.campfire.data.repository.implementation.sync.SyncEngine
import com.pandulapeter.campfire.data.repository.implementation.sync.SyncRunScheduler
import com.pandulapeter.campfire.data.repository.implementation.sync.SyncStateHolder
import com.pandulapeter.campfire.data.source.remote.api.SyncProviders
import com.pandulapeter.campfire.data.model.domain.AuthorizationCompletionPage
import org.koin.core.annotation.Single

/**
 * The state machine around [SyncEngine], and the only thing above the data layer that knows a service is involved
 * at all: the screens see a [SyncState], and which provider produced it is a detail of the `sync` package. A facade:
 * the connection is [SyncConnectionManager]'s, when a run starts [SyncRunScheduler]'s, and the state they report into
 * [SyncStateHolder]'s.
 */
@Single
internal class SyncRepositoryImpl(
    syncProviders: SyncProviders,
    stateHolder: SyncStateHolder,
    private val connectionManager: SyncConnectionManager,
    private val scheduler: SyncRunScheduler,
) : SyncRepository {

    override val syncState = stateHolder.state
    override val availableProviders = syncProviders.all.map { it.id }

    override suspend fun restore() = connectionManager.restore()

    override suspend fun connect(providerId: SyncProviderId, completionPage: AuthorizationCompletionPage) =
        connectionManager.connect(providerId, completionPage)

    override suspend fun cancelConnection() = connectionManager.cancelConnection()

    override suspend fun disconnect() = connectionManager.disconnect()

    override suspend fun forgetStoredConnection() = connectionManager.forgetStoredConnection()

    override fun synchronize(deletionPolicy: SyncDeletionPolicy) = scheduler.synchronize(deletionPolicy)

    override fun scheduleSynchronization() = scheduler.schedule()

    override fun startScheduledSynchronization() = scheduler.startScheduled()

    override fun cancelSynchronization() = scheduler.cancel()
}
