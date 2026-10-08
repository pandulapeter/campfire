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

import com.pandulapeter.campfire.data.source.local.api.SyncIndexLocalSource
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

/** Reads and writes `sync-index.json` as a [SyncIndexDocument]. Pure I/O: what the index is for is the run's business. */
@Single
internal class SyncIndexStore(
    private val syncIndexLocalSource: SyncIndexLocalSource,
    private val environment: SyncEnvironment,
) {

    /**
     * Throws when the file is there and cannot be read: an index taken for none is a run that undoes every deletion
     * since the last one, and then writes the empty index over the good one. One that reads but does not decode is
     * worth nothing to anybody and starts from nothing, as a device that never synced does.
     */
    suspend fun load(): SyncIndexDocument {
        val text = syncIndexLocalSource.loadSyncIndex() ?: return SyncIndexDocument()
        // Off the caller's thread, which for restore() is the main one: the index has an entry per library file.
        return withContext(environment.computation) {
            environment.logger.recovering(
                describe = { "Could not decode the sync index: ${it.message}" },
                fallback = { SyncIndexDocument() },
            ) { json.decodeFromString<SyncIndexDocument>(text) }
        }
    }

    /** For the callers that only show what the index says or check whose it is, and must not throw because of it. */
    suspend fun loadOrNull() = environment.logger.recovering(
        describe = { "Could not read the sync index: ${it.message}" },
        fallback = { null },
    ) { load() }

    suspend fun save(document: SyncIndexDocument) =
        syncIndexLocalSource.saveSyncIndex(withContext(environment.computation) { json.encodeToString(document) })

    /** Deletes the index, which describes a remote folder this device is no longer looking at. */
    suspend fun clear() = syncIndexLocalSource.saveSyncIndex(null)

    /**
     * For the writes nobody is waiting on the result of - the periodic one and the ones made on the way out of a run.
     * The index only ever saves work: whatever it fails to record looks unsynced to the next run, which is a slower
     * run and not a wrong one, so a failure here is not worth more than a line in the log. A run whose storage is
     * really gone still says so, through the opening write and the one that completes it.
     */
    suspend fun saveQuietly(document: SyncIndexDocument) = environment.logger.recovering(
        describe = { "Could not write the sync index: ${it.message}" },
        fallback = {},
    ) { save(document) }

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            encodeDefaults = true
        }
    }
}
