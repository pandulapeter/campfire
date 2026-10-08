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
import com.pandulapeter.campfire.data.model.domain.SyncDeletionDirection
import com.pandulapeter.campfire.data.model.domain.SyncDeletionPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeletionGuardTest {

    @Test
    fun `four local deletions of eight go ahead`() {
        assertNull(check(localDeletions = 4, total = 8))
    }

    @Test
    fun `five local deletions of nine ask`() {
        assertEquals(question(5, 9, SyncDeletionDirection.LOCAL), check(localDeletions = 5, total = 9))
    }

    @Test
    fun `exactly half is not more than half`() {
        assertNull(check(localDeletions = 5, total = 10))
    }

    @Test
    fun `the whole index asks however small it is`() {
        assertEquals(question(3, 3, SyncDeletionDirection.LOCAL), check(localDeletions = 3, total = 3))
    }

    @Test
    fun `no deletions and no index go ahead`() {
        assertNull(check(total = 8))
        assertNull(check(localDeletions = 3, remoteDeletions = 3, total = 0))
    }

    @Test
    fun `the cloud folder is guarded the same way`() {
        assertNull(check(remoteDeletions = 4, total = 8))
        assertEquals(question(5, 9, SyncDeletionDirection.REMOTE), check(remoteDeletions = 5, total = 9))
        assertNull(check(remoteDeletions = 5, total = 10))
        assertEquals(question(3, 3, SyncDeletionDirection.REMOTE), check(remoteDeletions = 3, total = 3))
    }

    @Test
    fun `an empty library folder asks before a single remote deletion`() {
        assertEquals(
            question(1, 20, SyncDeletionDirection.REMOTE),
            check(remoteDeletions = 1, total = 20, isLocalListingEmpty = true),
        )
    }

    @Test
    fun `this device is asked about first`() {
        assertEquals(question(6, 10, SyncDeletionDirection.LOCAL), check(localDeletions = 6, remoteDeletions = 6, total = 10))
    }

    @Test
    fun `an answer waives the guard of its own direction only`() {
        assertEquals(
            question(6, 10, SyncDeletionDirection.REMOTE),
            check(localDeletions = 6, remoteDeletions = 6, total = 10, policy = SyncDeletionPolicy.DELETE_LOCALLY),
        )
        assertEquals(
            question(6, 10, SyncDeletionDirection.LOCAL),
            check(localDeletions = 6, remoteDeletions = 6, total = 10, policy = SyncDeletionPolicy.KEEP_AND_DOWNLOAD),
        )
        assertNull(check(remoteDeletions = 6, total = 10, policy = SyncDeletionPolicy.KEEP_AND_DOWNLOAD))
        assertNull(check(localDeletions = 6, total = 10, policy = SyncDeletionPolicy.DELETE_LOCALLY))
    }

    private fun check(
        localDeletions: Int = 0,
        remoteDeletions: Int = 0,
        total: Int,
        isLocalListingEmpty: Boolean = false,
        policy: SyncDeletionPolicy = SyncDeletionPolicy.ASK,
    ) = DeletionGuard.check(
        plan = List(localDeletions) { SyncOperation.DeleteLocal(key(it)) } +
            List(remoteDeletions) { SyncOperation.DeleteRemote(key(100 + it), revision = "r") } +
            SyncOperation.Upload(key(1_000), expectedRevision = null),
        planningIndexSize = total,
        isLocalListingEmpty = isLocalListingEmpty,
        policy = policy,
    )

    private fun question(count: Int, total: Int, direction: SyncDeletionDirection) =
        SyncEngine.Result.DeletionsNeedConfirmation(count = count, total = total, direction = direction)

    private fun key(number: Int) = SyncKey(kind = LibraryFileKind.SONG, name = "song_$number.cho")
}
