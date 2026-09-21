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
import com.pandulapeter.campfire.data.model.domain.SyncAccount
import com.pandulapeter.campfire.data.model.domain.SyncDeletionPolicy
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import com.pandulapeter.campfire.data.source.remote.api.SyncNetworkException
import com.pandulapeter.campfire.data.source.remote.api.SyncRemoteStorageFullException
import com.pandulapeter.campfire.data.source.remote.api.hashing.localContentHash
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

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
        val snapshots = mutableListOf<() -> SyncIndexDocument>()

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

        val last = snapshots.last()()
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
        val snapshots = mutableListOf<() -> SyncIndexDocument>()

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
        assertTrue(snapshots.map { it() }.all { it.lastSyncedAt == 42L })
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
    fun `a conflict copy is not written over by a download waiting under its name`() = runTest {
        val copy = SyncKey(kind = LibraryFileKind.SONG, name = "song_1 (2).cho")
        val local = FakeLibraryFileLocalSource(files = mapOf(song(1) to "A's second edit".encodeToByteArray()))
        val provider = FakeSyncProvider(
            files = mapOf(song(1) to "B's edit".encodeToByteArray(), copy to "A's first edit".encodeToByteArray()),
        )

        val result = SyncEngine(local).synchronize(
            provider = provider,
            document = indexOf(song(1) to "Original".encodeToByteArray()),
            accountId = ACCOUNT_ID,
            onProgress = {},
            onIndexChanged = {},
            deletionPolicy = SyncDeletionPolicy.ASK,
        )

        val expected = setOf("A's second edit", "B's edit", "A's first edit")
        assertEquals("A's second edit", local.files.getValue(song(1)).decodeToString())
        assertEquals(expected, local.files.values.map { it.decodeToString() }.toSet())
        assertEquals(expected, provider.files.values.map { it.first.decodeToString() }.toSet())
        assertEquals("B's edit", local.files.getValue(copy).decodeToString())
        assertEquals("B's edit", provider.files.getValue(copy).first.decodeToString())
        assertEquals(2, assertIs<SyncEngine.Result.Completed>(result).summary.conflicts.size)
    }

    @Test
    fun `a song created under a name that is waiting to come down is kept`() = runTest {
        val mine = "Mine".encodeToByteArray()
        val theirs = "Theirs".encodeToByteArray()
        val local = FakeLibraryFileLocalSource()
        val provider = FakeSyncProvider(files = mapOf(song(1) to "One".encodeToByteArray(), song(2) to theirs))
        provider.onDownload = { key -> if (key == song(1)) local.files[song(2)] = mine }

        SyncEngine(local).synchronize(
            provider = provider,
            document = SyncIndexDocument(),
            accountId = ACCOUNT_ID,
            onProgress = {},
            onIndexChanged = {},
            deletionPolicy = SyncDeletionPolicy.ASK,
        )

        assertContentEquals(mine, local.files[song(2)])
        assertContentEquals(theirs, local.files[SyncKey(kind = LibraryFileKind.SONG, name = "song_2 (2).cho")])
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
    fun `a conflict whose copy cannot be written leaves the remote version alone`() = runTest {
        val local = FakeLibraryFileLocalSource(
            files = mapOf(song(1) to HERE),
            onWrite = { key -> if (key != song(1)) throw LibraryStorageException("Full") },
        )
        val provider = FakeSyncProvider(files = mapOf(song(1) to THERE))

        val result = SyncEngine(local).synchronize(
            provider = provider,
            document = indexOf(song(1) to ORIGINAL),
            accountId = ACCOUNT_ID,
            onProgress = {},
            onIndexChanged = {},
            deletionPolicy = SyncDeletionPolicy.ASK,
        )

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

        val result = SyncEngine(local).synchronize(
            provider = provider,
            document = indexOf(song(1) to ORIGINAL),
            accountId = ACCOUNT_ID,
            onProgress = {},
            onIndexChanged = {},
            deletionPolicy = SyncDeletionPolicy.ASK,
        )

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

        val result = SyncEngine(local).synchronize(
            provider = provider,
            document = indexOf(song(1) to ORIGINAL),
            accountId = ACCOUNT_ID,
            onProgress = {},
            onIndexChanged = {},
            deletionPolicy = SyncDeletionPolicy.ASK,
        )

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
            SyncEngine(local).synchronize(
                provider = provider,
                document = indexOf(song(1) to ORIGINAL),
                accountId = ACCOUNT_ID,
                onProgress = {},
                onIndexChanged = {},
                deletionPolicy = SyncDeletionPolicy.ASK,
            )
        }

        assertContentEquals(THERE, local.files[SyncKey(kind = LibraryFileKind.SONG, name = "song_1 (2).cho")])
    }

    @Test
    fun `a file that cannot be written is named in the summary and the others still move`() = runTest {
        val local = FakeLibraryFileLocalSource(onWrite = { key -> if (key == song(2)) throw LibraryStorageException("Full") })
        val provider = FakeSyncProvider(files = librarySongs(3))

        val result = SyncEngine(local).synchronize(
            provider = provider,
            document = SyncIndexDocument(),
            accountId = ACCOUNT_ID,
            onProgress = {},
            onIndexChanged = {},
            deletionPolicy = SyncDeletionPolicy.ASK,
        )

        val completed = assertIs<SyncEngine.Result.Completed>(result)
        assertEquals(listOf("song_2.cho"), completed.summary.failed)
        assertEquals(2, completed.summary.downloaded)
        assertTrue(song(2).path !in completed.index.entries)
    }

    @Test
    fun `a file that fails in both passes is named once`() = runTest {
        val local = FakeLibraryFileLocalSource(
            files = mapOf(song(4) to "Four".encodeToByteArray()),
            onWrite = { key -> if (key == song(2)) throw LibraryStorageException("Full") },
        )
        val provider = FakeSyncProvider(files = librarySongs(3))
        // Another device uploads the same new song while this one is uploading it, which asks for a second pass.
        var isContested = true
        provider.onUpload = { key ->
            if (key == song(4) && isContested) {
                isContested = false
                provider.files[key] = "Four".encodeToByteArray() to "r9"
            }
        }

        val result = SyncEngine(local).synchronize(
            provider = provider,
            document = SyncIndexDocument(),
            accountId = ACCOUNT_ID,
            onProgress = {},
            onIndexChanged = {},
            deletionPolicy = SyncDeletionPolicy.ASK,
        )

        assertTrue(!isContested)
        assertEquals(1, assertIs<SyncEngine.Result.Completed>(result).summary.failed.size)
    }

    @Test
    fun `a full remote folder ends the run`() = runTest {
        val local = FakeLibraryFileLocalSource(files = librarySongs(2))
        val provider = FakeSyncProvider()
        provider.onUpload = { throw SyncRemoteStorageFullException("Full") }

        assertFailsWith<SyncRemoteStorageFullException> {
            SyncEngine(local).synchronize(
                provider = provider,
                document = SyncIndexDocument(),
                accountId = ACCOUNT_ID,
                onProgress = {},
                onIndexChanged = {},
                deletionPolicy = SyncDeletionPolicy.ASK,
            )
        }
    }

    @Test
    fun `a local name another one shadows on a service that ignores case is reported instead of retried`() = runTest {
        val lower = "Lower".encodeToByteArray()
        val local = FakeLibraryFileLocalSource(files = mapOf(song("Song") to "Upper".encodeToByteArray(), song("song") to lower))
        val provider = FakeSyncProvider(files = mapOf(song("song") to lower), ignoresCase = true)

        val result = SyncEngine(local).synchronize(
            provider = provider,
            // In step at the revision the fake holds it at, so the only thing left to do is the other spelling.
            document = SyncIndexDocument(
                accountId = ACCOUNT_ID,
                entries = mapOf(
                    song("song").path to SyncIndexDocument.Entry(localHash = localContentHash(lower), remoteRevision = "r1"),
                ),
            ),
            accountId = ACCOUNT_ID,
            onProgress = {},
            onIndexChanged = {},
            deletionPolicy = SyncDeletionPolicy.ASK,
        )

        assertEquals(listOf("Song.cho"), assertIs<SyncEngine.Result.Completed>(result).summary.failed)
        assertEquals(1, provider.listCount)
        assertEquals(1, provider.files.size)
    }

    @Test
    fun `two local names that differ only by case both go up to a service with exact names`() = runTest {
        val local = FakeLibraryFileLocalSource(
            files = mapOf(song("Song") to "Upper".encodeToByteArray(), song("song") to "Lower".encodeToByteArray()),
        )
        val provider = FakeSyncProvider()

        val result = SyncEngine(local).synchronize(
            provider = provider,
            document = SyncIndexDocument(),
            accountId = ACCOUNT_ID,
            onProgress = {},
            onIndexChanged = {},
            deletionPolicy = SyncDeletionPolicy.ASK,
        )

        assertTrue(assertIs<SyncEngine.Result.Completed>(result).summary.failed.isEmpty())
        assertEquals(setOf(song("Song"), song("song")), provider.files.keys)
    }

    @Test
    fun `a deletion made elsewhere reaches a device whose index was filed under the e-mail address`() = runTest {
        val local = FakeLibraryFileLocalSource(files = mapOf(song(1) to ORIGINAL))
        val provider = FakeSyncProvider()
        val account = SyncAccount(SyncProviderId.DROPBOX, id = "dbid:1", displayName = "Someone", email = "someone@example.com")

        SyncEngine(local).synchronize(
            provider = provider,
            document = indexOf(song(1) to ORIGINAL).adoptedBy(account),
            accountId = account.indexKey(),
            onProgress = {},
            onIndexChanged = {},
            deletionPolicy = SyncDeletionPolicy.DELETE_LOCALLY,
        )

        assertTrue(local.files.isEmpty())
        assertTrue(provider.files.isEmpty())
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

    @Test
    fun `a file that is there but cannot be read stops the run instead of being deleted remotely`() = runTest {
        val library = librarySongs(3)
        val local = FakeLibraryFileLocalSource(
            files = library,
            onRead = { key -> if (key == song(2)) throw LibraryStorageException("Locked") },
        )
        val provider = FakeSyncProvider(files = library)

        assertFailsWith<LibraryStorageException> {
            SyncEngine(local).synchronize(
                provider = provider,
                document = indexOf(*library.toList().toTypedArray()).let { document ->
                    document.copy(entries = document.entries.mapValues { (_, entry) -> entry.copy(remoteRevision = "r1") })
                },
                accountId = ACCOUNT_ID,
                onProgress = {},
                onIndexChanged = {},
                deletionPolicy = SyncDeletionPolicy.ASK,
            )
        }

        assertEquals(library.keys, provider.files.keys)
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

        val result = SyncEngine(local).synchronize(
            provider = provider,
            document = SyncIndexDocument(),
            accountId = ACCOUNT_ID,
            onProgress = {},
            onIndexChanged = {},
            deletionPolicy = SyncDeletionPolicy.ASK,
        )

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

        val result = SyncEngine(FakeLibraryFileLocalSource()).synchronize(
            provider = provider,
            document = indexOf(key to bytes).let { document ->
                document.copy(entries = document.entries.mapValues { (_, entry) -> entry.copy(remoteRevision = "r1") })
            },
            accountId = ACCOUNT_ID,
            onProgress = {},
            onIndexChanged = {},
            deletionPolicy = SyncDeletionPolicy.ASK,
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

        SyncEngine(local).synchronize(
            provider = provider,
            document = indexOf(key to bytes).let { document ->
                document.copy(entries = document.entries.mapValues { (_, entry) -> entry.copy(remoteRevision = "r1") })
            },
            accountId = ACCOUNT_ID,
            onProgress = {},
            onIndexChanged = {},
            deletionPolicy = SyncDeletionPolicy.ASK,
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

        SyncEngine(local).synchronize(
            provider = provider,
            document = indexOf(key to indexed).let { document ->
                document.copy(entries = document.entries.mapValues { (_, entry) -> entry.copy(remoteRevision = "r1") })
            },
            accountId = ACCOUNT_ID,
            onProgress = {},
            onIndexChanged = {},
            deletionPolicy = SyncDeletionPolicy.ASK,
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

        val result = SyncEngine(local).synchronize(
            provider = provider,
            document = SyncIndexDocument(),
            accountId = ACCOUNT_ID,
            onProgress = {},
            onIndexChanged = {},
            deletionPolicy = SyncDeletionPolicy.ASK,
        )

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

        val result = SyncEngine(local).synchronize(
            provider = provider,
            document = document,
            accountId = ACCOUNT_ID,
            onProgress = {},
            onIndexChanged = {},
            deletionPolicy = SyncDeletionPolicy.ASK,
        )

        val completed = assertIs<SyncEngine.Result.Completed>(result)
        assertContentEquals(original, local.files[song(1)])
        assertEquals(0, completed.summary.deletedLocally)
        assertEquals(document.entries, completed.index.entries)
    }

    private companion object {
        const val ACCOUNT_ID = "dropbox:someone@example.com"
        val ORIGINAL = "Original".encodeToByteArray()
        val HERE = "Edited here".encodeToByteArray()
        val THERE = "Edited there".encodeToByteArray()

        fun song(number: Int) = song(name = "song_$number")

        fun song(name: String) = SyncKey(kind = LibraryFileKind.SONG, name = "$name.cho")

        fun foreign(name: String) = SyncKey(kind = LibraryFileKind.SONG, name = name)

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
