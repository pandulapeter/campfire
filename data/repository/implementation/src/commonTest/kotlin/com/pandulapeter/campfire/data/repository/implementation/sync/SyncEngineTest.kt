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
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.source.remote.api.SyncNetworkException
import com.pandulapeter.campfire.data.source.remote.api.hashing.localContentHash
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
        )

        assertContentEquals(edited, local.files[song(2)])
        assertContentEquals(edited, provider.files.getValue(song(2)).first)
        assertEquals(0, result.summary.deletedLocally)
    }

    private companion object {
        const val ACCOUNT_ID = "dropbox:someone@example.com"

        fun song(number: Int) = SyncKey(kind = LibraryFileKind.SONG, name = "song_$number.cho")

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
