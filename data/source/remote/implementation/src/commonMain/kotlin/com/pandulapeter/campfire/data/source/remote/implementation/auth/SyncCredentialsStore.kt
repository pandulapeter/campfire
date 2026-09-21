/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.auth

import com.pandulapeter.campfire.data.source.local.api.SyncStateLocalSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

/**
 * The one place the credentials document is read and written. Cached in memory (every request needs the access
 * token) and guarded by a mutex, so that two requests noticing an expired token at the same time cannot both start
 * a refresh and have the loser overwrite the winner's tokens.
 */
@Single
internal class SyncCredentialsStore(
    private val syncStateLocalSource: SyncStateLocalSource,
) {

    private val mutex = Mutex()
    private var cached: SyncCredentialsDocument? = null
    private var hasRead = false

    suspend fun load(): SyncCredentialsDocument? = mutex.withLock { read() }

    suspend fun save(document: SyncCredentialsDocument?) = mutex.withLock { write(document) }

    /**
     * Reads, changes and (where anything changed) writes the document as one step. The whole update runs under the
     * lock, which is what makes a token refresh safe to start from several requests at once.
     */
    suspend fun <T> update(block: suspend (SyncCredentialsDocument?) -> Pair<SyncCredentialsDocument?, T>): T = mutex.withLock {
        val current = read()
        val (updated, result) = block(current)
        // The common case is a token that is still good, and that is not a change worth a file write.
        if (updated != current) write(updated)
        result
    }

    private suspend fun read(): SyncCredentialsDocument? {
        if (!hasRead) {
            cached = try {
                syncStateLocalSource.loadSyncCredentials()?.let { json.decodeFromString<SyncCredentialsDocument>(it) }
            } catch (exception: CancellationException) {
                // Nothing has been found out yet, so nothing may be remembered: this object outlives whoever was
                // cancelled, and "no credentials" kept from here on is what a later authorization would write its
                // pending state over.
                throw exception
            } catch (exception: Exception) {
                // A parse failure's message quotes the input around where it failed, which here can be a piece of a token.
                println("Could not read the sync credentials: ${exception::class.simpleName}")
                null
            }
            hasRead = true
        }
        return cached
    }

    private suspend fun write(document: SyncCredentialsDocument?) {
        try {
            syncStateLocalSource.saveSyncCredentials(document?.let { json.encodeToString(it) })
        } catch (exception: Exception) {
            // Refused, or cancelled somewhere between starting and being seen to finish: either way what is stored
            // is no longer known here, and a guess would be acted on - a pending authorization that was never
            // written being "cleared" by a second write, a disconnect that did not happen being believed for the
            // rest of the process. The storage is asked again by whoever comes next.
            cached = null
            hasRead = false
            throw exception
        }
        cached = document
        hasRead = true
    }

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            encodeDefaults = true
        }
    }
}
