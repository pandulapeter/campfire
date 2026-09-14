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
import com.pandulapeter.campfire.data.source.remote.api.SyncNetworkException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
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

    private companion object {
        const val ACCOUNT_ID = "dropbox:someone@example.com"

        fun song(number: Int) = SyncKey(kind = LibraryFileKind.SONG, name = "song_$number.cho")
    }
}
