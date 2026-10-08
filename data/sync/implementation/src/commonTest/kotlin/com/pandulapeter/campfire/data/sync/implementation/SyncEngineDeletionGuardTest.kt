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

import com.pandulapeter.campfire.data.model.domain.SyncDeletionDirection
import com.pandulapeter.campfire.data.model.domain.SyncDeletionPolicy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The question a run asks before it deletes most of the library on either side, the answers to it, and the runs that
 * must not ask.
 */
class SyncEngineDeletionGuardTest {

    @Test
    fun `a run that would delete the whole library stops and asks before anything moves`() = runTest {
        val library = librarySongs(10)
        val local = FakeLibraryFileLocalSource(files = library)

        val result = synchronize(local, FakeSyncProvider(), indexOf(*library.toList().toTypedArray()))

        assertEquals(
            SyncEngine.Result.DeletionsNeedConfirmation(count = 10, total = 10, direction = SyncDeletionDirection.LOCAL),
            result,
        )
        assertEquals(library.keys, local.files.keys)
    }

    @Test
    fun `keeping the files a run asked about uploads them again`() = runTest {
        val library = librarySongs(10)
        val local = FakeLibraryFileLocalSource(files = library)
        val provider = FakeSyncProvider()

        val result = synchronize(local, provider, indexOf(*library.toList().toTypedArray()), deletionPolicy = SyncDeletionPolicy.KEEP_AND_UPLOAD)

        assertEquals(10, assertIs<SyncEngine.Result.Completed>(result).summary.uploaded)
        assertEquals(library.keys, local.files.keys)
        assertEquals(library.keys, provider.files.keys)
    }

    @Test
    fun `keeping the files a run asked about forgets their synced preferences`() = runTest {
        val library = librarySongs(10)
        val local = FakeLibraryFileLocalSource(files = library)
        val songs = (library.keys.map { it.name } + "elsewhere.cho").associateWith { JsonObject(mapOf("capo" to JsonPrimitive(2))) }

        val result = synchronize(
            local = local,
            provider = FakeSyncProvider(),
            document = indexOf(*library.toList().toTypedArray()).copy(
                syncedPreferences = JsonObject(mapOf("version" to JsonPrimitive(1), "songs" to JsonObject(songs))),
            ),
            deletionPolicy = SyncDeletionPolicy.KEEP_AND_UPLOAD,
        )

        val synced = assertIs<SyncEngine.Result.Completed>(result).index.syncedPreferences
        assertEquals(setOf("elsewhere.cho"), (synced?.get("songs") as? JsonObject)?.keys)
    }

    @Test
    fun `deleting the files a run asked about deletes them`() = runTest {
        val library = librarySongs(10)
        val local = FakeLibraryFileLocalSource(files = library)

        val result = synchronize(
            local = local,
            provider = FakeSyncProvider(),
            document = indexOf(*library.toList().toTypedArray()),
            deletionPolicy = SyncDeletionPolicy.DELETE_LOCALLY,
        )

        assertEquals(10, assertIs<SyncEngine.Result.Completed>(result).summary.deletedLocally)
        assertTrue(local.files.isEmpty())
    }

    @Test
    fun `a run that would empty the cloud folder stops and asks before anything moves`() = runTest {
        val library = librarySongs(10)
        val provider = FakeSyncProvider(files = library)

        val result = synchronize(FakeLibraryFileLocalSource(files = emptyMap()), provider, syncedIndexOf(library))

        assertEquals(
            SyncEngine.Result.DeletionsNeedConfirmation(count = 10, total = 10, direction = SyncDeletionDirection.REMOTE),
            result,
        )
        assertEquals(library.keys, provider.files.keys)
    }

    @Test
    fun `a small library that vanished from this device stops and asks`() = runTest {
        val library = librarySongs(5)
        // Two of the five are gone from the folder as well, so only three would be deleted from it: fewer than the
        // proportional rule asks about, and not the whole index either.
        val provider = FakeSyncProvider(files = library.filterKeys { it != song(1) && it != song(2) })

        val result = synchronize(FakeLibraryFileLocalSource(files = emptyMap()), provider, syncedIndexOf(library))

        assertEquals(
            SyncEngine.Result.DeletionsNeedConfirmation(count = 3, total = 5, direction = SyncDeletionDirection.REMOTE),
            result,
        )
        assertEquals(3, provider.files.size)
    }

    @Test
    fun `deleting the files a run asked about removes them from the cloud folder`() = runTest {
        val library = librarySongs(10)
        val provider = FakeSyncProvider(files = library)

        val result = synchronize(
            local = FakeLibraryFileLocalSource(files = emptyMap()),
            provider = provider,
            document = syncedIndexOf(library),
            deletionPolicy = SyncDeletionPolicy.DELETE_REMOTELY,
        )

        assertEquals(10, assertIs<SyncEngine.Result.Completed>(result).summary.deletedRemotely)
        assertTrue(provider.files.isEmpty())
        // One request, so that another device listing the folder meanwhile does not see the deletion half done.
        assertEquals(listOf(10), provider.deleteCalls.map { it.size })
    }

    @Test
    fun `a remote deletion the service refuses fails that file and keeps it in the index`() = runTest {
        val library = librarySongs(10)
        val provider = FakeSyncProvider(files = library).apply { refusedDeletions = setOf(library.keys.first().name) }

        val result = synchronize(
            local = FakeLibraryFileLocalSource(files = emptyMap()),
            provider = provider,
            document = syncedIndexOf(library),
            deletionPolicy = SyncDeletionPolicy.DELETE_REMOTELY,
        )

        val completed = assertIs<SyncEngine.Result.Completed>(result)
        assertEquals(9, completed.summary.deletedRemotely)
        assertEquals(listOf(library.keys.first().name), completed.summary.failed)
        assertEquals(setOf(library.keys.first()), provider.files.keys)
        assertEquals(setOf(library.keys.first().path), completed.index.entries.keys)
    }

    @Test
    fun `keeping the files a run asked about downloads them again`() = runTest {
        val library = librarySongs(10)
        val local = FakeLibraryFileLocalSource(files = emptyMap())
        val provider = FakeSyncProvider(files = library)

        val result = synchronize(local, provider, syncedIndexOf(library), deletionPolicy = SyncDeletionPolicy.KEEP_AND_DOWNLOAD)

        assertEquals(10, assertIs<SyncEngine.Result.Completed>(result).summary.downloaded)
        assertEquals(library.keys, local.files.keys)
        assertEquals(library.keys, provider.files.keys)
    }

    @Test
    fun `answering about this device does not let a run empty the cloud folder`() = runTest {
        val library = librarySongs(10)
        val provider = FakeSyncProvider(files = library)

        val result = synchronize(
            local = FakeLibraryFileLocalSource(files = emptyMap()),
            provider = provider,
            document = syncedIndexOf(library),
            deletionPolicy = SyncDeletionPolicy.DELETE_LOCALLY,
        )

        assertEquals(SyncDeletionDirection.REMOTE, assertIs<SyncEngine.Result.DeletionsNeedConfirmation>(result).direction)
        assertEquals(library.keys, provider.files.keys)
    }

    @Test
    fun `answering about the cloud folder does not let a run empty this device`() = runTest {
        val library = librarySongs(10)
        val local = FakeLibraryFileLocalSource(files = library)

        val result = synchronize(
            local = local,
            provider = FakeSyncProvider(),
            document = indexOf(*library.toList().toTypedArray()),
            deletionPolicy = SyncDeletionPolicy.DELETE_REMOTELY,
        )

        assertEquals(SyncDeletionDirection.LOCAL, assertIs<SyncEngine.Result.DeletionsNeedConfirmation>(result).direction)
        assertEquals(library.keys, local.files.keys)
    }

    @Test
    fun `a run with no index never asks`() = runTest {
        val result = synchronize(FakeLibraryFileLocalSource(files = emptyMap()), FakeSyncProvider(), SyncIndexDocument())

        assertIs<SyncEngine.Result.Completed>(result)
    }

    @Test
    fun `a run that deletes a few files out of many does not ask`() = runTest {
        val library = librarySongs(10)
        val local = FakeLibraryFileLocalSource(files = library)
        val provider = FakeSyncProvider(files = library.filterKeys { it != song(1) && it != song(2) })

        val result = synchronize(
            local = local,
            provider = provider,
            document = indexOf(*library.toList().toTypedArray()).let { document ->
                // In step with the fake's starting revision, so that only the two missing files make a plan.
                document.copy(entries = document.entries.mapValues { (_, entry) -> entry.copy(remoteRevision = "r1") })
            },
        )

        assertEquals(2, assertIs<SyncEngine.Result.Completed>(result).summary.deletedLocally)
        assertEquals(8, local.files.size)
    }

    /** A phone restored from a backup, which carries the library but not the index, signing in again. */
    @Test
    fun `with no index a file that is the same on both sides moves nothing`() = runTest {
        val library = librarySongs(1)
        val local = FakeLibraryFileLocalSource(files = library)
        var uploads = 0
        val provider = FakeSyncProvider(files = library, onUpload = { uploads++ })

        val completed = assertIs<SyncEngine.Result.Completed>(synchronize(local, provider, SyncIndexDocument()))

        assertFalse(completed.summary.hasChanges)
        assertEquals(0, uploads)
        assertNull(provider.downloadCounts[song(1)])
        assertEquals(setOf(song(1).path), completed.index.entries.keys)
    }
}
