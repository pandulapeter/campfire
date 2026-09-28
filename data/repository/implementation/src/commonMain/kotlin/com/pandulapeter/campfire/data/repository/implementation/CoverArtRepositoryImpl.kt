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
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.repository.api.CoverArtRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.data.source.local.api.CoverArtLocalSource
import com.pandulapeter.campfire.data.source.remote.api.CoverArtRemoteSource
import com.pandulapeter.campfire.data.source.remote.api.CoverArtSearchRemoteSource
import com.pandulapeter.campfire.data.source.remote.api.hashing.Sha256
import com.pandulapeter.campfire.data.source.remote.api.model.CoverArtDownload
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 */
@Single
internal class CoverArtRepositoryImpl(
    private val coverArtLocalSource: CoverArtLocalSource,
    private val coverArtRemoteSource: CoverArtRemoteSource,
    private val coverArtSearchRemoteSource: CoverArtSearchRemoteSource,
    songRepository: SongRepository,
) : CoverArtRepository {

    /** Nothing launched here has anybody to throw to, see `SyncRepositoryImpl`. */
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, throwable ->
            println("A cover art job ended in an exception nothing caught: $throwable")
        },
    )
    private val mutex = Mutex()
    private val downloads = mutableMapOf<String, Deferred<ByteArray?>>()

    /** When each address that failed may be asked again, null for never in this session. */
    private val failures = mutableMapOf<String, ComparableTimeMark?>()

    init {
        // Only a library that has been read to its end says which copies are wanted: the scan publishes its batches
        // as Loading, and a copy pruned on the strength of half a library would be downloaded again a moment later.
        scope.launch {
            songRepository.songs
                .filterIsInstance<DataState.Idle<List<Song>>>()
                .map { state -> state.data.mapNotNullTo(mutableSetOf()) { song -> song.coverArtUrl?.let(::keyOf) } }
                .distinctUntilChanged()
                .collect { keys -> coverArtLocalSource.keepOnlyCoverArt(keys) }
        }
    }

    override suspend fun getCoverArt(url: String): ByteArray? {
        val download = mutex.withLock {
            if (failures.containsKey(url)) {
                val retryAt = failures[url]
                if (retryAt == null || retryAt.hasNotPassedNow()) return null
                failures.remove(url)
            }
            downloads.getOrPut(url) { scope.async { load(url) } }
        }
        return download.await()
    }

    override suspend fun searchCoverArt(query: CoverArtQuery, onBusy: () -> Unit) = coverArtSearchRemoteSource.searchCoverArt(query, onBusy)

    private suspend fun load(url: String): ByteArray? = try {
        val key = keyOf(url)
        coverArtLocalSource.loadCoverArt(key) ?: when (val download = coverArtRemoteSource.downloadCoverArt(url)) {
            is CoverArtDownload.Image -> download.bytes.also { coverArtLocalSource.saveCoverArt(key, it) }
            CoverArtDownload.Missing -> null.also { mutex.withLock { failures[url] = null } }
            CoverArtDownload.Unreachable -> null.also { mutex.withLock { failures[url] = TimeSource.Monotonic.markNow() + UNREACHABLE_RETRY_DELAY } }
        }
    } finally {
        mutex.withLock { downloads.remove(url) }
    }

    private fun keyOf(url: String) = Sha256.hashToHex(url.encodeToByteArray())

    private companion object {
        val UNREACHABLE_RETRY_DELAY = 1.minutes
    }
}
