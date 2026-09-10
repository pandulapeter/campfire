package com.pandulapeter.campfire.data.source.remote.implementation.auth

import com.pandulapeter.campfire.data.source.local.api.SyncStateLocalSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * The one place the credentials document is read and written. Cached in memory (every request needs the access
 * token) and guarded by a mutex, so that two requests noticing an expired token at the same time cannot both start
 * a refresh and have the loser overwrite the winner's tokens.
 */
internal class SyncCredentialsStore(
    private val syncStateLocalSource: SyncStateLocalSource
) {

    private val mutex = Mutex()
    private var cached: SyncCredentialsDocument? = null
    private var hasRead = false

    suspend fun load(): SyncCredentialsDocument? = mutex.withLock { read() }

    suspend fun save(document: SyncCredentialsDocument?) = mutex.withLock { write(document) }

    /**
     * Reads, changes and writes the document as one step. The whole update runs under the lock, which is what makes
     * a token refresh safe to start from several requests at once.
     */
    suspend fun <T> update(block: suspend (SyncCredentialsDocument?) -> Pair<SyncCredentialsDocument?, T>): T = mutex.withLock {
        val (updated, result) = block(read())
        write(updated)
        result
    }

    private suspend fun read(): SyncCredentialsDocument? {
        if (!hasRead) {
            cached = try {
                syncStateLocalSource.loadSyncCredentials()?.let { json.decodeFromString<SyncCredentialsDocument>(it) }
            } catch (exception: Exception) {
                println("Could not read the sync credentials: ${exception.message}")
                null
            }
            hasRead = true
        }
        return cached
    }

    private suspend fun write(document: SyncCredentialsDocument?) {
        cached = document
        hasRead = true
        syncStateLocalSource.saveSyncCredentials(document?.let { json.encodeToString(it) })
    }

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            encodeDefaults = true
        }
    }
}
