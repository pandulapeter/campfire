/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.implementation.sync

import com.pandulapeter.campfire.data.model.domain.LibraryFileKind
import com.pandulapeter.campfire.data.model.domain.SyncDeletionPolicy
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.source.remote.api.SyncNetworkException
import com.pandulapeter.campfire.data.source.remote.api.hashing.localContentHash
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The engine against an in-memory library and remote folder: what a run leaves behind when it does not get to the
 * end, which the planner's tests cannot show since the planner never sees a run at all.
 */
class SyncEngineTest {

    @Test
    fun `a run that loses the network keeps the files it already transferred in the index`() = runTest {
        val remoteFiles = (1..5).associate { song(it) to "Song $it".encodeToByteArray() }
        var downloads = 0
        val provider = FakeSyncProvider(
            files = remoteFiles,
            onDownload = { if (++downloads == 3) throw SyncNetworkException("Offline") },
        )
        val snapshots = mutableListOf<SyncIndexDocument>()

        assertFailsWith<SyncNetworkException> {
            SyncEngine(FakeLibraryFileLocalSource()).synchronize(
                provider = provider,
                document = SyncIndexDocument(),
                accountId = ACCOUNT_ID,
                onProgress = {},
                onIndexChanged = { snapshots += it },
                deletionPolicy = SyncDeletionPolicy.ASK,
            )
        }

        val last = snapshots.last()
        assertTrue(last.isRunInProgress)
        assertEquals(ACCOUNT_ID, last.accountId)
        assertEquals(setOf(song(1).path, song(2).path), last.entries.keys)
    }

    @Test
    fun `an interrupted run keeps the time of the last completed one`() = runTest {
        var downloads = 0
        val provider = FakeSyncProvider(
            files = mapOf(song(1) to "One".encodeToByteArray(), song(2) to "Two".encodeToByteArray()),
            onDownload = { if (++downloads == 2) throw SyncNetworkException("Offline") },
        )
        val snapshots = mutableListOf<SyncIndexDocument>()

        assertFailsWith<SyncNetworkException> {
            SyncEngine(FakeLibraryFileLocalSource()).synchronize(
                provider = provider,
                document = SyncIndexDocument(accountId = ACCOUNT_ID, lastSyncedAt = 42),
                accountId = ACCOUNT_ID,
                onProgress = {},
                onIndexChanged = { snapshots += it },
                deletionPolicy = SyncDeletionPolicy.ASK,
            )
        }

        assertTrue(snapshots.isNotEmpty())
        assertTrue(snapshots.all { it.lastSyncedAt == 42L })
    }

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

        SyncEngine(local).synchronize(
            provider = provider,
            document = indexOf(song(1) to original, song(2) to original),
            accountId = ACCOUNT_ID,
            onProgress = {},
            onIndexChanged = {},
            deletionPolicy = SyncDeletionPolicy.ASK,
        )

        assertContentEquals(edited, local.files[song(2)])
        assertContentEquals(incoming, local.files[SyncKey(kind = LibraryFileKind.SONG, name = "song_2 (2).cho")])
        assertContentEquals(edited, provider.files.getValue(song(2)).first)
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

        val result = SyncEngine(local).synchronize(
            provider = provider,
            document = indexOf(song(1) to original, song(2) to original),
            accountId = ACCOUNT_ID,
            onProgress = {},
            onIndexChanged = {},
            deletionPolicy = SyncDeletionPolicy.ASK,
        )

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

        SyncEngine(local).synchronize(
            provider = provider,
            document = indexOf(song(1) to original),
            accountId = ACCOUNT_ID,
            onProgress = {},
            onIndexChanged = {},
            deletionPolicy = SyncDeletionPolicy.ASK,
        )

        assertEquals(setOf("song_1.cho", "song_1 (2).cho"), local.files.keys.map { it.name }.toSet())
        assertContentEquals(here, local.files[song(1)])
    }

    @Test
    fun `a library larger than one reading batch is read whole`() = runTest {
        val local = FakeLibraryFileLocalSource(files = (1..200).associate { song(it) to "Song $it".encodeToByteArray() })
        val provider = FakeSyncProvider()

        val result = SyncEngine(local).synchronize(
            provider = provider,
            document = SyncIndexDocument(),
            accountId = ACCOUNT_ID,
            onProgress = {},
            onIndexChanged = {},
            deletionPolicy = SyncDeletionPolicy.ASK,
        )

        assertEquals(200, assertIs<SyncEngine.Result.Completed>(result).summary.uploaded)
        assertEquals(local.files.keys, provider.files.keys)
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

        val result = SyncEngine(local).synchronize(
            provider = provider,
            document = SyncIndexDocument(),
            accountId = ACCOUNT_ID,
            onProgress = {},
            onIndexChanged = {},
            deletionPolicy = SyncDeletionPolicy.ASK,
        )

        assertEquals(setOf(song("Song").path), assertIs<SyncEngine.Result.Completed>(result).index.entries.keys)
        assertEquals(setOf(song("Song")), local.files.keys)
    }

    @Test
    fun `a run that would delete the whole library stops and asks before anything moves`() = runTest {
        val library = librarySongs(10)
        val local = FakeLibraryFileLocalSource(files = library)

        val result = SyncEngine(local).synchronize(
            provider = FakeSyncProvider(),
            document = indexOf(*library.toList().toTypedArray()),
            accountId = ACCOUNT_ID,
            onProgress = {},
            onIndexChanged = {},
            deletionPolicy = SyncDeletionPolicy.ASK,
        )

        assertEquals(SyncEngine.Result.DeletionsNeedConfirmation(count = 10, total = 10), result)
        assertEquals(library.keys, local.files.keys)
    }

    @Test
    fun `keeping the files a run asked about uploads them again`() = runTest {
        val library = librarySongs(10)
        val local = FakeLibraryFileLocalSource(files = library)
        val provider = FakeSyncProvider()

        val result = SyncEngine(local).synchronize(
            provider = provider,
            document = indexOf(*library.toList().toTypedArray()),
            accountId = ACCOUNT_ID,
            onProgress = {},
            onIndexChanged = {},
            deletionPolicy = SyncDeletionPolicy.KEEP_AND_UPLOAD,
        )

        assertEquals(10, assertIs<SyncEngine.Result.Completed>(result).summary.uploaded)
        assertEquals(library.keys, local.files.keys)
        assertEquals(library.keys, provider.files.keys)
    }

    @Test
    fun `deleting the files a run asked about deletes them`() = runTest {
        val library = librarySongs(10)
        val local = FakeLibraryFileLocalSource(files = library)

        val result = SyncEngine(local).synchronize(
            provider = FakeSyncProvider(),
            document = indexOf(*library.toList().toTypedArray()),
            accountId = ACCOUNT_ID,
            onProgress = {},
            onIndexChanged = {},
            deletionPolicy = SyncDeletionPolicy.DELETE_LOCALLY,
        )

        assertEquals(10, assertIs<SyncEngine.Result.Completed>(result).summary.deletedLocally)
        assertTrue(local.files.isEmpty())
    }

    @Test
    fun `a run that deletes a few files out of many does not ask`() = runTest {
        val library = librarySongs(10)
        val local = FakeLibraryFileLocalSource(files = library)
        val provider = FakeSyncProvider(files = library.filterKeys { it != song(1) && it != song(2) })

        val result = SyncEngine(local).synchronize(
            provider = provider,
            document = indexOf(*library.toList().toTypedArray()).let { document ->
                // In step with the fake's starting revision, so that only the two missing files make a plan.
                document.copy(entries = document.entries.mapValues { (_, entry) -> entry.copy(remoteRevision = "r1") })
            },
            accountId = ACCOUNT_ID,
            onProgress = {},
            onIndexChanged = {},
            deletionPolicy = SyncDeletionPolicy.ASK,
        )

        assertEquals(2, assertIs<SyncEngine.Result.Completed>(result).summary.deletedLocally)
        assertEquals(8, local.files.size)
    }

    private companion object {
        const val ACCOUNT_ID = "dropbox:someone@example.com"

        fun song(number: Int) = song(name = "song_$number")

        fun song(name: String) = SyncKey(kind = LibraryFileKind.SONG, name = "$name.cho")

        fun librarySongs(count: Int) = (1..count).associate { song(it) to "Song $it".encodeToByteArray() }

        /** An index that says the last run saw [files] with these contents, at the revision the fake starts from. */
        fun indexOf(vararg files: Pair<SyncKey, ByteArray>) = SyncIndexDocument.of(
            providerId = SyncProviderId.DROPBOX.id,
            accountId = ACCOUNT_ID,
            lastSyncedAt = 1,
            index = files.associate { (key, bytes) ->
                key to SyncIndexEntry(localHash = localContentHash(bytes), remoteRevision = "r0")
            },
        )
    }
}
