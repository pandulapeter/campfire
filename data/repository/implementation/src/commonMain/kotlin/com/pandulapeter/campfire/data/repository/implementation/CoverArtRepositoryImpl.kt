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

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.CoverArtQuery
import com.pandulapeter.campfire.data.model.domain.CoverArtSearchResults
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.repository.api.CoverArtRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.data.source.local.api.CoverArtLocalSource
import com.pandulapeter.campfire.data.source.remote.api.CoverArtRemoteSource
import com.pandulapeter.campfire.data.source.remote.api.CoverArtSearchRemoteSources
import com.pandulapeter.campfire.data.source.remote.api.hashing.Sha256
import com.pandulapeter.campfire.data.source.remote.api.model.CoverArtDownload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/**
 * The copies are named after the SHA-256 of their address, which is a name every storage can hold whatever the
 * address looks like, and the same on every platform.
 *
 * A download runs in the repository's own scope rather than in the caller's, so that the list scrolling a row out of
 * view — which cancels the request that row made — does not throw away a download another row, or the same row a
 * moment later, is waiting for.
 *
 * At most [MAX_CONCURRENT_DOWNLOADS] of them reach the network at a time, and one whose every asker has gone by the time
 * it gets its turn is not made at all. A list flung past a few hundred rows whose covers are not on the device yet would
 * otherwise queue a request for every one of them in the HTTP client, and the rows it stops on would wait behind all
 * of them — long enough for the client's timeout, which counts the time spent queued, to fail them and put their
 * addresses in the failure memory for a minute. One that has started is carried to its end whoever is still waiting.
 *
 * A search asks every source side by side, each in a coroutine of the collector's own, so that closing the sheet stops
 * all of them, and one that fails is recorded as failed rather than ending the others.
 */
@Single
internal class CoverArtRepositoryImpl(
    private val coverArtLocalSource: CoverArtLocalSource,
    private val coverArtRemoteSource: CoverArtRemoteSource,
    private val coverArtSearchRemoteSources: CoverArtSearchRemoteSources,
    songRepository: SongRepository,
) : CoverArtRepository {

    /** Nothing launched here has anybody to throw to, see `SyncRepositoryImpl`. */
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, throwable ->
            println("A cover art job ended in an exception nothing caught: $throwable")
        },
    )
    private val mutex = Mutex()
    private val downloads = mutableMapOf<String, Download>()
    private val downloadSlots = Semaphore(MAX_CONCURRENT_DOWNLOADS)

    /** When each address that failed may be asked again, null for never in this session. */
    private val failures = mutableMapOf<String, ComparableTimeMark?>()

    /** Counts the writes and prunes of the copies, which is what makes [coverArtCacheSize] list them again. */
    private val cacheChanges = MutableStateFlow(0L)

    // A StateFlow conflates what arrives while its collector is busy, which is what keeps this to one listing at a time.
    override val coverArtCacheSize = cacheChanges.map { coverArtLocalSource.getCoverArtCacheSize() }

    init {
        // Only a library that has been read to its end says which copies are wanted: the scan publishes its batches
        // as Loading, and a copy pruned on the strength of half a library would be downloaded again a moment later.
        scope.launch {
            songRepository.songs
                .filterIsInstance<DataState.Idle<List<Song>>>()
                .map { state -> state.data.mapNotNullTo(mutableSetOf()) { song -> song.coverArtUrl?.let(::keyOf) } }
                .distinctUntilChanged()
                .collect { keys ->
                    coverArtLocalSource.keepOnlyCoverArt(keys)
                    cacheChanges.update { it + 1 }
                }
        }
    }

    override suspend fun getCoverArt(url: String): ByteArray? {
        val download = mutex.withLock {
            if (failures.containsKey(url)) {
                val retryAt = failures[url]
                if (retryAt == null || retryAt.hasNotPassedNow()) return null
                failures.remove(url)
            }
            downloads.getOrPut(url) {
                Download().also { download -> download.deferred = scope.async { load(url, download) } }
            }.also { it.waiters++ }
        }
        return try {
            download.deferred.await()
        } finally {
            // The caller is usually being cancelled here, and a count left one too high would make the download of an
            // address nobody waits for any more look wanted.
            withContext(NonCancellable) { mutex.withLock { download.waiters-- } }
        }
    }

    override fun searchCoverArt(query: CoverArtQuery): Flow<CoverArtSearchResults> = channelFlow {
        val resultsMutex = Mutex()
        var results = CoverArtSearchResults(
            candidates = emptyList(),
            pending = coverArtSearchRemoteSources.all.mapTo(mutableSetOf()) { it.service },
            busy = emptySet(),
            failed = emptySet(),
        )
        val update: suspend (CoverArtSearchResults.() -> CoverArtSearchResults) -> Unit = { transform ->
            resultsMutex.withLock {
                results = results.transform()
                send(results)
            }
        }
        send(results)
        coverArtSearchRemoteSources.all.forEach { source ->
            val service = source.service
            launch {
                val candidates = try {
                    source.searchCoverArt(
                        query = query,
                        // A wait reported by a source that has answered since is not one anybody is still waiting on.
                        onBusy = { launch { update { if (service in pending) copy(busy = busy + service) else this } } },
                    )
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    println("The cover search on $service failed: ${exception::class.simpleName}")
                    null
                }
                update {
                    copy(
                        candidates = this.candidates + candidates.orEmpty(),
                        pending = pending - service,
                        busy = busy - service,
                        failed = if (candidates == null) failed + service else failed,
                    )
                }
            }
        }
    }

    override suspend fun clearCoverArtCache() {
        coverArtLocalSource.keepOnlyCoverArt(emptySet())
        mutex.withLock { failures.clear() }
        cacheChanges.update { it + 1 }
    }

    private suspend fun load(url: String, download: Download): ByteArray? = try {
        val key = keyOf(url)
        coverArtLocalSource.loadCoverArt(key) ?: downloadSlots.withPermit {
            // An abandoned download records no failure, so that the next asker starts afresh; it leaves the map at
            // once, under the same lock, so that nobody joins it in the moment before it answers null.
            val isAbandoned = mutex.withLock {
                (download.waiters == 0).also { isAbandoned -> if (isAbandoned) downloads.remove(url) }
            }
            if (isAbandoned) null else fetch(url, key)
        }
    } finally {
        mutex.withLock { if (downloads[url] === download) downloads.remove(url) }
    }

    private suspend fun fetch(url: String, key: String) = when (val download = coverArtRemoteSource.downloadCoverArt(url)) {
        is CoverArtDownload.Image -> download.bytes.also {
            coverArtLocalSource.saveCoverArt(key, it)
            cacheChanges.update { changes -> changes + 1 }
        }
        CoverArtDownload.Missing -> null.also { mutex.withLock { failures[url] = null } }
        CoverArtDownload.Unreachable -> null.also { mutex.withLock { failures[url] = TimeSource.Monotonic.markNow() + UNREACHABLE_RETRY_DELAY } }
    }

    private fun keyOf(url: String) = Sha256.hashToHex(url.encodeToByteArray())

    /** One address's download and how many callers are waiting for it, both guarded by [mutex]. */
    private class Download {
        lateinit var deferred: Deferred<ByteArray?>
        var waiters = 0
    }

    companion object {
        /** Low enough that the covers a list stops on are not kept waiting behind a queue of the ones it went past. */
        const val MAX_CONCURRENT_DOWNLOADS = 4
        private val UNREACHABLE_RETRY_DELAY = 1.minutes
    }
}
