/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation

import com.pandulapeter.campfire.data.model.domain.AuthorizationCompletionPage
import com.pandulapeter.campfire.data.model.domain.Logger
import com.pandulapeter.campfire.data.model.domain.SyncAccount
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.model.domain.SyncState
import com.pandulapeter.campfire.data.source.local.api.LibraryChanges
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLock
import com.pandulapeter.campfire.data.source.remote.api.SyncProviders
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope

/** The account [FakeSyncProvider] is given to say is connected in the repository's tests. */
internal val TEST_ACCOUNT = SyncAccount(
    providerId = SyncProviderId.DROPBOX,
    id = "dbid:1",
    displayName = "Someone",
    email = "someone@example.com",
)

internal val TEST_COMPLETION_PAGE = AuthorizationCompletionPage(title = "", message = "")

/** A redirect that answers [FakeSyncProvider]'s authorization request: its code, and the state it was started with. */
internal const val TEST_REDIRECT = "campfire://sync?code=c&state=state"

/**
 * The repository wired the way `DataSyncModule` wires it, over one [provider] and the in-memory fakes, with every
 * launched job on the test's scheduler (see [testEnvironment]).
 */
internal fun TestScope.syncRepository(
    provider: FakeSyncProvider,
    authenticator: FakeSyncAuthenticator = FakeSyncAuthenticator(),
    pendingAuthorizationStore: FakePendingAuthorizationStore = FakePendingAuthorizationStore(),
    stateLocalSource: FakeSyncIndexLocalSource = FakeSyncIndexLocalSource(),
    libraryFileLocalSource: FakeLibraryFileLocalSource = FakeLibraryFileLocalSource(),
    songRepository: RecordingSongRepository = RecordingSongRepository(),
    setlistRepository: RecordingSetlistRepository = RecordingSetlistRepository(),
    userPreferencesRepository: FakeUserPreferencesRepository = FakeUserPreferencesRepository(),
    logger: Logger = Logger.Standard,
): SyncRepositoryImpl {
    val environment = testEnvironment(logger = logger)
    val syncProviders = SyncProviders(listOf(provider))
    val stateHolder = SyncStateHolder(logger)
    val indexStore = SyncIndexStore(stateLocalSource, environment)
    val syncedPreferencesSync = SyncedPreferencesSync(userPreferencesRepository, libraryFileLocalSource, logger)
    val runner = SyncRunner(
        syncProviders = syncProviders,
        engine = DataSyncModule.syncEngine(
            libraryFileLocalSource = libraryFileLocalSource,
            libraryFileLock = LibraryFileLock(),
            setlistComparison = NoSetlistComparison,
            userPreferencesRepository = userPreferencesRepository,
            logger = logger,
        ),
        syncedPreferencesSync = syncedPreferencesSync,
        indexStore = indexStore,
        libraryRefresher = SyncLibraryRefresher(songRepository, setlistRepository, environment),
        stateHolder = stateHolder,
        environment = environment,
    )
    val scheduler = SyncRunScheduler(
        runner = runner,
        stateHolder = stateHolder,
        libraryChanges = LibraryChanges(),
        syncedPreferencesSync = syncedPreferencesSync,
        environment = environment,
    )
    return SyncRepositoryImpl(
        syncProviders = syncProviders,
        stateHolder = stateHolder,
        connectionManager = SyncConnectionManager(
            syncProviders = syncProviders,
            authenticator = authenticator,
            pendingAuthorizationStore = pendingAuthorizationStore,
            syncIndexLocalSource = stateLocalSource,
            stateHolder = stateHolder,
            indexStore = indexStore,
            runner = runner,
            scheduler = scheduler,
            environment = environment,
        ),
        scheduler = scheduler,
    )
}

/** Waits for the state a run reports at its end; a run that never reports is failed by `runTest`'s own timeout. */
internal suspend fun SyncRepositoryImpl.awaitOutcome() = syncState.first {
    it is SyncState.Connected && !it.isSyncing && it.lastOutcome != null
} as SyncState.Connected
