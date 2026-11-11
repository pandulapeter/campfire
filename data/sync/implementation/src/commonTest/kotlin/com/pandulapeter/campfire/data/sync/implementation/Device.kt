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
import com.pandulapeter.campfire.data.sync.implementation.shared.SharedCopySync
import com.pandulapeter.campfire.data.sync.implementation.shared.SharedLinksJournal
import com.pandulapeter.campfire.data.sync.implementation.shared.SharedLinksJournalApplier
import com.pandulapeter.campfire.data.sync.implementation.shared.SharedLinksMirror
import com.pandulapeter.campfire.data.sync.implementation.shared.SharedLinksRegistry
import com.pandulapeter.campfire.data.sync.implementation.shared.SharedLinksRepositoryImpl
import com.pandulapeter.campfire.data.model.sync.SyncState
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncProviders
import kotlinx.coroutines.Dispatchers
import kotlin.random.Random
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

/**
 * One device connected to a cloud folder that other devices share: its library, the index its runs keep, and, where a
 * test gives it [preferences], the synced part of those settled after every completed run the way `SyncRunner` does it.
 * Its shared links' mirror and journal are [sharedLinks], and every completed run brings the shared copies up to date
 * after the preferences, as [buildNumber] builds them, as `SyncRunner` does; [sharing] is what the app's sharing actions
 * and its notes of renames and deletions go through, over the same mirror and journal. Its file system compares names by
 * [naming].
 */
internal class Device(
    files: Map<SyncKey, ByteArray> = emptyMap(),
    val preferences: FakeUserPreferencesRepository? = null,
    naming: FileSystemNaming = FileSystemNaming.EXACT,
) {

    val library = FakeLibraryFileLocalSource(files, naming = naming)
    private val lock = LibraryFileLock()
    private val preferencesSync = preferences?.let { SyncedPreferencesSync(it, library, Logger.Standard) }
    private var index = SyncIndexDocument()

    val sharedLinks = FakeSharedLinksLocalSource()
    val sharedContents = FakeSharedCopyContents()
    var buildNumber = 1

    /** Whether the last run's shared-copy step settled every copy. */
    var areSharedCopiesSettled = true
        private set

    /**
     * What the mirror, the journal, and the sharing actions use. Unconfined, so that what a sharing action applies
     * without waiting (`SharedLinksJournalApplier.applySoon`) runs in the caller's turn, as a test can replay it.
     */
    private val environment = SyncEnvironment(
        context = Dispatchers.Unconfined,
        timeSource = TimeSource.Monotonic,
        clock = Clock.System,
        computation = Dispatchers.Unconfined,
        logger = Logger.Standard,
    )

    private val mirror = SharedLinksMirror(sharedLinks, environment)
    private val registry = SharedLinksRegistry(mirror, environment)
    private val sharedLinksJournal = SharedLinksJournal(sharedLinks, environment)
    private var sharingRepository: SharedLinksRepositoryImpl? = null

    /** The sharing actions against [cloud], connected; one repository per device, as the app has one. */
    fun sharing(cloud: FakeSyncProvider) = sharingRepository ?: SharedLinksRepositoryImpl(
        syncProviders = SyncProviders(listOf(cloud)) { cloud.id },
        stateHolder = SyncStateHolder(Logger.Standard).apply {
            update { SyncState.Connected(account = TEST_ACCOUNT, progress = null, lastSyncedAt = null, lastOutcome = null) }
        },
        mirror = mirror,
        registry = registry,
        journal = sharedLinksJournal,
        journalApplier = SharedLinksJournalApplier(sharedLinksJournal, registry, environment),
        environment = environment,
    ).also { sharingRepository = it }

    /** The library by file name, as text. */
    val contents get() = library.files.entries.associate { (key, bytes) -> key.name to bytes.decodeToString() }

    /** A run that must complete, after which the index is kept and the synced preferences and shared copies settled. */
    suspend fun sync(cloud: FakeSyncProvider, deletionPolicy: SyncDeletionPolicy = SyncDeletionPolicy.ASK) {
        assertIs<SyncEngine.Result.Completed>(attempt(cloud, deletionPolicy))
    }

    /**
     * A run however it ends, keeping the index the way `SyncRunner` does: a completed run's, with the synced preferences
     * and the shared copies settled after it, and otherwise the last one the engine reported, so that a run that stops,
     * fails or asks first keeps the files it did move. Every index the run reported is kept in [writtenIndexes] as well.
     */
    suspend fun attempt(cloud: FakeSyncProvider, deletionPolicy: SyncDeletionPolicy): SyncEngine.Result {
        var reported: (() -> SyncIndexDocument)? = null
        writtenIndexes.clear()
        writtenIndexes += index
        val result = try {
            synchronize(
                local = library,
                provider = cloud,
                document = index,
                setlistComparison = DayBlindSetlistComparison,
                lock = lock,
                onIndexChanged = { snapshot ->
                    reported = snapshot
                    writtenIndexes += snapshot()
                },
                deletionPolicy = deletionPolicy,
            )
        } catch (throwable: Throwable) {
            reported?.let { index = it().markedAsFinished() }
            throw throwable
        }
        when (result) {
            is SyncEngine.Result.DeletionsNeedConfirmation -> reported?.let { index = it().markedAsFinished() }
            is SyncEngine.Result.Completed -> settle(cloud, result)
        }
        return result
    }

    /**
     * The indexes the last [attempt] could have left on disk: the one it started from, and every one it reported since,
     * any of which `SyncRunner`'s throttled writer may have been the last to save.
     */
    private val writtenIndexes = mutableListOf<SyncIndexDocument>()

    /**
     * The app killed during or right after the last [attempt], before it wrote the index it ended with: what the next
     * launch finds is one of the [writtenIndexes], picked by [random], which the launch marks as finished.
     */
    fun killed(random: Random) {
        index = writtenIndexes.random(random).markedAsFinished()
    }

    private suspend fun settle(cloud: FakeSyncProvider, completed: SyncEngine.Result.Completed) {
        val settled = preferencesSync?.synchronize(
            provider = cloud,
            base = completed.index.syncedPreferences,
            unchanged = index.unchangedPreferences(ACCOUNT_ID, completed.documentRevisions),
            keptFileNames = completed.summary.failed,
            onLocalSettled = {},
        )
        areSharedCopiesSettled = SharedCopySync(
            libraryFileLocalSource = library,
            contents = sharedContents,
            registry = registry,
            mirror = mirror,
            journal = sharedLinksJournal,
            logger = Logger.Standard,
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

    /** Disconnecting and connecting the same account again, which forgets the index, the mirror, and the journal. */
    suspend fun reconnect() {
        index = SyncIndexDocument()
        mirror.clear()
        sharedLinksJournal.clear()
    }

    /** How many files the index says the last run saw, which is what the deletion guard measures a run's deletions against. */
    val indexSize get() = index.toIndex().size

    /** What the app's rename and deletion of a shared item leave for the next run. */
    suspend fun journal(entry: SharedLinksJournal.Entry) = sharedLinksJournal.append(listOf(entry))

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
