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
import com.pandulapeter.campfire.data.model.domain.SyncDeletionPolicy
import com.pandulapeter.campfire.data.source.remote.api.hashing.localContentHash
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Names that are one file to a service or a file system but two strings: by case on a service that ignores it, by
 * Unicode form everywhere, and names this device cannot store at all.
 */
class SyncEngineNameFoldingTest {

    @Test
    fun `a local name another one shadows on a service that ignores case is reported instead of retried`() = runTest {
        val lower = "Lower".encodeToByteArray()
        val local = FakeLibraryFileLocalSource(files = mapOf(song("Song") to "Upper".encodeToByteArray(), song("song") to lower))
        val provider = FakeSyncProvider(files = mapOf(song("song") to lower), ignoresCase = true)

        val result = synchronize(
            local = local,
            provider = provider,
            // In step at the revision the fake holds it at, so the only thing left to do is the other spelling.
            document = SyncIndexDocument(
                accountId = ACCOUNT_ID,
                entries = mapOf(
                    song("song").path to SyncIndexDocument.Entry(localHash = localContentHash(lower), remoteRevision = "r1"),
                ),
            ),
        )

        assertEquals(listOf("Song.cho"), assertIs<SyncEngine.Result.Completed>(result).summary.failed)
        assertEquals(1, provider.listCount)
        assertEquals(1, provider.files.size)
    }

    @Test
    fun `a song renamed by case keeps its index entry under the new spelling`() = runTest {
        val local = FakeLibraryFileLocalSource(files = mapOf(song("hallelujah") to ORIGINAL))
        val provider = FakeSyncProvider(files = mapOf(song("Hallelujah") to ORIGINAL), ignoresCase = true)

        val result = synchronize(local, provider, renamedIndexOf(song("Hallelujah") to ORIGINAL, revision = "r1"))

        val completed = assertIs<SyncEngine.Result.Completed>(result)
        assertEquals(setOf(song("hallelujah").path), completed.index.entries.keys)
        assertFalse(completed.summary.hasChanges)
        assertTrue(song("Hallelujah") in provider.files)
    }

    @Test
    fun `deleting a song renamed by case deletes it remotely`() = runTest {
        val local = FakeLibraryFileLocalSource()
        val provider = FakeSyncProvider(files = mapOf(song("Hallelujah") to ORIGINAL), ignoresCase = true)

        synchronize(
            local = local,
            provider = provider,
            document = renamedIndexOf(song("hallelujah") to ORIGINAL, revision = "r1"),
            // The one song is the whole library, which an ordinary run asks about before emptying the folder.
            deletionPolicy = SyncDeletionPolicy.DELETE_REMOTELY,
        )

        assertTrue(provider.files.isEmpty())
        assertTrue(local.files.isEmpty())
    }

    @Test
    fun `an edit made elsewhere to a song renamed by case is downloaded rather than taken for a conflict`() = runTest {
        val local = FakeLibraryFileLocalSource(files = mapOf(song("hallelujah") to ORIGINAL))
        val provider = FakeSyncProvider(files = mapOf(song("Hallelujah") to THERE), ignoresCase = true)

        val result = synchronize(local, provider, renamedIndexOf(song("Hallelujah") to ORIGINAL, revision = "r0"))

        assertEquals(setOf(song("hallelujah")), local.files.keys)
        assertContentEquals(THERE, local.files.getValue(song("hallelujah")))
        assertTrue(assertIs<SyncEngine.Result.Completed>(result).summary.conflicts.isEmpty())
    }

    @Test
    fun `an index entry two listed names fold to is left where it is`() {
        val index = mapOf(song("SONG") to SyncIndexEntry(localHash = "a", remoteRevision = "r1"))

        assertEquals(index, foldIndexNamesOntoListings(index = index, listed = setOf(song("Song"), song("song"))))
    }

    @Test
    fun `two index entries that fold to one listed name are left where they are`() {
        val index = mapOf(
            song("SONG") to SyncIndexEntry(localHash = "a", remoteRevision = "r1"),
            song("Song") to SyncIndexEntry(localHash = "b", remoteRevision = "r2"),
        )

        assertEquals(index, foldIndexNamesOntoListings(index = index, listed = setOf(song("song"))))
    }

    @Test
    fun `two local names that differ only by case both go up to a service with exact names`() = runTest {
        val local = FakeLibraryFileLocalSource(
            files = mapOf(song("Song") to "Upper".encodeToByteArray(), song("song") to "Lower".encodeToByteArray()),
        )
        val provider = FakeSyncProvider()

        val result = synchronize(local, provider, SyncIndexDocument())

        assertTrue(assertIs<SyncEngine.Result.Completed>(result).summary.failed.isEmpty())
        assertEquals(setOf(song("Song"), song("song")), provider.files.keys)
    }

    @Test
    fun `a remote name that differs from a local one only by case takes the local spelling`() = assertEquals(
        expected = listOf(RemoteFileState(song("Song"), revision = "r1", contentHash = null)),
        actual = foldRemoteNamesOntoLocal(
            local = listOf(LocalFileState(song("Song"), hash = "a")),
            remote = listOf(RemoteFileState(song("song"), revision = "r1", contentHash = null)),
        ),
    )

    @Test
    fun `a remote name with an exact local match is left as it is`() = assertEquals(
        expected = listOf(
            RemoteFileState(song("Song"), revision = "r1", contentHash = null),
            RemoteFileState(song("song"), revision = "r2", contentHash = null),
        ),
        actual = foldRemoteNamesOntoLocal(
            local = listOf(LocalFileState(song("Song"), hash = "a"), LocalFileState(song("song"), hash = "b")),
            remote = listOf(
                RemoteFileState(song("Song"), revision = "r1", contentHash = null),
                RemoteFileState(song("song"), revision = "r2", contentHash = null),
            ),
        ),
    )

    @Test
    fun `names are only folded within the same kind`() = assertEquals(
        expected = listOf(RemoteFileState(SyncKey(LibraryFileKind.SETLIST, "song.cho"), revision = "r1", contentHash = null)),
        actual = foldRemoteNamesOntoLocal(
            local = listOf(LocalFileState(song("Song"), hash = "a")),
            remote = listOf(RemoteFileState(SyncKey(LibraryFileKind.SETLIST, "song.cho"), revision = "r1", contentHash = null)),
        ),
    )

    @Test
    fun `a file whose remote name differs only by case is not uploaded as a new one`() = runTest {
        val bytes = "Song".encodeToByteArray()
        val local = FakeLibraryFileLocalSource(files = mapOf(song("Song") to bytes))
        val provider = FakeSyncProvider(files = mapOf(song("song") to bytes))

        val result = synchronize(local, provider, SyncIndexDocument())

        assertEquals(setOf(song("Song").path), assertIs<SyncEngine.Result.Completed>(result).index.entries.keys)
        assertEquals(setOf(song("Song")), local.files.keys)
    }

    @Test
    fun `a remote file this device cannot store is left alone and named once`() = runTest {
        val local = FakeLibraryFileLocalSource(canHoldFileName = { '?' !in it })
        val provider = FakeSyncProvider(files = mapOf(song("ok") to ORIGINAL, song("who?") to ORIGINAL))

        val result = synchronize(local, provider, SyncIndexDocument())

        val completed = assertIs<SyncEngine.Result.Completed>(result)
        assertEquals(setOf(song("ok")), local.files.keys)
        assertEquals(setOf(song("ok"), song("who?")), provider.files.keys)
        assertEquals(listOf(song("who?").name), completed.summary.failed)
        assertEquals(setOf(song("ok").path), completed.index.entries.keys)
    }

    @Test
    fun `a remote file this device cannot store is not taken for a deletion`() = runTest {
        val local = FakeLibraryFileLocalSource(files = mapOf(song("ok") to ORIGINAL), canHoldFileName = { '?' !in it })
        val provider = FakeSyncProvider(files = mapOf(song("ok") to ORIGINAL, song("who?") to ORIGINAL))

        val result = synchronize(local, provider, syncedIndexOf(mapOf(song("ok") to ORIGINAL, song("who?") to ORIGINAL)))

        val completed = assertIs<SyncEngine.Result.Completed>(result)
        assertTrue(song("who?") in provider.files)
        assertEquals(setOf(song("ok").path), completed.index.entries.keys)
    }

    @Test
    fun `a name this device cannot store is not folded onto a local one`() = runTest {
        val local = FakeLibraryFileLocalSource(files = mapOf(song("who") to ORIGINAL), canHoldFileName = { '?' !in it })
        val provider = FakeSyncProvider(files = mapOf(song("who?") to THERE))

        val result = synchronize(local, provider, SyncIndexDocument())

        assertEquals(1, assertIs<SyncEngine.Result.Completed>(result).summary.uploaded)
        assertContentEquals(THERE, provider.files.getValue(song("who?")).first)
        assertContentEquals(ORIGINAL, provider.files.getValue(song("who")).first)
        assertEquals(setOf(song("who")), local.files.keys)
    }

    @Test
    fun `a device that can store every name reports no failures`() = runTest {
        val local = FakeLibraryFileLocalSource()
        val provider = FakeSyncProvider(files = mapOf(song("ok") to ORIGINAL, song("who?") to ORIGINAL))

        val result = synchronize(local, provider, SyncIndexDocument())

        assertTrue(assertIs<SyncEngine.Result.Completed>(result).summary.failed.isEmpty())
        assertEquals(setOf(song("ok"), song("who?")), local.files.keys)
    }

    @Test
    fun `a file whose remote name differs only by Unicode form is not uploaded as a new one`() = runTest {
        val local = FakeLibraryFileLocalSource(files = mapOf(song(COMPOSED) to ORIGINAL))
        val provider = FakeSyncProvider(files = mapOf(song(DECOMPOSED) to ORIGINAL))

        val result = synchronize(local, provider, SyncIndexDocument())

        assertEquals(setOf(song(COMPOSED).path), assertIs<SyncEngine.Result.Completed>(result).index.entries.keys)
        assertEquals(setOf(song(COMPOSED)), local.files.keys)
        assertEquals(1, provider.files.size)
    }

    @Test
    fun `an index entry follows a name across a change of Unicode form`() = runTest {
        val local = FakeLibraryFileLocalSource(files = mapOf(song(COMPOSED) to ORIGINAL))
        val provider = FakeSyncProvider(files = mapOf(song(COMPOSED) to ORIGINAL))

        val result = synchronize(local, provider, renamedIndexOf(song(DECOMPOSED) to ORIGINAL, revision = "r1"))

        val completed = assertIs<SyncEngine.Result.Completed>(result)
        assertFalse(completed.summary.hasChanges)
        assertEquals(setOf(song(COMPOSED).path), completed.index.entries.keys)
        assertEquals(setOf(song(COMPOSED)), local.files.keys)
        assertEquals(setOf(song(COMPOSED)), provider.files.keys)
    }

    private companion object {

        /** One name in the two Unicode forms the platforms hand out, composed as Android writes it and decomposed as macOS does. */
        const val COMPOSED = "\u043C\u0430\u0439"
        const val DECOMPOSED = "\u043C\u0430\u0438\u0306"
    }
}
