/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalTime::class)

package com.pandulapeter.campfire.data.sync.implementation

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.sync.SyncDeletionPolicy
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLock
import com.pandulapeter.campfire.data.sync.implementation.index.SyncIndexDocument
import com.pandulapeter.campfire.data.sync.implementation.preferences.SyncedPreferencesSync
import com.pandulapeter.campfire.data.sync.implementation.run.SyncEngine
import com.pandulapeter.campfire.data.sync.implementation.shared.FakeSharedCopyContents
import com.pandulapeter.campfire.data.sync.implementation.shared.FakeSharedLinksLocalSource
import com.pandulapeter.campfire.data.sync.implementation.shared.SharedLinksFormat
import com.pandulapeter.campfire.data.sync.implementation.shared.SharedLinksJournal
import com.pandulapeter.campfire.data.sync.implementation.shared.sharedCopySync
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

/**
 * One device connected to a cloud folder that other devices share: its library, the index its runs keep, and, where a
 * test gives it [preferences], the synced part of those settled after every completed run the way `SyncRunner` does it.
 * Its shared links' mirror and journal are [sharedLinks], and every completed run brings the shared copies up to date
 * after the preferences, as [buildNumber] builds them, as `SyncRunner` does.
 */
internal class Device(
    files: Map<SyncKey, ByteArray> = emptyMap(),
    val preferences: FakeUserPreferencesRepository? = null,
) {

    val library = FakeLibraryFileLocalSource(files)
    private val lock = LibraryFileLock()
    private val preferencesSync = preferences?.let { SyncedPreferencesSync(it, library, Logger.Standard) }
    private var index = SyncIndexDocument()

    val sharedLinks = FakeSharedLinksLocalSource()
    val sharedContents = FakeSharedCopyContents()
    var buildNumber = 1

    /** Whether the last run's shared-copy step settled every copy. */
    var areSharedCopiesSettled = true
        private set

    /** Only what the mirror and the journal use: nothing here launches or waits. */
    private val environment = SyncEnvironment(
        context = EmptyCoroutineContext,
        timeSource = TimeSource.Monotonic,
        clock = Clock.System,
        computation = Dispatchers.Unconfined,
        logger = Logger.Standard,
    )

    /** The library by file name, as text. */
    val contents get() = library.files.entries.associate { (key, bytes) -> key.name to bytes.decodeToString() }

    /** A run that must complete, after which the index is kept and the synced preferences and shared copies settled. */
    suspend fun sync(cloud: FakeSyncProvider, deletionPolicy: SyncDeletionPolicy = SyncDeletionPolicy.ASK) {
        val completed = assertIs<SyncEngine.Result.Completed>(syncResult(cloud, deletionPolicy))
        val settled = preferencesSync?.synchronize(
            provider = cloud,
            base = completed.index.syncedPreferences,
            unchanged = index.unchangedPreferences(ACCOUNT_ID, completed.documentRevisions),
            keptFileNames = completed.summary.failed,
            onLocalSettled = {},
        )
        areSharedCopiesSettled = sharedCopySync(
            library = library,
            environment = environment,
            localSource = sharedLinks,
            contents = sharedContents,
            buildNumber = buildNumber,
        ).synchronize(
            folder = cloud,
            index = completed.index.toIndex(),
            sharedFiles = completed.sharedFiles,
            registryRevision = completed.documentRevisions[SharedLinksFormat.NAME],
        )
        index = completed.index.copy(
            syncedPreferences = settled?.document ?: completed.index.syncedPreferences,
            syncedPreferencesRevision = settled?.revision,
        )
    }

    /** What the app's rename and deletion of a shared item leave for the next run. */
    suspend fun journal(entry: SharedLinksJournal.Entry) = SharedLinksJournal(sharedLinks, environment).append(listOf(entry))

    /** A run whatever it comes to; only [sync] keeps what a completed one leaves. */
    suspend fun syncResult(cloud: FakeSyncProvider, deletionPolicy: SyncDeletionPolicy) = synchronize(
        local = library,
        provider = cloud,
        document = index,
        setlistComparison = DayBlindSetlistComparison,
        lock = lock,
        deletionPolicy = deletionPolicy,
    )
}
