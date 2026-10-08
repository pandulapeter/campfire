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

import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Files a run cannot or must not move: one too large to be a song, one that cannot be read here, and one in the
 * cloud folder that is not a library file at all. None of them is ever taken for a deletion.
 */
class SyncEngineUnreadableFileTest {

    @Test
    fun `a local file too large to sync is neither uploaded nor taken for a deletion`() = runTest {
        val synced = "Synced".encodeToByteArray()
        val local = FakeLibraryFileLocalSource(files = mapOf(song(1) to tooLarge()))
        var uploads = 0
        val provider = FakeSyncProvider(files = mapOf(song(1) to synced), onUpload = { uploads++ })

        val result = synchronize(local, provider, indexOf(song(1) to synced))

        assertEquals(0, uploads)
        assertContentEquals(synced, provider.files.getValue(song(1)).first)
        assertEquals(listOf(song(1).name), assertIs<SyncEngine.Result.Completed>(result).summary.failed)
    }

    @Test
    fun `a local file too large to sync is not uploaded when it is new`() = runTest {
        val local = FakeLibraryFileLocalSource(files = mapOf(song(1) to tooLarge()))
        val provider = FakeSyncProvider()

        val result = synchronize(local, provider, SyncIndexDocument())

        assertTrue(provider.files.isEmpty())
        assertEquals(listOf(song(1).name), assertIs<SyncEngine.Result.Completed>(result).summary.failed)
    }

    @Test
    fun `a file that is there but cannot be read is named and neither deleted nor overwritten`() = runTest {
        val library = librarySongs(3)
        val local = FakeLibraryFileLocalSource(
            files = library,
            onRead = { key -> if (key == song(2)) throw LibraryStorageException("Locked") },
        )
        val provider = FakeSyncProvider(files = library)
        val edit = "Three, edited".encodeToByteArray()
        provider.files[song(3)] = edit to "r5"

        val logger = RecordingLogger()

        val completed = assertIs<SyncEngine.Result.Completed>(synchronize(local, provider, syncedIndexOf(library), logger = logger))

        assertEquals(listOf("song_2.cho"), completed.summary.failed)
        assertTrue(logger.lines.any { song(2).path in it })
        assertContentEquals(library.getValue(song(2)), provider.files.getValue(song(2)).first)
        assertContentEquals(edit, local.files.getValue(song(3)))
        assertNull(provider.downloadCounts[song(2)])
        assertTrue(song(2).path in completed.index.entries)
    }

    @Test
    fun `a remote edit to a file that cannot be read here is not downloaded over it`() = runTest {
        val library = librarySongs(3)
        val local = FakeLibraryFileLocalSource(
            files = library,
            onRead = { key -> if (key == song(1)) throw LibraryStorageException("Locked") },
        )
        val provider = FakeSyncProvider(files = library)
        provider.files[song(1)] = "One, edited".encodeToByteArray() to "r5"

        val completed = assertIs<SyncEngine.Result.Completed>(synchronize(local, provider, syncedIndexOf(library)))

        assertNull(provider.downloadCounts[song(1)])
        assertContentEquals(library.getValue(song(1)), local.files.getValue(song(1)))
        assertTrue("song_1.cho" in completed.summary.failed)
    }

    @Test
    fun `a library of which no file can be read ends the run as a storage failure`() = runTest {
        val library = librarySongs(3)
        val local = FakeLibraryFileLocalSource(files = library, onRead = { throw LibraryStorageException("Locked") })
        val provider = FakeSyncProvider(files = library)

        assertFailsWith<LibraryStorageException> { synchronize(local, provider, syncedIndexOf(library)) }

        assertEquals(library.keys, provider.files.keys)
    }

    @Test
    fun `a file that cannot be read is not taken for a deletion when the index knows it`() = runTest {
        val library = librarySongs(3)
        val local = FakeLibraryFileLocalSource(
            files = library,
            onRead = { key -> if (key == song(1)) throw LibraryStorageException("Locked") },
        )
        val provider = FakeSyncProvider(files = library)

        synchronize(local, provider, syncedIndexOf(library))

        assertTrue(song(1) in provider.files)
        assertTrue(provider.deleteCalls.isEmpty())
    }

    @Test
    fun `a remote file that is not a library file is left where it is`() = runTest {
        val libraryFile = song(1)
        val foreignFile = foreign("wonderwall.pdf")
        val provider = FakeSyncProvider(
            files = mapOf(
                libraryFile to "Song".encodeToByteArray(),
                foreignFile to "PDF".encodeToByteArray(),
            ),
        )
        val local = FakeLibraryFileLocalSource()

        val result = synchronize(local, provider, SyncIndexDocument())

        val completed = assertIs<SyncEngine.Result.Completed>(result)
        assertEquals(setOf(libraryFile), local.files.keys)
        assertEquals(setOf(libraryFile, foreignFile), provider.files.keys)
        assertEquals(setOf(libraryFile.path), completed.index.entries.keys)
        assertEquals(1, completed.summary.downloaded)
    }

    @Test
    fun `a foreign file an earlier run indexed is forgotten rather than deleted remotely`() = runTest {
        val key = foreign("wonderwall.pdf")
        val bytes = "PDF".encodeToByteArray()
        val provider = FakeSyncProvider(files = mapOf(key to bytes))

        val result = synchronize(
            local = FakeLibraryFileLocalSource(),
            provider = provider,
            document = indexOf(key to bytes).let { document ->
                document.copy(entries = document.entries.mapValues { (_, entry) -> entry.copy(remoteRevision = "r1") })
            },
        )

        val completed = assertIs<SyncEngine.Result.Completed>(result)
        assertEquals(setOf(key), provider.files.keys)
        assertEquals(0, completed.summary.deletedRemotely)
        assertTrue(completed.index.entries.isEmpty())
    }

    @Test
    fun `the local copy of a foreign file an earlier run downloaded is removed while the remote one is still there`() = runTest {
        val key = foreign("wonderwall.pdf")
        val bytes = "PDF".encodeToByteArray()
        val local = FakeLibraryFileLocalSource(files = mapOf(key to bytes))
        val provider = FakeSyncProvider(files = mapOf(key to bytes))

        synchronize(
            local = local,
            provider = provider,
            document = indexOf(key to bytes).let { document ->
                document.copy(entries = document.entries.mapValues { (_, entry) -> entry.copy(remoteRevision = "r1") })
            },
        )

        assertTrue(key !in local.files)
        assertEquals(setOf(key), provider.files.keys)
    }

    @Test
    fun `a changed local copy of a foreign file an earlier run downloaded is kept`() = runTest {
        val key = foreign("wonderwall.pdf")
        val indexed = "PDF".encodeToByteArray()
        val changed = "Changed".encodeToByteArray()
        val local = FakeLibraryFileLocalSource(files = mapOf(key to changed))
        val provider = FakeSyncProvider(files = mapOf(key to indexed))

        synchronize(
            local = local,
            provider = provider,
            document = indexOf(key to indexed).let { document ->
                document.copy(entries = document.entries.mapValues { (_, entry) -> entry.copy(remoteRevision = "r1") })
            },
        )

        assertContentEquals(changed, local.files[key])
        assertEquals(setOf(key), provider.files.keys)
    }

    @Test
    fun `a remote file too large to be a song is not downloaded`() = runTest {
        val provider = FakeSyncProvider(
            files = mapOf(
                song(1) to "One".encodeToByteArray(),
                song(2) to "Two".encodeToByteArray(),
            ),
            sizes = mapOf(song(2) to (9L shl 20)),
            onDownload = { if (it == song(2)) fail("Downloaded") },
        )
        val local = FakeLibraryFileLocalSource()

        val result = synchronize(local, provider, SyncIndexDocument())

        val completed = assertIs<SyncEngine.Result.Completed>(result)
        assertEquals(setOf(song(1)), local.files.keys)
        assertEquals(setOf(song(1).path), completed.index.entries.keys)
        assertEquals(setOf(song(1), song(2)), provider.files.keys)
        assertEquals(1, completed.summary.downloaded)
    }

    @Test
    fun `a remote file that grew too large does not take the local one with it`() = runTest {
        val original = "Original".encodeToByteArray()
        val local = FakeLibraryFileLocalSource(files = mapOf(song(1) to original))
        val provider = FakeSyncProvider(
            files = mapOf(song(1) to "Changed remotely".encodeToByteArray()),
            sizes = mapOf(song(1) to (9L shl 20)),
        )
        val document = indexOf(song(1) to original)

        val result = synchronize(local, provider, document)

        val completed = assertIs<SyncEngine.Result.Completed>(result)
        assertContentEquals(original, local.files[song(1)])
        assertEquals(0, completed.summary.deletedLocally)
        assertEquals(document.entries, completed.index.entries)
    }
}
