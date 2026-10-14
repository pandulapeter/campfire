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

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.sync.SyncDeletionPolicy
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLock
import com.pandulapeter.campfire.data.sync.implementation.index.SyncIndexDocument
import com.pandulapeter.campfire.data.sync.implementation.preferences.SyncedPreferencesSync
import com.pandulapeter.campfire.data.sync.implementation.run.SyncEngine
import kotlin.test.assertIs

/**
 * One device connected to a cloud folder that other devices share: its library, the index its runs keep, and, where a
 * test gives it [preferences], the synced part of those settled after every completed run the way `SyncRunner` does it.
 */
internal class Device(
    files: Map<SyncKey, ByteArray> = emptyMap(),
    val preferences: FakeUserPreferencesRepository? = null,
) {

    val library = FakeLibraryFileLocalSource(files)
    private val lock = LibraryFileLock()
    private val preferencesSync = preferences?.let { SyncedPreferencesSync(it, library, Logger.Standard) }
    private var index = SyncIndexDocument()

    /** The library by file name, as text. */
    val contents get() = library.files.entries.associate { (key, bytes) -> key.name to bytes.decodeToString() }

    /** A run that must complete, after which the index is kept and the synced preferences settled. */
    suspend fun sync(cloud: FakeSyncProvider, deletionPolicy: SyncDeletionPolicy = SyncDeletionPolicy.ASK) {
        val completed = assertIs<SyncEngine.Result.Completed>(syncResult(cloud, deletionPolicy))
        val syncedPreferences = preferencesSync?.synchronize(
            provider = cloud,
            base = completed.index.syncedPreferences,
            keptFileNames = completed.summary.failed,
            onLocalSettled = {},
        )
        index = completed.index.copy(syncedPreferences = syncedPreferences ?: completed.index.syncedPreferences)
    }

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
