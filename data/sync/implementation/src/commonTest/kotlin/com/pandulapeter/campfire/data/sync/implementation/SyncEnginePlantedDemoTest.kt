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
import com.pandulapeter.campfire.data.source.remote.api.hashing.localContentHash
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * A demo file this device planted, met in the cloud folder for the first time: still as planted, it takes the folder's
 * version with no copy, and changed here it keeps both like any other file.
 */
class SyncEnginePlantedDemoTest {

    @Test
    fun `an untouched planted demo song takes the cloud folder's version and keeps no copy`() = runTest {
        val planted = "Demo, as this version plants it".encodeToByteArray()
        val there = "Demo, as an older version planted it".encodeToByteArray()
        val local = FakeLibraryFileLocalSource(files = mapOf(song(1) to planted))
        val provider = FakeSyncProvider(files = mapOf(song(1) to there))

        val result = synchronize(
            local = local,
            provider = provider,
            document = SyncIndexDocument(),
            plantedContentHash = { key -> if (key == song(1)) localContentHash(planted) else null },
        )

        val completed = assertIs<SyncEngine.Result.Completed>(result)
        assertContentEquals(there, local.files[song(1)])
        assertEquals(setOf(song(1)), local.files.keys)
        assertEquals(setOf(song(1)), provider.files.keys)
        assertContentEquals(there, provider.files.getValue(song(1)).first)
        assertEquals(emptyList(), completed.summary.conflicts)
        assertEquals(localContentHash(there), completed.index.entries.getValue(song(1).path).localHash)
    }

    @Test
    fun `a planted demo song edited here is kept next to the cloud folder's version`() = runTest {
        val planted = "Demo, as this version plants it".encodeToByteArray()
        val edited = "Demo, with the user's own verse".encodeToByteArray()
        val there = "Demo, as an older version planted it".encodeToByteArray()
        val local = FakeLibraryFileLocalSource(files = mapOf(song(1) to edited))
        val provider = FakeSyncProvider(files = mapOf(song(1) to there))

        val result = synchronize(
            local = local,
            provider = provider,
            document = SyncIndexDocument(),
            plantedContentHash = { key -> if (key == song(1)) localContentHash(planted) else null },
        )

        assertContentEquals(edited, local.files[song(1)])
        assertContentEquals(there, local.files[SyncKey(kind = LibraryFileKind.SONG, name = "song_1 (2).cho")])
        assertContentEquals(edited, provider.files.getValue(song(1)).first)
        assertEquals(listOf("song_1 (2).cho"), assertIs<SyncEngine.Result.Completed>(result).summary.conflicts)
    }

    @Test
    fun `a file planted under another name does not make this one yield`() = runTest {
        val here = "Song, as written here".encodeToByteArray()
        val there = "Song, as written there".encodeToByteArray()
        val local = FakeLibraryFileLocalSource(files = mapOf(song(1) to here))
        val provider = FakeSyncProvider(files = mapOf(song(1) to there))

        val result = synchronize(
            local = local,
            provider = provider,
            document = SyncIndexDocument(),
            plantedContentHash = { key -> if (key == song(2)) localContentHash(here) else null },
        )

        assertContentEquals(here, local.files[song(1)])
        assertContentEquals(there, local.files[SyncKey(kind = LibraryFileKind.SONG, name = "song_1 (2).cho")])
        assertEquals(listOf("song_1 (2).cho"), assertIs<SyncEngine.Result.Completed>(result).summary.conflicts)
    }

    @Test
    fun `a planted demo song changed back here after a synced edit keeps its copy`() = runTest {
        val planted = "Demo, as this version plants it".encodeToByteArray()
        val edited = "Demo, with a tag this device took off again".encodeToByteArray()
        val there = "Demo, with a verse another device edited".encodeToByteArray()
        val local = FakeLibraryFileLocalSource(files = mapOf(song(1) to planted))
        val provider = FakeSyncProvider(files = mapOf(song(1) to there))

        val result = synchronize(
            local = local,
            provider = provider,
            document = indexOf(song(1) to edited),
            plantedContentHash = { key -> if (key == song(1)) localContentHash(planted) else null },
        )

        assertContentEquals(planted, local.files[song(1)])
        assertContentEquals(there, local.files[SyncKey(kind = LibraryFileKind.SONG, name = "song_1 (2).cho")])
        assertEquals(listOf("song_1 (2).cho"), assertIs<SyncEngine.Result.Completed>(result).summary.conflicts)
    }

    @Test
    fun `a planted demo setlist that changed in more than its day takes the folder's version`() = runTest {
        val here = "Gig;date:2026-10-07;songs:a,b".encodeToByteArray()
        val there = "Gig;date:2026-10-05;songs:a,c".encodeToByteArray()
        val local = FakeLibraryFileLocalSource(files = mapOf(GIG to here))
        val provider = FakeSyncProvider(files = mapOf(GIG to there))

        val result = synchronize(
            local = local,
            provider = provider,
            document = SyncIndexDocument(),
            plantedContentHash = { key -> if (key == GIG) localContentHash(here) else null },
        )

        assertContentEquals(there, local.files[GIG])
        assertEquals(setOf(GIG), local.files.keys)
        assertEquals(emptyList(), assertIs<SyncEngine.Result.Completed>(result).summary.conflicts)
    }
}
