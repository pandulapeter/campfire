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
import com.pandulapeter.campfire.data.model.domain.CoverArtCandidate
import com.pandulapeter.campfire.data.model.domain.CoverArtQuery
import com.pandulapeter.campfire.data.model.domain.CoverArtSearchResults
import com.pandulapeter.campfire.data.model.domain.CoverArtService
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.repository.implementation.sync.RecordingSongRepository
import com.pandulapeter.campfire.data.source.local.api.CoverArtLocalSource
import com.pandulapeter.campfire.data.source.remote.api.CoverArtRemoteSource
import com.pandulapeter.campfire.data.source.remote.api.CoverArtSearchException
import com.pandulapeter.campfire.data.source.remote.api.CoverArtSearchRemoteSource
import com.pandulapeter.campfire.data.source.remote.api.CoverArtSearchRemoteSources
import com.pandulapeter.campfire.data.source.remote.api.hashing.Sha256
import com.pandulapeter.campfire.data.source.remote.api.model.CoverArtDownload
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoverArtRepositoryImplTest {

    @Test
    fun `a cover is downloaded once and read from the device's copy after that`() = runTest {
        val remote = FakeCoverArtRemoteSource { CoverArtDownload.Image(IMAGE) }
        val local = FakeCoverArtLocalSource()
        val repository = repository(local = local, remote = remote)

        assertContentEquals(IMAGE, repository.getCoverArt(URL))
        assertContentEquals(IMAGE, repository.getCoverArt(URL))

        assertEquals(1, remote.requestCount)
        assertEquals(setOf(keyOf(URL)), local.copies.keys)
    }

    @Test
    fun `callers asking at once share one download`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val remote = FakeCoverArtRemoteSource {
            gate.await()
            CoverArtDownload.Image(IMAGE)
        }
        val repository = repository(remote = remote)

        val results = withContext(Dispatchers.Default) {
            val callers = List(5) { async { repository.getCoverArt(URL) } }
            withTimeout(5_000) { while (remote.requestCount == 0) delay(1) }
            gate.complete(Unit)
            callers.awaitAll()
        }

        results.forEach { assertContentEquals(IMAGE, it) }
        assertEquals(1, remote.requestCount)
    }

    @Test
    fun `an address that is not a cover is not asked again`() = runTest {
        val remote = FakeCoverArtRemoteSource { CoverArtDownload.Missing }
        val repository = repository(remote = remote)

        assertNull(repository.getCoverArt(URL))
        assertNull(repository.getCoverArt(URL))

        assertEquals(1, remote.requestCount)
    }

    @Test
    fun `an address that could not be reached is not asked again right away`() = runTest {
        val remote = FakeCoverArtRemoteSource { CoverArtDownload.Unreachable }
        val repository = repository(remote = remote)

        assertNull(repository.getCoverArt(URL))
        assertNull(repository.getCoverArt(URL))

        assertEquals(1, remote.requestCount)
    }

    @Test
    fun `copies no song names are deleted once the library has been read whole`() = runTest {
        val local = FakeCoverArtLocalSource()
        local.copies[keyOf(URL)] = IMAGE
        local.copies[keyOf(OTHER_URL)] = IMAGE
        val songs = MutableStateFlow<DataState<List<Song>>>(DataState.Loading(listOf(song(OTHER_URL))))
        repository(local = local, songs = songs)

        // Half a library is not a reason to delete anything.
        withContext(Dispatchers.Default) { delay(50) }
        assertEquals(setOf(keyOf(URL), keyOf(OTHER_URL)), local.copies.keys)

        songs.value = DataState.Idle(listOf(song(URL), song(null)))
        withContext(Dispatchers.Default) { withTimeout(5_000) { while (local.copies.size != 1) delay(1) } }
        assertEquals(setOf(keyOf(URL)), local.copies.keys)
    }

    @Test
    fun `a search reports every service as it answers, in the order they answer`() = runTest {
        val musicBrainz = FakeCoverArtSearchRemoteSource(CoverArtService.MUSIC_BRAINZ) { onBusy ->
            onBusy()
            delay(2_000)
            listOf(candidate(CoverArtService.MUSIC_BRAINZ, "a"))
        }
        val iTunes = FakeCoverArtSearchRemoteSource(CoverArtService.ITUNES) {
            delay(100)
            listOf(candidate(CoverArtService.ITUNES, "b"))
        }
        val repository = repository(searchSources = listOf(musicBrainz, iTunes))

        val emissions = repository.searchCoverArt(QUERY).toList()

        assertEquals(
            listOf(
                results(pending = setOf(CoverArtService.MUSIC_BRAINZ, CoverArtService.ITUNES)),
                results(pending = setOf(CoverArtService.MUSIC_BRAINZ, CoverArtService.ITUNES), busy = setOf(CoverArtService.MUSIC_BRAINZ)),
                results(
                    candidates = listOf(candidate(CoverArtService.ITUNES, "b")),
                    pending = setOf(CoverArtService.MUSIC_BRAINZ),
                    busy = setOf(CoverArtService.MUSIC_BRAINZ),
                ),
                results(candidates = listOf(candidate(CoverArtService.ITUNES, "b"), candidate(CoverArtService.MUSIC_BRAINZ, "a"))),
            ),
            emissions,
        )
    }

    @Test
    fun `a service that fails is recorded as failed and does not end the others`() = runTest {
        val failing = FakeCoverArtSearchRemoteSource(CoverArtService.MUSIC_BRAINZ) { throw CoverArtSearchException("No network.") }
        val answering = FakeCoverArtSearchRemoteSource(CoverArtService.ITUNES) {
            delay(100)
            listOf(candidate(CoverArtService.ITUNES, "b"))
        }

        val last = repository(searchSources = listOf(failing, answering)).searchCoverArt(QUERY).toList().last()

        assertTrue(last.isComplete)
        assertEquals(setOf(CoverArtService.MUSIC_BRAINZ), last.failed)
        assertEquals(listOf(candidate(CoverArtService.ITUNES, "b")), last.candidates)
    }

    private fun repository(
        local: FakeCoverArtLocalSource = FakeCoverArtLocalSource(),
        remote: FakeCoverArtRemoteSource = FakeCoverArtRemoteSource { CoverArtDownload.Missing },
        songs: MutableStateFlow<DataState<List<Song>>> = MutableStateFlow(DataState.Loading(null)),
        searchSources: List<CoverArtSearchRemoteSource> = emptyList(),
    ) = CoverArtRepositoryImpl(
        coverArtLocalSource = local,
        coverArtRemoteSource = remote,
        coverArtSearchRemoteSources = CoverArtSearchRemoteSources(all = searchSources),
        songRepository = RecordingSongRepository(songs = songs),
    )

    private class FakeCoverArtSearchRemoteSource(
        override val service: CoverArtService,
        private val answer: suspend (onBusy: () -> Unit) -> List<CoverArtCandidate>,
    ) : CoverArtSearchRemoteSource {
        override suspend fun searchCoverArt(query: CoverArtQuery, onBusy: () -> Unit) = answer(onBusy)
    }

    private class FakeCoverArtLocalSource : CoverArtLocalSource {
        val copies = mutableMapOf<String, ByteArray>()

        override suspend fun loadCoverArt(key: String) = copies[key]

        override suspend fun saveCoverArt(key: String, bytes: ByteArray) {
            copies[key] = bytes
        }

        override suspend fun keepOnlyCoverArt(keys: Set<String>) {
            copies.keys.retainAll(keys)
        }
    }

    private class FakeCoverArtRemoteSource(private val answer: suspend () -> CoverArtDownload) : CoverArtRemoteSource {
        var requestCount = 0

        override suspend fun downloadCoverArt(url: String): CoverArtDownload {
            requestCount++
            return answer()
        }
    }

    private companion object {
        const val URL = "https://coverartarchive.org/release-group/a/front-250"
        const val OTHER_URL = "https://coverartarchive.org/release-group/b/front-250"
        val IMAGE = byteArrayOf(1, 2, 3)

        val QUERY = CoverArtQuery(artist = "Green Day", album = "Nimrod", title = "")

        fun keyOf(url: String) = Sha256.hashToHex(url.encodeToByteArray())

        fun candidate(service: CoverArtService, id: String) = CoverArtCandidate(
            service = service,
            id = id,
            title = "",
            artist = "",
            year = null,
            type = null,
            coverArtUrl = "https://example.com/$id",
        )

        fun results(
            candidates: List<CoverArtCandidate> = emptyList(),
            pending: Set<CoverArtService> = emptySet(),
            busy: Set<CoverArtService> = emptySet(),
        ) = CoverArtSearchResults(candidates = candidates, pending = pending, busy = busy, failed = emptySet())

        fun song(coverArtUrl: String?) = Song(
            fileName = "${coverArtUrl.hashCode()}.cho",
            title = "",
            artist = "",
            key = null,
            transpose = 0,
            tags = emptyList(),
            languages = emptyList(),
            coverArtUrl = coverArtUrl,
            hasChords = false,
            canUpdateFileName = false,
            lastModified = 0L,
            size = 0L,
        )
    }
}
