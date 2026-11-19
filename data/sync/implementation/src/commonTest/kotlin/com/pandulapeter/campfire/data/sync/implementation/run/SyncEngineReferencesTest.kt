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

import com.pandulapeter.campfire.data.sync.implementation.FakeLibraryFileLocalSource
import com.pandulapeter.campfire.data.sync.implementation.FakeSyncProvider
import com.pandulapeter.campfire.data.sync.implementation.run.OtherAppLibrary.blob
import com.pandulapeter.campfire.data.sync.implementation.run.OtherAppLibrary.note
import com.pandulapeter.campfire.data.sync.implementation.run.OtherAppLibrary.synchronize
import com.pandulapeter.campfire.data.sync.implementation.run.OtherAppLibrary.syncedIndexOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What another app's documents name, read by its own [SyncReferences], is kept and restored by a whole run. */
class SyncEngineReferencesTest {

    @Test
    fun `a file a note names goes back up when another device deleted it from the folder`() = runTest {
        val local = FakeLibraryFileLocalSource(mapOf(NOTE to NAMING, BLOB to BLOB_BYTES))
        val cloud = FakeSyncProvider(mapOf(NOTE to NAMING))

        synchronize(local, cloud, syncedIndexOf(NOTE to NAMING, BLOB to BLOB_BYTES))

        assertContentEquals(BLOB_BYTES, local.files[BLOB])
        assertContentEquals(BLOB_BYTES, cloud.files[BLOB]?.first)
    }

    @Test
    fun `a file no note names is deleted here when another device deleted it from the folder`() = runTest {
        val plain = "Groceries".encodeToByteArray()
        val local = FakeLibraryFileLocalSource(mapOf(NOTE to plain, BLOB to BLOB_BYTES))
        val cloud = FakeSyncProvider(mapOf(NOTE to plain))

        synchronize(local, cloud, syncedIndexOf(NOTE to plain, BLOB to BLOB_BYTES))

        assertNull(local.files[BLOB])
        assertNull(cloud.files[BLOB])
    }

    @Test
    fun `a file a note names that no side holds is asked back from its collection's history`() = runTest {
        val local = FakeLibraryFileLocalSource(mapOf(NOTE to NAMING))
        val cloud = FakeSyncProvider(mapOf(NOTE to NAMING))
        cloud.deletedFiles[BLOB] = BLOB_BYTES

        val first = synchronize(local, cloud, syncedIndexOf(NOTE to NAMING))
        synchronize(local, cloud, first.index)

        assertTrue(first.asksForFollowUp)
        assertEquals(listOf(BLOB), cloud.restoreRequests)
        assertContentEquals(BLOB_BYTES, local.files[BLOB])
    }

    private companion object {
        val BLOB_BYTES = "a photo".encodeToByteArray()
        val BLOB = blob(BLOB_BYTES)
        val NOTE = note("groceries")
        val NAMING = "Groceries;see:${BLOB.name};".encodeToByteArray()
    }
}
