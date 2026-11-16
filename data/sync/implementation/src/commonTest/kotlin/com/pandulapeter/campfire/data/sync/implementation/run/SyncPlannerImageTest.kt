/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.run

import com.pandulapeter.campfire.data.model.library.LibraryFileKind
import com.pandulapeter.campfire.data.sync.implementation.LocalFileState
import com.pandulapeter.campfire.data.sync.implementation.RemoteFileState
import com.pandulapeter.campfire.data.sync.implementation.SyncIndexEntry
import com.pandulapeter.campfire.data.sync.implementation.SyncKey
import com.pandulapeter.campfire.data.sync.implementation.SyncOperation
import com.pandulapeter.campfire.data.sync.implementation.song
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncPlannerImageTest {

    @Test
    fun `two pictures of one size deleted on opposite sides are both deleted`() = assertEquals(
        expected = setOf(SyncOperation.DeleteRemote(FIRST, "r1"), SyncOperation.DeleteLocal(SECOND)),
        actual = SyncPlanner.plan(
            local = listOf(LocalFileState(SECOND, localImageHash(10))),
            remote = listOf(RemoteFileState(FIRST, "r1", contentHash = null)),
            index = mapOf(FIRST to SyncIndexEntry(localImageHash(10), "r1"), SECOND to SyncIndexEntry(localImageHash(10), "r1")),
            imageReferences = emptyMap(),
        ).toSet(),
    )

    @Test
    fun `no picture is deleted on either side while the songs of this device are not all known`() = assertEquals(
        expected = emptyList(),
        actual = SyncPlanner.plan(
            local = listOf(LocalFileState(SECOND, localImageHash(10))),
            remote = listOf(RemoteFileState(FIRST, "r1", contentHash = null)),
            index = mapOf(FIRST to SyncIndexEntry(localImageHash(10), "r1"), SECOND to SyncIndexEntry(localImageHash(10), "r1")),
            imageReferences = null,
        ),
    )

    @Test
    fun `a picture named only by a song this pass deletes here waits for the next run`() = assertEquals(
        expected = listOf<SyncOperation>(SyncOperation.DeleteLocal(song(1))),
        actual = SyncPlanner.plan(
            local = listOf(LocalFileState(song(1), "a"), LocalFileState(FIRST, localImageHash(10))),
            remote = emptyList(),
            index = mapOf(song(1) to SyncIndexEntry("a", "r1"), FIRST to SyncIndexEntry(localImageHash(10), "r1")),
            imageReferences = mapOf(song(1) to setOf(FIRST.name)),
        ),
    )

    private companion object {
        val FIRST = SyncKey(LibraryFileKind.FILE, "0123456789abcdef.png")
        val SECOND = SyncKey(LibraryFileKind.FILE, "fedcba9876543210.jpg")
    }
}
