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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SongContentRepositoryImplTest {

    private val localSource = FakeSongLocalSource()
    private val repository = SongContentRepositoryImpl(localSource, Logger.Standard)

    @Test
    fun `a collector busy reading still learns of every invalidation that arrived meanwhile`() = runTest {
        val isReading = CompletableDeferred<Unit>()
        val canContinue = CompletableDeferred<Unit>()
        val seen = mutableListOf<Long>()
        backgroundScope.launch(Dispatchers.Unconfined) {
            repository.invalidations.collect { revision ->
                seen += revision
                if (seen.size == 1) {
                    isReading.complete(Unit)
                    canContinue.await()
                }
            }
        }
        isReading.await()
        repeat(100) { repository.invalidate(setOf("$it.cho")) }
        repository.invalidate("open.cho")
        canContinue.complete(Unit)

        assertEquals(101L, seen.last())
    }

    @Test
    fun `a cached text is answered without reading the file again`() = runTest {
        localSource.files["a.cho"] = "old"
        repository.loadSongContent("a.cho")
        localSource.files["a.cho"] = "new"

        assertEquals("old", repository.loadSongContent("a.cho")?.text)
        assertEquals(1, localSource.reads.count { it == "a.cho" })
    }

    @Test
    fun `a read past the cache sees the file as it is and leaves the cache alone`() = runTest {
        localSource.files["a.cho"] = "old"
        repository.loadSongContent("a.cho")
        localSource.files["a.cho"] = "new"

        assertEquals("new", repository.loadSongContent("a.cho", useCache = false)?.text)
        assertEquals("old", repository.loadSongContent("a.cho")?.text)
        localSource.files.remove("a.cho")
        assertEquals(null, repository.loadSongContent("a.cho", useCache = false))
    }

    @Test
    fun `only the most recently used songs are kept`() = runTest {
        (0..40).forEach { localSource.files["$it.cho"] = "text" }
        (0..40).forEach { repository.loadSongContent("$it.cho") }
        // Used again, so it is the most recent rather than the oldest.
        repository.loadSongContent("9.cho")
        localSource.reads.clear()

        (0..40).forEach { repository.loadSongContent("$it.cho") }

        assertEquals((0..8).map { "$it.cho" }, localSource.reads.take(9))
        assertEquals(false, "9.cho" in localSource.reads)
    }

    @Test
    fun `a few very long songs are not all kept`() = runTest {
        val long = "x".repeat(400_000)
        (0..3).forEach { localSource.files["$it.cho"] = long }
        (0..3).forEach { repository.loadSongContent("$it.cho") }
        localSource.reads.clear()

        repository.loadSongContent("3.cho")
        repository.loadSongContent("0.cho")

        assertEquals(listOf("0.cho"), localSource.reads)
    }

    @Test
    fun `a text larger than the whole budget is handed out without emptying the cache`() = runTest {
        localSource.files["a.cho"] = "text"
        localSource.files["huge.cho"] = "x".repeat(2_000_000)
        repository.loadSongContent("a.cho")

        assertEquals(2_000_000, repository.loadSongContent("huge.cho")?.text?.length)
        repository.loadSongContent("a.cho")
        repository.loadSongContent("huge.cho")

        assertEquals(listOf("a.cho", "huge.cho", "huge.cho"), localSource.reads)
    }

    @Test
    fun `a read that an invalidation overtook is not cached`() = runTest {
        localSource.files["a.cho"] = "old"
        localSource.readGate = CompletableDeferred()
        val read = backgroundScope.launch { repository.loadSongContent("a.cho") }
        testScheduler.runCurrent()
        localSource.files["a.cho"] = "new"
        repository.invalidate("a.cho")
        localSource.readGate?.complete(Unit)
        read.join()
        localSource.readGate = null

        assertEquals("new", repository.loadSongContent("a.cho")?.text)
    }
}
