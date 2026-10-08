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
 * A setlist whose two versions differ only in the day they name, which every device gives an undated setlist on its
 * own: the cloud folder's version is taken with no copy, and anything more than the day still keeps both.
 */
class SyncEngineSetlistDateTest {

    @Test
    fun `a setlist dated differently on two devices takes the folder's day and keeps no copy`() = runTest {
        val here = "Gig;date:2026-10-07;songs:a".encodeToByteArray()
        val there = "Gig;date:2026-10-05;songs:a".encodeToByteArray()
        val local = FakeLibraryFileLocalSource(files = mapOf(GIG to here))
        val provider = FakeSyncProvider(files = mapOf(GIG to there))

        val result = synchronize(
            local = local,
            provider = provider,
            document = indexOf(GIG to "Gig;date:2026-01-01;songs:a".encodeToByteArray()),
            setlistComparison = DayBlindSetlistComparison,
        )

        val completed = assertIs<SyncEngine.Result.Completed>(result)
        assertContentEquals(there, local.files[GIG])
        assertEquals(setOf(GIG), local.files.keys)
        assertEquals(setOf(GIG), provider.files.keys)
        assertEquals(emptyList(), completed.summary.conflicts)
        val entry = completed.index.entries.getValue(GIG.path)
        assertEquals(localContentHash(there), entry.localHash)
        assertEquals("r1", entry.remoteRevision)
    }

    @Test
    fun `a setlist dated differently on a device connecting for the first time keeps no copy`() = runTest {
        val there = "Gig;date:2026-10-05;songs:a".encodeToByteArray()
        val local = FakeLibraryFileLocalSource(files = mapOf(GIG to "Gig;date:2026-10-07;songs:a".encodeToByteArray()))
        val provider = FakeSyncProvider(files = mapOf(GIG to there))

        val result = synchronize(local, provider, SyncIndexDocument(), setlistComparison = DayBlindSetlistComparison)

        assertContentEquals(there, local.files[GIG])
        assertEquals(setOf(GIG), local.files.keys)
        assertEquals(setOf(GIG), provider.files.keys)
        assertEquals(emptyList(), assertIs<SyncEngine.Result.Completed>(result).summary.conflicts)
    }

    @Test
    fun `a setlist changed in more than its date still keeps both`() = runTest {
        val here = "Gig;date:2026-10-07;songs:a,b".encodeToByteArray()
        val there = "Gig;date:2026-10-05;songs:a,c".encodeToByteArray()
        val local = FakeLibraryFileLocalSource(files = mapOf(GIG to here))
        val provider = FakeSyncProvider(files = mapOf(GIG to there))

        val result = synchronize(
            local = local,
            provider = provider,
            document = indexOf(GIG to "Gig;date:2026-01-01;songs:a".encodeToByteArray()),
            setlistComparison = DayBlindSetlistComparison,
        )

        assertContentEquals(here, local.files[GIG])
        assertContentEquals(there, local.files[GIG_COPY])
        assertEquals(listOf(GIG_COPY.name), assertIs<SyncEngine.Result.Completed>(result).summary.conflicts)
    }

    @Test
    fun `a song that differs only in what reads as a day still keeps both`() = runTest {
        val here = "Song;date:2026-10-07;".encodeToByteArray()
        val there = "Song;date:2026-10-05;".encodeToByteArray()
        val local = FakeLibraryFileLocalSource(files = mapOf(song(1) to here))
        val provider = FakeSyncProvider(files = mapOf(song(1) to there))

        val result = synchronize(local, provider, indexOf(song(1) to "Song;".encodeToByteArray()), setlistComparison = DayBlindSetlistComparison)

        assertContentEquals(here, local.files[song(1)])
        assertContentEquals(there, local.files[SyncKey(kind = LibraryFileKind.SONG, name = "song_1 (2).cho")])
        assertEquals(listOf("song_1 (2).cho"), assertIs<SyncEngine.Result.Completed>(result).summary.conflicts)
    }

    @Test
    fun `a setlist saved while the folder's day comes down is not written over`() = runTest {
        val here = "Gig;date:2026-10-07;songs:a".encodeToByteArray()
        val saved = "Gig;date:2026-10-07;songs:a,b".encodeToByteArray()
        val there = "Gig;date:2026-10-05;songs:a".encodeToByteArray()
        val local = FakeLibraryFileLocalSource(files = mapOf(GIG to here))
        val provider = FakeSyncProvider(files = mapOf(GIG to there))
        var hasSaved = false
        provider.onDownload = { key ->
            if (key == GIG && !hasSaved) {
                hasSaved = true
                local.files[GIG] = saved
            }
        }

        val result = synchronize(
            local = local,
            provider = provider,
            document = indexOf(GIG to "Gig;date:2026-01-01;songs:a".encodeToByteArray()),
            setlistComparison = DayBlindSetlistComparison,
        )

        // The first pass leaves it to the second, which finds the save: more than a day apart from the folder's copy.
        assertContentEquals(saved, local.files[GIG])
        assertContentEquals(saved, provider.files.getValue(GIG).first)
        assertContentEquals(there, local.files[GIG_COPY])
        assertEquals(listOf(GIG_COPY.name), assertIs<SyncEngine.Result.Completed>(result).summary.conflicts)
    }

    @Test
    fun `a setlist only dated here takes another device's edit and keeps no copy`() = runTest {
        val undated = "Gig;songs:a".encodeToByteArray()
        val edited = "Gig;songs:a,b".encodeToByteArray()
        val local = FakeLibraryFileLocalSource(files = mapOf(GIG to "Gig;date:2026-10-07;songs:a".encodeToByteArray()))
        val provider = FakeSyncProvider(files = mapOf(GIG to edited))

        val result = synchronize(local, provider, indexOf(GIG to undated), setlistComparison = DayBlindSetlistComparison)

        val completed = assertIs<SyncEngine.Result.Completed>(result)
        assertContentEquals(edited, local.files[GIG])
        assertEquals(setOf(GIG), local.files.keys)
        assertEquals(emptyList(), completed.summary.conflicts)
        val entry = completed.index.entries.getValue(GIG.path)
        assertEquals(localContentHash(edited), entry.localHash)
        assertEquals("r1", entry.remoteRevision)
    }

    @Test
    fun `a setlist edited and dated here still keeps another device's edit next to it`() = runTest {
        val here = "Gig;date:2026-10-07;songs:a,c".encodeToByteArray()
        val edited = "Gig;songs:a,b".encodeToByteArray()
        val local = FakeLibraryFileLocalSource(files = mapOf(GIG to here))
        val provider = FakeSyncProvider(files = mapOf(GIG to edited))

        val result = synchronize(local, provider, indexOf(GIG to "Gig;songs:a".encodeToByteArray()), setlistComparison = DayBlindSetlistComparison)

        assertContentEquals(here, local.files[GIG])
        assertContentEquals(edited, local.files[GIG_COPY])
        assertEquals(listOf(GIG_COPY.name), assertIs<SyncEngine.Result.Completed>(result).summary.conflicts)
    }
}
