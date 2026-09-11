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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The whole point of keeping the planner a pure function: every way a file can differ between two devices is one
 * case here, including the ones that would otherwise only show up as a song someone lost.
 */
class SyncPlannerTest {

    @Test
    fun `a file that is new locally goes up`() = assertEquals(
        expected = listOf(SyncOperation.Upload(SONG, expectedRevision = null)),
        actual = SyncPlanner.plan(local = listOf(local(SONG, "a")), remote = emptyList(), index = emptyMap()),
    )

    @Test
    fun `a file that is new remotely comes down`() = assertEquals(
        expected = listOf(SyncOperation.Download(SONG, revision = "r1")),
        actual = SyncPlanner.plan(local = emptyList(), remote = listOf(remote(SONG, "r1")), index = emptyMap()),
    )

    @Test
    fun `a file that has not moved on either side is left alone`() = assertEquals(
        expected = emptyList(),
        actual = SyncPlanner.plan(
            local = listOf(local(SONG, "a")),
            remote = listOf(remote(SONG, "r1")),
            index = mapOf(SONG to entry(hash = "a", revision = "r1")),
        ),
    )

    @Test
    fun `a file edited locally goes up against the revision the last run saw`() = assertEquals(
        expected = listOf(SyncOperation.Upload(SONG, expectedRevision = "r1")),
        actual = SyncPlanner.plan(
            local = listOf(local(SONG, "b")),
            remote = listOf(remote(SONG, "r1")),
            index = mapOf(SONG to entry(hash = "a", revision = "r1")),
        ),
    )

    @Test
    fun `a file edited remotely comes down`() = assertEquals(
        expected = listOf(SyncOperation.Download(SONG, revision = "r2")),
        actual = SyncPlanner.plan(
            local = listOf(local(SONG, "a")),
            remote = listOf(remote(SONG, "r2")),
            index = mapOf(SONG to entry(hash = "a", revision = "r1")),
        ),
    )

    @Test
    fun `a file edited on both sides is resolved rather than overwritten`() = assertEquals(
        expected = listOf(SyncOperation.Resolve(SONG, revision = "r2")),
        actual = SyncPlanner.plan(
            local = listOf(local(SONG, "b")),
            remote = listOf(remote(SONG, "r2")),
            index = mapOf(SONG to entry(hash = "a", revision = "r1")),
        ),
    )

    /** Both sides hold a file the other has never heard of, which is what connecting a second device looks like. */
    @Test
    fun `a file present on both sides with no index entry is resolved`() = assertEquals(
        expected = listOf(SyncOperation.Resolve(SONG, revision = "r1")),
        actual = SyncPlanner.plan(
            local = listOf(local(SONG, "a")),
            remote = listOf(remote(SONG, "r1")),
            index = emptyMap(),
        ),
    )

    @Test
    fun `a file deleted remotely and untouched locally is deleted locally`() = assertEquals(
        expected = listOf(SyncOperation.DeleteLocal(SONG)),
        actual = SyncPlanner.plan(
            local = listOf(local(SONG, "a")),
            remote = emptyList(),
            index = mapOf(SONG to entry(hash = "a", revision = "r1")),
        ),
    )

    @Test
    fun `a file deleted locally and untouched remotely is deleted remotely`() = assertEquals(
        expected = listOf(SyncOperation.DeleteRemote(SONG, revision = "r1")),
        actual = SyncPlanner.plan(
            local = emptyList(),
            remote = listOf(remote(SONG, "r1")),
            index = mapOf(SONG to entry(hash = "a", revision = "r1")),
        ),
    )

    /**
     * The rule that keeps work from disappearing: a song edited on the phone while it was deleted on the laptop
     * comes back, rather than the deletion winning and the edit being lost with nothing to show for it.
     */
    @Test
    fun `a file edited locally and deleted remotely is put back`() = assertEquals(
        expected = listOf(SyncOperation.Upload(SONG, expectedRevision = null)),
        actual = SyncPlanner.plan(
            local = listOf(local(SONG, "b")),
            remote = emptyList(),
            index = mapOf(SONG to entry(hash = "a", revision = "r1")),
        ),
    )

    @Test
    fun `a file edited remotely and deleted locally is put back`() = assertEquals(
        expected = listOf(SyncOperation.Download(SONG, revision = "r2")),
        actual = SyncPlanner.plan(
            local = emptyList(),
            remote = listOf(remote(SONG, "r2")),
            index = mapOf(SONG to entry(hash = "a", revision = "r1")),
        ),
    )

    @Test
    fun `a file gone from both sides is forgotten`() = assertEquals(
        expected = listOf(SyncOperation.Forget(SONG)),
        actual = SyncPlanner.plan(
            local = emptyList(),
            remote = emptyList(),
            index = mapOf(SONG to entry(hash = "a", revision = "r1")),
        ),
    )

    /** Songs and setlists are separate folders, so the same name in each is two different files. */
    @Test
    fun `the same name in the two folders is two files`() {
        val songKey = SyncKey(LibraryFileKind.SONG, "Both.cho")
        val setlistKey = SyncKey(LibraryFileKind.SETLIST, "Both.cho")
        val operations = SyncPlanner.plan(
            local = listOf(LocalFileState(songKey, "a")),
            remote = listOf(RemoteFileState(setlistKey, "r1", contentHash = null)),
            index = emptyMap(),
        )
        assertEquals(expected = 2, actual = operations.size)
        assertTrue(operations.contains(SyncOperation.Upload(songKey, expectedRevision = null)))
        assertTrue(operations.contains(SyncOperation.Download(setlistKey, revision = "r1")))
    }

    @Test
    fun `an empty library on both sides has nothing to do`() = assertEquals(
        expected = emptyList(),
        actual = SyncPlanner.plan(local = emptyList(), remote = emptyList(), index = emptyMap()),
    )

    /** The index is keyed by a path, and a key has to survive the round trip through it unchanged. */
    @Test
    fun `a key survives being written to the index and read back`() {
        val key = SyncKey(LibraryFileKind.SETLIST, "Summer tour.setlist.json")
        assertEquals(expected = key, actual = SyncKey.fromPath(key.path))
    }

    @Test
    fun `a key with a slash in the name is not read back as something else`() =
        assertEquals(expected = null, actual = SyncKey.fromPath("nonsense"))

    private companion object {
        val SONG = SyncKey(LibraryFileKind.SONG, "Artist - Title.cho")

        fun local(key: SyncKey, hash: String) = LocalFileState(key = key, hash = hash)

        fun remote(key: SyncKey, revision: String, contentHash: String? = null) =
            RemoteFileState(key = key, revision = revision, contentHash = contentHash)

        fun entry(hash: String, revision: String) = SyncIndexEntry(localHash = hash, remoteRevision = revision)
    }
}
