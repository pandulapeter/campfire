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

import com.pandulapeter.campfire.data.model.domain.LibraryFileKind
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLock
import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import com.pandulapeter.campfire.data.source.remote.api.SyncNetworkException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A file changed on both sides, or changed here while a run is moving it: the version that loses is always kept next
 * to the other, never written over or deleted, and never left only in memory.
 */
class SyncEngineConflictTest {

    @Test
    fun `a song edited while it waits to come down is kept next to the incoming version`() = runTest {
        val original = "Original".encodeToByteArray()
        val edited = "Edited here".encodeToByteArray()
        val incoming = "Edited there".encodeToByteArray()
        val local = FakeLibraryFileLocalSource(files = mapOf(song(1) to original, song(2) to original))
        val provider = FakeSyncProvider(files = mapOf(song(1) to incoming, song(2) to incoming))
        // Both songs were in step at the last run and have since moved on remotely, so both are planned as downloads;
        // the first one's transfer is where the user saves an edit to the second.
        provider.onDownload = { key -> if (key == song(1)) local.files[song(2)] = edited }

        synchronize(local, provider, indexOf(song(1) to original, song(2) to original))

        assertContentEquals(edited, local.files[song(2)])
        assertContentEquals(incoming, local.files[SyncKey(kind = LibraryFileKind.SONG, name = "song_2 (2).cho")])
        assertContentEquals(edited, provider.files.getValue(song(2)).first)
    }

    @Test
    fun `a conflict copy is not written over by a download waiting under its name`() = runTest {
        val copy = SyncKey(kind = LibraryFileKind.SONG, name = "song_1 (2).cho")
        val local = FakeLibraryFileLocalSource(files = mapOf(song(1) to "A's second edit".encodeToByteArray()))
        val provider = FakeSyncProvider(
            files = mapOf(song(1) to "B's edit".encodeToByteArray(), copy to "A's first edit".encodeToByteArray()),
        )

        val result = synchronize(local, provider, indexOf(song(1) to "Original".encodeToByteArray()))

        val expected = setOf("A's second edit", "B's edit", "A's first edit")
        assertEquals("A's second edit", local.files.getValue(song(1)).decodeToString())
        assertEquals(expected, local.files.values.map { it.decodeToString() }.toSet())
        assertEquals(expected, provider.files.values.map { it.first.decodeToString() }.toSet())
        // The copy takes the first name free on both sides, so the file waiting under "(2)" comes down as it is.
        val nextCopy = SyncKey(kind = LibraryFileKind.SONG, name = "song_1 (3).cho")
        assertEquals("A's first edit", local.files.getValue(copy).decodeToString())
        assertEquals("A's first edit", provider.files.getValue(copy).first.decodeToString())
        assertEquals("B's edit", local.files.getValue(nextCopy).decodeToString())
        assertEquals("B's edit", provider.files.getValue(nextCopy).first.decodeToString())
        assertEquals(listOf(nextCopy.name), assertIs<SyncEngine.Result.Completed>(result).summary.conflicts)
    }

    @Test
    fun `a song created under a name that is waiting to come down is kept`() = runTest {
        val mine = "Mine".encodeToByteArray()
        val theirs = "Theirs".encodeToByteArray()
        val local = FakeLibraryFileLocalSource()
        val provider = FakeSyncProvider(files = mapOf(song(1) to "One".encodeToByteArray(), song(2) to theirs))
        provider.onDownload = { key -> if (key == song(1)) local.files[song(2)] = mine }

        synchronize(local, provider, SyncIndexDocument())

        assertContentEquals(mine, local.files[song(2)])
        assertContentEquals(theirs, local.files[SyncKey(kind = LibraryFileKind.SONG, name = "song_2 (2).cho")])
    }

    @Test
    fun `a song saved while its own download is in flight is kept next to the incoming version`() = runTest {
        val original = "Original".encodeToByteArray()
        val edited = "Edited here".encodeToByteArray()
        val incoming = "Edited there".encodeToByteArray()
        val local = FakeLibraryFileLocalSource(files = mapOf(song(1) to original))
        val provider = FakeSyncProvider(files = mapOf(song(1) to incoming))
        provider.onDownload = { key -> if (key == song(1)) local.files[song(1)] = edited }

        synchronize(local, provider, indexOf(song(1) to original))

        assertContentEquals(edited, local.files[song(1)])
        assertContentEquals(incoming, local.files[SyncKey(kind = LibraryFileKind.SONG, name = "song_1 (2).cho")])
        assertContentEquals(edited, provider.files.getValue(song(1)).first)
        assertEquals(1, provider.downloadCounts[song(1)])
    }

    @Test
    fun `a song created under the name while its download is in flight is kept`() = runTest {
        val mine = "Mine".encodeToByteArray()
        val theirs = "Theirs".encodeToByteArray()
        val local = FakeLibraryFileLocalSource()
        val provider = FakeSyncProvider(files = mapOf(song(2) to theirs))
        provider.onDownload = { key -> if (key == song(2)) local.files[song(2)] = mine }

        synchronize(local, provider, SyncIndexDocument())

        assertContentEquals(mine, local.files[song(2)])
        assertContentEquals(theirs, local.files[SyncKey(kind = LibraryFileKind.SONG, name = "song_2 (2).cho")])
    }

    @Test
    fun `a song saved between the last check of its download and the write is not written over`() = runTest {
        val local = FakeLibraryFileLocalSource(files = mapOf(song(1) to ORIGINAL))
        val lock = LibraryFileLock()
        val provider = FakeSyncProvider(files = mapOf(song(1) to THERE))
        // The save starts once the engine has decided the file is still the one it saw, and gets as far as it can
        // before the engine writes the download.
        var save: Job? = null
        local.onWrite = { key ->
            if (key == song(1) && save == null) {
                save = launch { saveUnderTheLock(lock, local, song(1), HERE) }
                yield()
            }
        }

        synchronize(local, provider, indexOf(song(1) to ORIGINAL), lock = lock)
        save?.join()

        assertContentEquals(HERE, local.files[song(1)])
    }

    @Test
    fun `a song saved between the last check of its deletion and the deletion is not deleted`() = runTest {
        val local = FakeLibraryFileLocalSource(files = mapOf(song(1) to ORIGINAL, song(2) to ORIGINAL))
        val lock = LibraryFileLock()
        val provider = FakeSyncProvider(files = mapOf(song(2) to ORIGINAL))
        var save: Job? = null
        local.onDelete = { key ->
            if (key == song(1) && save == null) {
                save = launch { saveUnderTheLock(lock, local, song(1), HERE) }
                yield()
            }
        }

        synchronize(local, provider, syncedIndexOf(mapOf(song(1) to ORIGINAL, song(2) to ORIGINAL)), lock = lock)
        save?.join()

        assertContentEquals(HERE, local.files[song(1)])
    }

    @Test
    fun `a song edited while it waits to be deleted goes back up instead`() = runTest {
        val original = "Original".encodeToByteArray()
        val edited = "Edited here".encodeToByteArray()
        val local = FakeLibraryFileLocalSource(files = mapOf(song(1) to original, song(2) to original))
        val provider = FakeSyncProvider(files = mapOf(song(1) to "Edited there".encodeToByteArray()))
        // The second song is gone remotely and unchanged here, which plans a local deletion; it is edited while the
        // first one comes down, before the deletions get their turn.
        provider.onDownload = { local.files[song(2)] = edited }

        val result = synchronize(local, provider, indexOf(song(1) to original, song(2) to original))

        assertContentEquals(edited, local.files[song(2)])
        assertContentEquals(edited, provider.files.getValue(song(2)).first)
        assertEquals(0, assertIs<SyncEngine.Result.Completed>(result).summary.deletedLocally)
    }

    @Test
    fun `a conflict that is contested while it is resolved leaves one copy rather than two`() = runTest {
        val original = "Original".encodeToByteArray()
        val here = "Edited here".encodeToByteArray()
        val there = "Edited there".encodeToByteArray()
        val local = FakeLibraryFileLocalSource(files = mapOf(song(1) to here))
        val provider = FakeSyncProvider(files = mapOf(song(1) to there))
        // Another device writes the file once while this one is downloading it, so the first upload is refused.
        var isContested = true
        provider.onDownload = { key ->
            if (isContested) {
                isContested = false
                provider.files[key] = there to "r9"
            }
        }

        synchronize(local, provider, indexOf(song(1) to original))

        assertEquals(setOf("song_1.cho", "song_1 (2).cho"), local.files.keys.map { it.name }.toSet())
        assertContentEquals(here, local.files[song(1)])
    }

    @Test
    fun `a conflict whose copy cannot be written leaves the remote version alone`() = runTest {
        val local = FakeLibraryFileLocalSource(
            files = mapOf(song(1) to HERE),
            onWrite = { key -> if (key != song(1)) throw LibraryStorageException("Full") },
        )
        val provider = FakeSyncProvider(files = mapOf(song(1) to THERE))

        val result = synchronize(local, provider, indexOf(song(1) to ORIGINAL))

        assertContentEquals(THERE, provider.files.getValue(song(1)).first)
        assertEquals(setOf(song(1)), local.files.keys)
        assertTrue(assertIs<SyncEngine.Result.Completed>(result).summary.conflicts.isEmpty())
    }

    @Test
    fun `an upload that landed before it was reported as contested keeps the copy`() = runTest {
        val copy = SyncKey(kind = LibraryFileKind.SONG, name = "song_1 (2).cho")
        val local = FakeLibraryFileLocalSource(files = mapOf(song(1) to HERE))
        val provider = FakeSyncProvider(files = mapOf(song(1) to THERE))
        // The write lands and its answer is lost, so the retry carries a revision that is no longer current.
        var isFirstUpload = true
        provider.onUpload = { key ->
            if (key == song(1) && isFirstUpload) {
                isFirstUpload = false
                provider.files[key] = HERE to "r7"
            }
        }

        val result = synchronize(local, provider, indexOf(song(1) to ORIGINAL))

        assertContentEquals(HERE, local.files[song(1)])
        assertContentEquals(HERE, provider.files.getValue(song(1)).first)
        assertContentEquals(THERE, local.files[copy])
        assertContentEquals(THERE, provider.files.getValue(copy).first)
        assertEquals(listOf(copy.name), assertIs<SyncEngine.Result.Completed>(result).summary.conflicts)
    }

    @Test
    fun `an upload the service refuses takes the copy back`() = runTest {
        val local = FakeLibraryFileLocalSource(files = mapOf(song(1) to HERE))
        val provider = FakeSyncProvider(files = mapOf(song(1) to THERE))
        provider.onUpload = { key -> if (key == song(1)) throw IllegalStateException("Refused") }

        val result = synchronize(local, provider, indexOf(song(1) to ORIGINAL))

        assertIs<SyncEngine.Result.Completed>(result)
        assertEquals(setOf(song(1)), local.files.keys)
        assertContentEquals(THERE, provider.files.getValue(song(1)).first)
    }

    @Test
    fun `an upload cut off by the network keeps the copy`() = runTest {
        val local = FakeLibraryFileLocalSource(files = mapOf(song(1) to HERE))
        val provider = FakeSyncProvider(files = mapOf(song(1) to THERE))
        provider.onUpload = { throw SyncNetworkException("Offline") }

        assertFailsWith<SyncNetworkException> {
            synchronize(local, provider, indexOf(song(1) to ORIGINAL))
        }

        assertContentEquals(THERE, local.files[SyncKey(kind = LibraryFileKind.SONG, name = "song_1 (2).cho")])
    }

    @Test
    fun `a conflict copy is not given a name the cloud folder already holds`() = runTest {
        val another = "Another song".encodeToByteArray()
        val local = FakeLibraryFileLocalSource(files = mapOf(song("x") to HERE))
        val provider = FakeSyncProvider(files = mapOf(song("x") to THERE, song("x (2)") to another))

        val completed = assertIs<SyncEngine.Result.Completed>(synchronize(local, provider, indexOf(song("x") to ORIGINAL)))

        assertEquals(listOf("x (3).cho"), completed.summary.conflicts)
        assertContentEquals(HERE, local.files.getValue(song("x")))
        assertContentEquals(another, local.files.getValue(song("x (2)")))
        assertContentEquals(THERE, local.files.getValue(song("x (3)")))
        assertContentEquals(HERE, provider.files.getValue(song("x")).first)
        assertContentEquals(another, provider.files.getValue(song("x (2)")).first)
        assertEquals("r1", provider.files.getValue(song("x (2)")).second)
        assertContentEquals(THERE, provider.files.getValue(song("x (3)")).first)
    }
}
