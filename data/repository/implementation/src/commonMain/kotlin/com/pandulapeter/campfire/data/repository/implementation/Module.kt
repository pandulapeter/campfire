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

import com.pandulapeter.campfire.data.model.domain.Logger
import com.pandulapeter.campfire.data.repository.api.UserPreferencesRepository
import com.pandulapeter.campfire.data.repository.implementation.base.RepositoryEnvironment
import com.pandulapeter.campfire.data.repository.implementation.sync.SyncEngine
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLocalSource
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLock
import com.pandulapeter.campfire.data.source.local.api.SetlistComparison
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

@Module
@ComponentScan
object DataRepositoryModule {

    /**
     * Built here rather than declared on the class, since the scope, the clocks and the dispatcher are exactly what
     * the repositories' tests replace with the test scheduler's.
     */
    @OptIn(ExperimentalTime::class)
    @Single
    internal fun repositoryEnvironment(logger: Logger): RepositoryEnvironment = RepositoryEnvironment(
        context = SupervisorJob() + Dispatchers.Default,
        timeSource = TimeSource.Monotonic,
        clock = Clock.System,
        computation = Dispatchers.Default,
        logger = logger,
    )

    /**
     * The one [Logger] of the data and domain layers, provided here for every module: the Koin graph is checked whole
     * in `:app:di`, so which module object declares it does not matter to anyone who asks for it.
     */
    @Single
    internal fun logger(): Logger = Logger.Standard

    /**
     * Built here rather than declared on the class, so that its tests keep the constructor with the default lookup:
     * the Koin compiler plugin would hand an annotated class that default rather than this lookup, and a demo file
     * this device planted would then never yield to the cloud folder's version.
     */
    @Single
    internal fun syncEngine(
        libraryFileLocalSource: LibraryFileLocalSource,
        libraryFileLock: LibraryFileLock,
        setlistComparison: SetlistComparison,
        userPreferencesRepository: UserPreferencesRepository,
        logger: Logger,
    ): SyncEngine = SyncEngine(
        libraryFileLocalSource = libraryFileLocalSource,
        libraryFileLock = libraryFileLock,
        setlistComparison = setlistComparison,
        plantedContentHash = { key -> userPreferencesRepository.loadUserPreferencesIfNeeded()?.demoLibraryContentHashes?.get(key.path) },
        logger = logger,
    )
}
