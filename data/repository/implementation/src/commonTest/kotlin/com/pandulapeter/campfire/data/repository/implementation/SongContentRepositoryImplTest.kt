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

import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.data.source.local.api.SongLocalSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SongContentRepositoryImplTest {

    private val localSource = FakeSongLocalSource()
    private val repository = SongContentRepositoryImpl(localSource)

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

    /** Only the reads are answered, and every one of them is recorded. */
    private class FakeSongLocalSource : SongLocalSource {

        val files = mutableMapOf<String, String>()
        val reads = mutableListOf<String>()

        /** Awaited by a read once it has taken the file's text, before it hands it back. */
        var readGate: CompletableDeferred<Unit>? = null

        override suspend fun loadSongContent(fileName: String): SongContent? {
            reads += fileName
            val text = files[fileName]
            readGate?.await()
            return text?.let { SongContent(fileName = fileName, text = it) }
        }

        override suspend fun loadSongs(onProgress: (List<Song>) -> Unit) = throw UnsupportedOperationException()

        override suspend fun loadSongFileSizes() = throw UnsupportedOperationException()

        override suspend fun loadSong(fileName: String) = throw UnsupportedOperationException()

        override suspend fun saveSongContent(content: SongContent) = throw UnsupportedOperationException()

        override suspend fun createSong(title: String, artist: String, text: String) = throw UnsupportedOperationException()

        override fun importFileName(fallbackTitle: String, text: String) = throw UnsupportedOperationException()

        override suspend fun importSong(fileName: String, text: String, shouldReplace: Boolean) = throw UnsupportedOperationException()

        override suspend fun renameSong(song: Song) = throw UnsupportedOperationException()

        override suspend fun deleteSong(fileName: String) = throw UnsupportedOperationException()

        override suspend fun exists(fileName: String) = throw UnsupportedOperationException()
    }
}
