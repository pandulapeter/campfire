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
import com.pandulapeter.campfire.data.model.domain.SyncAccount
import com.pandulapeter.campfire.data.model.domain.SyncDeletionPolicy
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import com.pandulapeter.campfire.data.source.remote.api.SyncNetworkException
import com.pandulapeter.campfire.data.source.remote.api.SyncRemoteStorageFullException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The engine against an in-memory library and remote folder: what a run leaves behind when it does not get to the
 * end, what it reports and records as it goes, and whose index it acts on, which the planner's tests cannot show since
 * the planner never sees a run at all. The rest of the engine's behaviour is in the other `SyncEngine…Test` files.
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
            synchronize(FakeLibraryFileLocalSource(), provider, SyncIndexDocument(), onIndexChanged = { snapshots += it })
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
            synchronize(
                local = FakeLibraryFileLocalSource(),
                provider = provider,
                document = SyncIndexDocument(accountId = ACCOUNT_ID, lastSyncedAt = 42),
                onIndexChanged = { snapshots += it },
            )
        }

        assertTrue(snapshots.isNotEmpty())
        assertTrue(snapshots.map { it() }.all { it.lastSyncedAt == 42L })
    }

    @Test
    fun `a file that cannot be written is named in the summary and the others still move`() = runTest {
        val local = FakeLibraryFileLocalSource(onWrite = { key -> if (key == song(2)) throw LibraryStorageException("Full") })
        val provider = FakeSyncProvider(files = librarySongs(3))

        val result = synchronize(local, provider, SyncIndexDocument())

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

        val result = synchronize(local, provider, SyncIndexDocument())

        assertTrue(!isContested)
        assertEquals(1, assertIs<SyncEngine.Result.Completed>(result).summary.failed.size)
    }

    @Test
    fun `a file still contested after the last pass is named in the summary`() = runTest {
        val local = FakeLibraryFileLocalSource(files = mapOf(song(1) to HERE))
        val provider = FakeSyncProvider(files = mapOf(song(1) to ORIGINAL))
        // A second device that writes the file again every time this one is about to, in every pass the run makes.
        var contestedUploads = 0
        provider.onUpload = { key ->
            if (key == song(1)) provider.files[key] = provider.files.getValue(key).first to "r${100 + ++contestedUploads}"
        }

        val result = synchronize(local, provider, syncedIndexOf(mapOf(song(1) to ORIGINAL)))

        assertEquals(2, contestedUploads)
        assertEquals(listOf(song(1).name), assertIs<SyncEngine.Result.Completed>(result).summary.failed)
        assertContentEquals(HERE, local.files[song(1)])
    }

    @Test
    fun `a full remote folder ends the run`() = runTest {
        val local = FakeLibraryFileLocalSource(files = librarySongs(2))
        val provider = FakeSyncProvider()
        provider.onUpload = { throw SyncRemoteStorageFullException("Full") }

        assertFailsWith<SyncRemoteStorageFullException> {
            synchronize(local, provider, SyncIndexDocument())
        }
    }

    @Test
    fun `a deletion made elsewhere reaches a device whose index was filed under the e-mail address`() = runTest {
        val local = FakeLibraryFileLocalSource(files = mapOf(song(1) to ORIGINAL))
        val provider = FakeSyncProvider()
        val account = SyncAccount(SyncProviderId.DROPBOX, id = "dbid:1", displayName = "Someone", email = "someone@example.com")

        synchronize(
            local = local,
            provider = provider,
            document = indexOf(song(1) to ORIGINAL).adoptedBy(account),
            accountId = account.indexKey(),
            deletionPolicy = SyncDeletionPolicy.DELETE_LOCALLY,
        )

        assertTrue(local.files.isEmpty())
        assertTrue(provider.files.isEmpty())
    }

    @Test
    fun `an index written for another account is disregarded, files and synced preferences alike`() = runTest {
        val library = librarySongs(3)
        val local = FakeLibraryFileLocalSource(files = library)
        val provider = FakeSyncProvider()
        val document = syncedIndexOf(library).copy(
            accountId = "dropbox:someone-else",
            syncedPreferences = JsonObject(mapOf("version" to JsonPrimitive(1))),
        )

        // Deleting here allowed, so that the guard is not what keeps the other account's index from emptying the library.
        val result = synchronize(local, provider, document, deletionPolicy = SyncDeletionPolicy.DELETE_LOCALLY)

        val completed = assertIs<SyncEngine.Result.Completed>(result)
        assertEquals(3, completed.summary.uploaded)
        assertEquals(0, completed.summary.deletedLocally)
        assertEquals(library.keys, local.files.keys)
        assertEquals(library.keys, provider.files.keys)
        assertEquals(ACCOUNT_ID, completed.index.accountId)
        assertNull(completed.index.syncedPreferences)
    }

    @Test
    fun `a library larger than one reading batch is read whole`() = runTest {
        val local = FakeLibraryFileLocalSource(files = (1..200).associate { song(it) to "Song $it".encodeToByteArray() })
        val provider = FakeSyncProvider()

        val result = synchronize(local, provider, SyncIndexDocument())

        assertEquals(200, assertIs<SyncEngine.Result.Completed>(result).summary.uploaded)
        assertEquals(local.files.keys, provider.files.keys)
    }

    @Test
    fun `a download reports the file it wrote`() = runTest {
        val changed = mutableSetOf<SyncKey>()

        synchronize(
            local = FakeLibraryFileLocalSource(),
            provider = FakeSyncProvider(files = mapOf(song(1) to THERE)),
            document = SyncIndexDocument(),
            onLocalFileChanged = { changed += it },
        )

        assertEquals(setOf(song(1)), changed)
    }

    @Test
    fun `a local deletion reports the file it deleted`() = runTest {
        val changed = mutableSetOf<SyncKey>()

        synchronize(
            local = FakeLibraryFileLocalSource(files = mapOf(song(1) to ORIGINAL)),
            provider = FakeSyncProvider(),
            document = indexOf(song(1) to ORIGINAL),
            onLocalFileChanged = { changed += it },
            deletionPolicy = SyncDeletionPolicy.DELETE_LOCALLY,
        )

        assertEquals(setOf(song(1)), changed)
    }

    @Test
    fun `a conflict reports the copy it wrote`() = runTest {
        val changed = mutableSetOf<SyncKey>()

        synchronize(
            local = FakeLibraryFileLocalSource(files = mapOf(song(1) to HERE)),
            provider = FakeSyncProvider(files = mapOf(song(1) to THERE)),
            document = indexOf(song(1) to ORIGINAL),
            onLocalFileChanged = { changed += it },
        )

        assertEquals(setOf(SyncKey(kind = LibraryFileKind.SONG, name = "song_1 (2).cho")), changed)
    }

    @Test
    fun `a copy taken back is reported`() = runTest {
        val local = FakeLibraryFileLocalSource(files = mapOf(song(1) to HERE))
        val provider = FakeSyncProvider(files = mapOf(song(1) to THERE))
        provider.onUpload = { key -> if (key == song(1)) throw IllegalStateException("Refused") }
        val changed = mutableListOf<SyncKey>()

        synchronize(local, provider, indexOf(song(1) to ORIGINAL), onLocalFileChanged = { changed += it })

        val copy = SyncKey(kind = LibraryFileKind.SONG, name = "song_1 (2).cho")
        assertEquals(listOf(copy, copy), changed)
        assertEquals(setOf(song(1)), local.files.keys)
    }

    @Test
    fun `a download records the revision it fetched`() = runTest {
        val local = FakeLibraryFileLocalSource()
        val provider = FakeSyncProvider(files = mapOf(song(1) to ORIGINAL))
        // Another device saves the song again between the listing and the request.
        provider.onDownload = {
            provider.files[song(1)] = THERE to "r9"
            provider.onDownload = {}
        }

        val result = synchronize(local, provider, SyncIndexDocument())

        assertContentEquals(THERE, local.files[song(1)])
        assertEquals("r9", assertIs<SyncEngine.Result.Completed>(result).index.entries.getValue(song(1).path).remoteRevision)
    }

    @Test
    fun `an edit after a download that raced a remote save is not a conflict`() = runTest {
        val local = FakeLibraryFileLocalSource()
        val provider = FakeSyncProvider(files = mapOf(song(1) to ORIGINAL))
        provider.onDownload = {
            provider.files[song(1)] = THERE to "r9"
            provider.onDownload = {}
        }
        val first = assertIs<SyncEngine.Result.Completed>(synchronize(local, provider, SyncIndexDocument()))
        local.files[song(1)] = HERE

        val second = assertIs<SyncEngine.Result.Completed>(synchronize(local, provider, first.index))

        assertTrue(second.summary.conflicts.isEmpty())
        assertContentEquals(HERE, provider.files.getValue(song(1)).first)
        assertEquals(setOf(song(1)), local.files.keys)
    }
}
